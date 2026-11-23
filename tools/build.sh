#!/usr/bin/env bash
# Debug APK for TUI, no Gradle: aapt2 + javac (Java 8 source) + d8 + zipalign + apksigner (Android build-tools 34).
# minSdk 21, targetSdk 34. Name / id / version come from core/src/org/tuiapp/core/Brand.java.
# Output: ${BUILD_DIR:-/tmp/tui-build}/tui-debug.apk  (never inside the project tree)
# Add-on build: ADDONS="<name> ..." compiles addons/<name>/src too, writes assets/addons.txt from addons/<name>/addon.txt
#   and outputs tui-<names>-debug.apk. The DEFAULT build includes no add-on code and is leak-checked at the end.
# Signing: a throwaway DEBUG key outside the tree, generated on first use:
#   ${TUI_DEBUG_KEYSTORE:-$HOME/bulk/tui-debug-key/debug.jks}  (password "android", like Android's own debug key)
# Never sign with a private release key from another project. A real release key is a Phase 3 decision.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/android-sdk}"; BT="$SDK/build-tools/34.0.0"; JAR="$SDK/platforms/android-34/android.jar"
B="${BUILD_DIR:-/tmp/tui-build}"; OUT="$B/tui-debug.apk"
KS="${TUI_DEBUG_KEYSTORE:-$HOME/bulk/tui-debug-key/debug.jks}"
case "$B" in "$here"*) echo "BUILD_DIR must be outside the project tree" >&2; exit 1;; esac
case "$KS" in "$here"*) echo "keystore must be outside the project tree" >&2; exit 1;; esac

brand="$here/core/src/org/tuiapp/core/Brand.java"
get() { sed -n "s/.*$1 = \"\\(.*\\)\";.*/\\1/p" "$brand" | head -1; }
APP_ID="$(get APP_ID)"; APP_NAME="$(get APP_NAME)"; VN="$(get VERSION_NAME)"
VC="$(sed -n 's/.*VERSION_CODE = \([0-9]*\);.*/\1/p' "$brand")"
[ -n "$APP_ID" ] && [ -n "$APP_NAME" ] && [ -n "$VN" ] && [ -n "$VC" ] || { echo "cannot read Brand.java" >&2; exit 1; }

# PREVIEW=1: owner-only head-unit preview (separate package <APP_ID>.preview, label "<APP_NAME> Preview", Android 4.2+ /
#   API 17, own preview key ~/bulk/tui-preview-key). Add-ons and default settings come from an owner-only
#   addons/*/preview.conf (PREVIEW_ADDONS, PREVIEW_DEFAULTS); none exists in a public export.
MIN_API=21; JAR_COMPILE="$JAR"; KS_ALIAS=tuidebug; DEFAULTS=""
# Release signing (docs/RELEASING.md): TUI_RELEASE=1 with TUI_RELEASE_KEYSTORE (path), TUI_KEYSTORE_PASS, TUI_KEY_ALIAS,
# TUI_KEY_PASS from the environment (GitHub encrypted secrets in CI). No release key is ever generated or stored here.
if [ "${TUI_RELEASE:-0}" = 1 ]; then
  KS="${TUI_RELEASE_KEYSTORE:?TUI_RELEASE_KEYSTORE not set}"; KS_ALIAS="${TUI_KEY_ALIAS:?TUI_KEY_ALIAS not set}"
  : "${TUI_KEYSTORE_PASS:?TUI_KEYSTORE_PASS not set}"; export TUI_KEY_PASS="${TUI_KEY_PASS:-$TUI_KEYSTORE_PASS}"
  [ -s "$KS" ] || { echo "release keystore $KS missing" >&2; exit 1; }
fi
if [ "${PREVIEW:-0}" = 1 ]; then
  PREVIEW_APP_ID=""; PREVIEW_ADDONS=""; PREVIEW_DEFAULTS=""
  for c in "$here"/addons/*/preview.conf; do [ -f "$c" ] && . "$c"; done
  APP_ID="${PREVIEW_APP_ID:-$APP_ID.preview}"; APP_NAME="$APP_NAME Preview"; VN="$VN-preview"; MIN_API=17
  JAR_COMPILE="$SDK/platforms/android-17/android.jar"   # compile against API 17: any newer call fails the build
  KS="${TUI_PREVIEW_KEYSTORE:-$HOME/bulk/tui-preview-key/preview.jks}"; KS_ALIAS=tuipreview
  ADDONS="${ADDONS-$PREVIEW_ADDONS}"; DEFAULTS="$PREVIEW_DEFAULTS"
fi
ADDONS="${ADDONS:-}"
TAG=""; [ "${PREVIEW:-0}" = 1 ] && TAG="-preview"; for a in $ADDONS; do [ -d "$here/addons/$a/src" ] || { echo "no add-on $a" >&2; exit 1; }; TAG="$TAG-$a"; done
OUT="$B/tui$TAG-debug.apk"
W="$B/apk$TAG"; rm -rf "$W"; mkdir -p "$W/classes" "$W/dex" "$W/assets" "$W/res" "$W/host"
sed -e "s/@APP_ID@/$APP_ID/g" -e "s/@APP_NAME@/$APP_NAME/g" "$here/app/AndroidManifest.xml" > "$W/AndroidManifest.xml"
{ cat "$here/NOTICE"; echo; echo "=================================================================="; cat "$here/LICENSE"; } > "$W/assets/licenses.txt"
[ -n "$DEFAULTS" ] && printf '%s\n' "$DEFAULTS" > "$W/assets/defaults.properties"
python3 -I "$here/tools/gen_synthetic_replay.py" "$W/assets/demo.jsonl" >/dev/null
python3 -I "$here/tools/gen_synthetic_replay.py" --map "$W/assets/demo-map.json" >/dev/null
SRCS="$here/core/src $here/app/src"
for a in $ADDONS; do SRCS="$SRCS $here/addons/$a/src"; grep -v '^#' "$here/addons/$a/addon.txt" >> "$W/assets/addons.txt"; done
# app icon: drawn by the host tool (original artwork), never stored as a binary in the tree
javac -nowarn -Xlint:-options --release 8 -encoding UTF-8 -d "$W/host" $(find "$here/core/src" "$here/tools/host/src" -name '*.java')
java -Djava.awt.headless=true -cp "$W/host" org.tuiapp.host.IconGen "$W/res"
"$BT/aapt2" compile --dir "$W/res" -o "$W/res.zip"

"$BT/aapt2" link -I "$JAR" --manifest "$W/AndroidManifest.xml" -A "$W/assets" "$W/res.zip" \
  --min-sdk-version "$MIN_API" --target-sdk-version 34 --version-code "$VC" --version-name "$VN" -o "$W/unsigned.apk"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$JAR_COMPILE" -d "$W/classes" \
  $(find $SRCS -name '*.java')
"$BT/d8" --release --min-api "$MIN_API" --lib "$JAR" --output "$W/dex" $(find "$W/classes" -name '*.class')
cp "$W/unsigned.apk" "$W/withcode.apk"
python3 -I - "$W/withcode.apk" "$W/dex" <<'PY'
import glob, os, sys, zipfile
with zipfile.ZipFile(sys.argv[1], "a") as z:
    for d in sorted(glob.glob(os.path.join(sys.argv[2], "classes*.dex"))):
        z.write(d, os.path.basename(d), zipfile.ZIP_DEFLATED)
PY
"$BT/zipalign" -p -f 4 "$W/withcode.apk" "$W/aligned.apk"

if [ ! -s "$KS" ] && [ "${TUI_RELEASE:-0}" != 1 ]; then
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias "$KS_ALIAS" -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=TUI $KS_ALIAS,O=Debug,C=US" >/dev/null 2>&1
  echo "generated throwaway debug keystore $KS"
fi
if [ "${TUI_RELEASE:-0}" = 1 ]; then SP=(--ks-pass env:TUI_KEYSTORE_PASS --key-pass env:TUI_KEY_PASS); else SP=(--ks-pass pass:android --key-pass pass:android); fi
"$BT/apksigner" sign --ks "$KS" --ks-key-alias "$KS_ALIAS" "${SP[@]}" \
  --min-sdk-version "$MIN_API" --out "$OUT" "$W/aligned.apk"
rm -f "$OUT.idsig"
if [ -z "$ADDONS" ] && [ "${PREVIEW:-0}" != 1 ]; then "$here/tools/clean-export-check.sh" --apk "$OUT" || { echo "DEFAULT APK LEAK CHECK FAILED: $OUT" >&2; exit 1; }; fi
[ -n "${OUT_APK:-}" ] && { cp "$OUT" "$OUT_APK"; OUT="$OUT_APK"; }
echo "built $OUT ($(stat -c %s "$OUT") bytes, $APP_ID $VN/$VC, sha256 $(sha256sum "$OUT" | cut -c1-64))"
