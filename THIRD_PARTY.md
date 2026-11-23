# Third-party notices

The **lean APK** (`tools/build.sh`) bundles no third-party code, fonts, images or map data: only the Android framework
and the Java standard library; text uses the device's system fonts.

The **MapLibre APK** (`tools/gradle-build.sh`) bundles, through Gradle (versions pinned in `build.gradle`):
- MapLibre Native Android 13.7.1, OpenGL ES build (BSD 2-Clause) and its MapLibre GeoJSON / Turf / gestures modules
  (BSD / MIT, see each artifact): notice must appear in the app's about screen before any release.
- OkHttp 4.12 and Okio (Apache 2.0), Timber (Apache 2.0), Kotlin standard library and kotlinx-coroutines (Apache 2.0),
  AndroidX (Apache 2.0): pulled in by MapLibre.
- Map data is NOT bundled; it is fetched at run time from the user's chosen source. Default source OpenFreeMap:
  attribution "OpenFreeMap · © OpenMapTiles · Data © OpenStreetMap contributors" is shown on the map
  (OpenStreetMap data: ODbL; OpenMapTiles schema: BSD 3-Clause code, CC-BY 4.0 design). Offline use follows
  OpenFreeMap's terms (docs/MAP.md).
In the app: Settings -> About and licences -> Full licence texts (NOTICE, MapLibre BSD-2 from docs/licenses/, Apache 2.0).

Interfaces, not code:
- The `/api/hud` and `/api/hud/scene` JSON contracts are served by StarPilot's Galaxy web server (StarPilot and
  openpilot are MIT-licensed, copyright comma.ai and the StarPilot authors). This app only reads them over HTTP;
  no StarPilot or openpilot source is included.

Build tools (not shipped in the APK): Android SDK build-tools (aapt2, d8, zipalign, apksigner), JDK.

To add when used (keep this list current before any release):
- Self-built tile packs (if added): publish the build recipe (ODbL derived database).
- Inter font (SIL OFL 1.1) if bundled: ship OFL.txt.
