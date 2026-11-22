#!/usr/bin/env bash
# MapLibre flavour APK via Gradle (Gradle + Android Gradle Plugin + MapLibre fetched into ~/bulk, never ~/.openclaw).
# Output: /tmp/tui-build/tui-maplibre[-<addons>]-debug.apk. Default (no ADDONS) is leak-checked like tools/build.sh.
#   TUI_GRADLE (default ~/bulk/tui-gradle/gradle-8.14.8/bin/gradle), GRADLE_USER_HOME (default ~/bulk/tui-gradle/home)
#   ADDONS="<name>" adds addons/<name>/src + assets/addons.txt.  ABIS="arm64-v8a,armeabi-v7a" (add x86_64 for emulators)
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
# Gradle: TUI_GRADLE, else a local install in ~/bulk, else the project's wrapper (./gradlew, e.g. in CI)
G="${TUI_GRADLE:-$HOME/bulk/tui-gradle/gradle-8.14.8/bin/gradle}"; [ -x "$G" ] || G="$(cd "$(dirname "$0")/.." && pwd)/gradlew"
[ -d "$HOME/bulk" ] && export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/bulk/tui-gradle/home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
W="${TUI_GRADLE_WORK:-/tmp/tui-gradle}"; export TUI_GEN="$W/gen" TUI_GRADLE_OUT="$W/build"
case "$W" in "$here"*) echo "work dir must be outside the tree" >&2; exit 1;; esac
ADDONS="${ADDONS:-}"; TAG=""; for a in $ADDONS; do [ -d "$here/addons/$a/src" ] || { echo "no add-on $a" >&2; exit 1; }; TAG="$TAG-$a"; done
brand="$here/core/src/org/tuiapp/core/Brand.java"
APP_NAME="$(sed -n 's/.*APP_NAME = "\(.*\)";.*/\1/p' "$brand" | head -1)"
rm -rf "$TUI_GEN"; mkdir -p "$TUI_GEN/assets" "$TUI_GEN/res" "$W/host"
# manifest without the package attribute (Gradle uses namespace), INTERNET + network state for tiles
sed -e '/package="@APP_ID@"/d' -e "s/@APP_NAME@/$APP_NAME/g" -e 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android"|<manifest xmlns:android="http://schemas.android.com/apk/res/android">|' \
  "$here/app/AndroidManifest.xml" | sed -e 's|^    *>$||' > "$TUI_GEN/AndroidManifest.xml"
# MapLibre's library manifest asks for location + Wi-Fi state. Keep FINE location for the opt-in "use this device's GPS"
# setting (asked at run time only when the user turns it on); strip COARSE location and Wi-Fi state
python3 -I - "$TUI_GEN/AndroidManifest.xml" <<'PY2'
import sys
p = sys.argv[1]; s = open(p).read()
s = s.replace('<manifest xmlns:android="http://schemas.android.com/apk/res/android">',
              '<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">', 1)
rm = "".join('    <uses-permission android:name="android.permission.%s" tools:node="remove" />\n' % n
             for n in ("ACCESS_COARSE_LOCATION", "ACCESS_WIFI_STATE"))
s = s.replace("    <application", rm + '    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />\n\n    <application', 1)
open(p, "w").write(s)
PY2
python3 -I "$here/tools/gen_synthetic_replay.py" "$TUI_GEN/assets/demo.jsonl" >/dev/null
python3 -I "$here/tools/gen_synthetic_replay.py" --map "$TUI_GEN/assets/demo-map.json" >/dev/null
printf 'org.tuiapp.map.maplibre.MapLibreEngine\n' > "$TUI_GEN/assets/map-engines.txt"
for a in $ADDONS; do grep -v '^#' "$here/addons/$a/addon.txt" >> "$TUI_GEN/assets/addons.txt"; done
javac -nowarn -Xlint:-options --release 8 -encoding UTF-8 -d "$W/host" $(find "$here/core/src" "$here/tools/host/src" -name '*.java')
java -Djava.awt.headless=true -cp "$W/host" org.tuiapp.host.IconGen "$TUI_GEN/res"
cd "$here"
case "$G" in ./*|*/gradlew) G="$(cd "$(dirname "$G")" && pwd)/$(basename "$G")";; esac
"$G" --no-daemon --console=plain --project-cache-dir "$W/project-cache" -Paddons="$ADDONS" -Pabis="${ABIS:-arm64-v8a,armeabi-v7a}" $( [ "${TUI_RELEASE:-0}" = 1 ] && echo assembleRelease || echo assembleDebug )
mkdir -p /tmp/tui-build
if [ "${TUI_RELEASE:-0}" = 1 ]; then OUT="${OUT_APK:-/tmp/tui-build/tui-maplibre$TAG-release.apk}"; cp "$TUI_GRADLE_OUT/outputs/apk/release/"*-release.apk "$OUT"
else OUT="${OUT_APK:-/tmp/tui-build/tui-maplibre$TAG-debug.apk}"; cp "$TUI_GRADLE_OUT/outputs/apk/debug/"*-debug.apk "$OUT"; fi
if [ -z "$ADDONS" ]; then "$here/tools/clean-export-check.sh" --apk "$OUT" || { echo "DEFAULT APK LEAK CHECK FAILED: $OUT" >&2; exit 1; }; fi
echo "built $OUT ($(stat -c %s "$OUT") bytes, sha256 $(sha256sum "$OUT" | cut -c1-64))"
