#!/usr/bin/env bash
# Host tests + host screenshots (no device, no network beyond loopback). Needs a JDK (8+) and python3.
# Output only under ${OUT:-/tmp/tui-host} (never inside the project tree).
# Usage: tools/hosttest.sh [--png]     (--png also renders the layout screenshots to $OUT/screens)
# Add-on tests: every addons/<name>/test.sh is run after the core tests (add-ons are not part of the default build).
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
O="${OUT:-/tmp/tui-host}"
rm -rf "$O/cls"; mkdir -p "$O/cls"
python3 -I "$here/tools/gen_synthetic_replay.py" "$O/replay.jsonl" >/dev/null
python3 -I "$here/tools/gen_synthetic_replay.py" --map "$O/demo-map.json" >/dev/null
javac -nowarn -Xlint:-options --release 8 -encoding UTF-8 -d "$O/cls" \
  $(find "$here/core/src" "$here/tools/host/src" "$here/tests/host/src" -name '*.java')
java -Djava.awt.headless=true -cp "$O/cls" org.tuiapp.test.HostTests "$here/tests/fixtures" "$O/replay.jsonl" "$O/demo-map.json"
for t in "$here"/addons/*/test.sh; do [ -x "$t" ] && OUT="$O" "$t"; done
if [ "${1:-}" = "--png" ]; then
  java -Djava.awt.headless=true -cp "$O/cls" org.tuiapp.host.RenderPngs "$O/replay.jsonl" "$O/screens" "$O/demo-map.json"
fi
