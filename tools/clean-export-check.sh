#!/usr/bin/env bash
# Fails (exit 1) if the tree contains secrets, private markers or real-drive data. See docs/PRIVACY-CLEAN-EXPORT.md.
# Private markers (names, handles, private hosts, home SSIDs) live OUTSIDE the tree, one extended regex per line:
#   $TUI_PRIVATE_MARKERS (default: <tree>/../../state/tui-private-markers.txt). Missing file = FAIL unless
#   TUI_NO_PRIVATE_MARKERS=1 (for clones that have no private list, e.g. public CI).
#   tools/clean-export-check.sh --apk FILE.apk   checks a DEFAULT-build APK only: no add-on code, no add-on endpoints.
# Paths listed in .export-exclude (owner-only add-ons) are not part of a public export: they are skipped by the scans
# below, and add-on-only identifiers must not appear anywhere else in the tree (ADDON_ONLY).
set -uo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
# identifiers that may exist only inside excluded add-on dirs (built from pieces so this file does not match itself)
ADDON_ONLY="steering""Board|openpilot""Enabled|[Ss][Ss][Vv]3|org/tuiapp/addon|org\\.tuiapp\\.addon"
if [ "${1:-}" = "--apk" ]; then
  apk="$2"; t=$(mktemp -d /tmp/tui-apkchk.XXXX)
  python3 -I -c 'import sys,zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])' "$apk" "$t" || { echo "FAIL cannot read $apk"; exit 1; }
  leak=$(grep -rlaE "$ADDON_ONLY" "$t" | sed "s|$t/||"; [ -e "$t/assets/addons.txt" ] && echo "assets/addons.txt (add-on list)"; [ -e "$t/assets/defaults.properties" ] && echo "assets/defaults.properties (owner preview defaults)")
  rm -rf "$t"
  if [ -n "$leak" ]; then echo "FAIL default APK contains add-on code or add-on endpoint strings:"; echo "$leak" | sed 's/^/     /'; exit 1; fi
  echo "apk leak check: PASS ($(basename "$apk"))"; exit 0
fi
MK="${TUI_PRIVATE_MARKERS:-$here/../../state/tui-private-markers.txt}"
bad=0
hit() { echo "FAIL $1"; printf '%s\n' "$2" | head -5 | sed 's/^/     /'; bad=1; }
self="tools/clean-export-check.sh"
cd "$here"
excl=$(grep -v '^#' .export-exclude 2>/dev/null | sed '/^$/d')
files=$(find . -type f ! -path './.git/*' | sed 's|^\./||' | sort)
for e in $excl; do files=$(printf '%s\n' $files | grep -v "^$e"); done
allfiles=$(find . -type f ! -path './.git/*' | sed 's|^\./||' | sort)
grepf() { # $1 = label, $2 = ERE, [$3 = extra grep flags]
  local out; out=$(printf '%s\n' $files | grep -v "^$self\$" | xargs -r grep -nIE ${3:-} -- "$2" 2>/dev/null)
  [ -n "$out" ] && hit "$1" "$out"
}

# 1. private markers from the external list. An optional allow list (outside the tree too, $TUI_PRIVATE_MARKERS_ALLOW,
#    default next to the marker list) names exact public strings that may contain a marker (e.g. the published repo URL).
AL="${TUI_PRIVATE_MARKERS_ALLOW:-${MK%.txt}-allow.txt}"
if [ -s "$MK" ]; then
  while IFS= read -r re; do
    [ -z "$re" ] || [ "${re#\#}" != "$re" ] && continue
    out=$(printf '%s\n' $files | grep -v "^$self\$" | xargs -r grep -nIE -- "$re" 2>/dev/null | python3 -I -c '
import re, sys
al = [l.strip() for l in open(sys.argv[2]).read().splitlines() if l.strip() and not l.startswith("#")] if sys.argv[2] and __import__("os").path.exists(sys.argv[2]) else []
for line in sys.stdin:
    body = line.split(":", 2)[-1]
    for a in al: body = re.sub(a, "", body)
    if re.search(sys.argv[1], body): sys.stdout.write(line)
' "$re" "$AL")
    [ -n "$out" ] && hit "private marker /$re/" "$out"
  done < "$MK"
elif [ "${TUI_NO_PRIVATE_MARKERS:-0}" != 1 ]; then
  echo "FAIL private marker list missing: $MK (set TUI_NO_PRIVATE_MARKERS=1 only on a clone without one)"; bad=1
fi

# 2. generic secret / private-data patterns
grepf "key-like hex string (32+ hex chars)" '\b[0-9a-fA-F]{32,}\b'
grepf "PEM / private key block" 'BEGIN (RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY|BEGIN CERTIFICATE'
grepf "password / token assignment" '(passw(or)?d|passphrase|psk|secret|token|api[_-]?key)["'"'"']?[[:space:]]*[:=][[:space:]]*["'"'"'][^"'"'"' ]{4,}' -i
grepf "Tailscale (100.64/10) address" '\b100\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\.[0-9]{1,3}\.[0-9]{1,3}\b'
grepf "private LAN address" '\b(192\.168|10\.[0-9]{1,3})\.[0-9]{1,3}\.[0-9]{1,3}\b'
# coordinates: synthetic test data sits within 1 degree of 0,0 (open ocean); anything else is treated as real
geo=$(printf '%s\n' $files | grep -v "^$self\$" | python3 -I -c '
import re, sys
pair = re.compile(r"([-+]?[0-9]{1,2}\.[0-9]{3,})[ \t]*,[ \t]*([-+]?[0-9]{1,3}\.[0-9]{3,})")
key = re.compile(r"\x22(lat|lon|latitude|longitude)\x22[ \t]*:[ \t]*([-+]?[0-9.]+)")
for f in sys.stdin.read().split():
    try: lines = open(f, encoding="utf-8").read().splitlines()
    except Exception: continue
    for n, l in enumerate(lines, 1):
        hits = [m for m in pair.finditer(l) if abs(float(m.group(1))) >= 1 or abs(float(m.group(2))) >= 1]
        hits += [m for m in key.finditer(l) if re.fullmatch(r"[-+]?[0-9]*\.?[0-9]+", m.group(2)) and abs(float(m.group(2))) >= 1]
        if hits: print("%s:%d:%s" % (f, n, l.strip()[:160]))
')
[ -n "$geo" ] && hit "coordinates outside the synthetic 1-degree box around 0,0" "$geo"
grepf "e-mail address" '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}'
grepf "Wi-Fi config (ssid/psk)" '\b(ssid|wpa_passphrase|psk)[[:space:]]*='  -i
# trademarks in the product identity (name / id) only
idf=$(grep -nEi 'tesla|honda|acura|comma' core/src/org/tuiapp/core/Brand.java app/AndroidManifest.xml | grep -Ei 'APP_ID|APP_NAME|package=|label=')
[ -n "$idf" ] && hit "trademark in app name / package id" "$idf"

# 2b. add-on-only identifiers outside the excluded add-on dirs (the default build must never contain them)
grepf "add-on-only identifier outside an add-on dir" "$ADDON_ONLY"

# 3. files that must never be here
f=$(printf '%s\n' $allfiles | grep -Ei '\.(jks|keystore|p12|pfx|pem|key|apk|aab|dex|so|rlog|zst|bz2)$|(^|/)(rlog|qlog|replay-skin|route-skin|blackbox)|\.mbtiles$')
[ -n "$f" ] && hit "forbidden file type (keys, builds, logs, real drives, tiles)" "$f"
s=$(find . -type l ! -path './.git/*'); [ -n "$s" ] && hit "symlink (tree must be self-contained)" "$s"
big=$(find . -type f ! -path './.git/*' -size +512k); [ -n "$big" ] && hit "file over 512 KB" "$big"
n=$(printf '%s\n' $allfiles | wc -l); [ "$n" -gt 300 ] && hit "more than 300 files ($n)" ""

if [ "$bad" = 0 ]; then echo "clean-export-check: PASS ($n files)"; else echo "clean-export-check: FAIL"; fi
exit $bad
