#!/usr/bin/env bash
# Generic secret scan (no external tools): credentials, keys and tokens by well-known patterns, plus high-entropy
# assignments. Usage: tools/secret-scan.sh [DIR] (default: the project tree). Exit 1 on any finding.
set -uo pipefail
D="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
python3 -I - "$D" <<'PY'
import math, os, re, sys
root = sys.argv[1]
pats = {
    "private key block": r"-----BEGIN (RSA |EC |DSA |OPENSSH |PGP |ENCRYPTED )?PRIVATE KEY",
    "AWS access key": r"\b(AKIA|ASIA)[0-9A-Z]{16}\b",
    "GitHub token": r"\b(ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36}\b|\bgithub_pat_[A-Za-z0-9_]{50,}",
    "Google API key": r"\bAIza[0-9A-Za-z_\-]{35}\b",
    "Slack token": r"\bxox[abprs]-[0-9A-Za-z-]{10,}",
    "Mapbox token": r"\b[ps]k\.ey[A-Za-z0-9._-]{20,}",
    "JWT": r"\beyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}",
    "Discord token": r"\b[MN][A-Za-z\d]{23,25}\.[\w-]{6}\.[\w-]{27,}",
    "password in URL": r"[a-z]+://[^/\s:@]+:[^/\s:@]{3,}@",
    "keystore / key file": None,
}
bad_ext = re.compile(r"\.(jks|keystore|p12|pfx|pem|key|der|asc|gpg)$", re.I)
assign = re.compile(r"(?i)(secret|token|passw(or)?d|api[_-]?key|auth)[\"']?\s*[:=]\s*[\"']([^\"'\s]{12,})[\"']")
def entropy(s):
    return -sum(s.count(c) / len(s) * math.log2(s.count(c) / len(s)) for c in set(s))
hits = 0
for dp, dn, fn in os.walk(root):
    dn[:] = [d for d in dn if d not in (".git", ".gradle", "build")]
    for f in fn:
        p = os.path.join(dp, f); rel = os.path.relpath(p, root)
        if bad_ext.search(f): print("FAIL key file:", rel); hits += 1; continue
        if rel == "tools/secret-scan.sh": continue
        try: text = open(p, encoding="utf-8").read()
        except Exception: continue
        for n, line in enumerate(text.splitlines(), 1):
            for name, pat in pats.items():
                if pat and re.search(pat, line): print("FAIL %s: %s:%d" % (name, rel, n)); hits += 1
            m = assign.search(line)
            if m and entropy(m.group(3)) > 3.5 and not re.fullmatch(r"[a-z_.]+", m.group(3)):
                print("FAIL high-entropy secret assignment: %s:%d" % (rel, n)); hits += 1
print("secret-scan: %s (%s)" % ("PASS" if hits == 0 else "FAIL %d" % hits, root))
sys.exit(1 if hits else 0)
PY
