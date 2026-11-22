# Map (v0.3)

## Two builds
- **Lean** (`tools/build.sh`, no dependencies, minSdk 21, ~200 KB): built-in core map renderer only.
- **MapLibre** (`tools/gradle-build.sh`, Gradle 8.14.8 + Android Gradle Plugin 8.13.2 + MapLibre Native Android
  13.7.1 OpenGL ES build, minSdk 23, ~23 MB with arm64-v8a + armeabi-v7a): live and offline vector maps.
  Gradle, its cache and every build output live outside the tree (`~/bulk/tui-gradle`, `/tmp/tui-gradle`).
  The OpenGL ES artifact (`android-sdk-opengl`) is used on purpose: the plain `android-sdk` artifact is the Vulkan
  build, which cheap head units may not support.

## Engine interface
`app/.../MapEngine` (Android side). The MapLibre flavour lists `org.tuiapp.map.maplibre.MapLibreEngine` in
`assets/map-engines.txt`; the lean build has no such file and no engine. The engine's view sits behind the drive view;
the core renderer leaves the map pane transparent (`Gfx.clear`) and still draws the turn card, mode label and
attribution on top.

## Which map is shown (`core/MapChooser`, host-tested)
online tiles -> offline / cached tiles -> built-in renderer:
- demo drive: built-in synthetic town;
- no comma GPS fix: "Waiting for GPS from the comma";
- engine with "Online map tiles" on and a network: live map;
- otherwise, fix inside a downloaded area: offline map (MapLibre is set to not connect at all);
- otherwise, style loaded from cache: cached map (tiles may be missing);
- style failed / nothing loaded, or the lean build: built-in renderer with the comma's route around the fix.
Position, heading, route and next turn come only from the comma (`/api/hud` `gps`, `nav`; route geometry from
`/api/hud/route?gen=g`, fetched once per route generation). The app asks for no location permission (MapLibre's
merged location / Wi-Fi permissions are removed in the manifest).

## Tile sources (Settings -> Map source)
- **OpenFreeMap** (default; `liberty` style) and **OpenFreeMap dark**: free, no API key, OpenMapTiles schema.
  Attribution shown on the map: "OpenFreeMap · © OpenMapTiles · Data © OpenStreetMap contributors".
  Their terms (read 2026-11-22) allow offline downloads in apps if each download is at most **3,000 tiles or 50 MB**,
  uses at most **4 parallel requests**, and sends a **User-Agent that identifies the app**. Enforced: the offline screen
  refuses larger areas (estimate before, and the engine pauses a download that runs past the limit), one OkHttp client
  with max 4 requests per host serves every MapLibre request, User-Agent `TUI-driving-display/<version> (MapLibre Native)`
  (no user, car or device data; no cookies or tokens are added).
- **Custom URL**: any MapLibre style JSON the user enters (their responsibility); offline areas are capped at the
  "offline cap" setting (default 500 MB per area), still 4 parallel requests.
- Not offered: the openstreetmap.org standard tile server (its usage policy forbids offline bulk downloads by apps).

## Offline areas (Settings -> Offline maps)
Map viewport (pan / pinch on the device) + "Draw area" (drag a rectangle) or presets (visible area, 10 km / 50 km
around the car; the radius presets need a GPS fix). Detail: Overview z12, Roads z13, Streets z14 (minimum zoom 5).
Estimate: tile count per zoom (Web Mercator) and an average tile size per zoom (a rough number for the OpenMapTiles
schema, plus 1.5 MB for style, fonts and icons). Download / pause / resume / delete use MapLibre's `OfflineManager`
(`OfflineTilePyramidRegionDefinition`); progress, size and storage used come from `OfflineRegionStatus`.
Layout works at 800x480 (side column) and on tablets (portrait stacks the controls under the map).

## Verified / not verified
- Verified on the host: everything in `core/` (486 host tests incl. geo math, limits, map choice, offline screen
  hit-testing, route fetch from a fake comma), both APKs build, sign and pass the add-on leak check; the MapLibre engine
  compiles against MapLibre 13.7.1.
- Verified on the real network (2026-11-22, from the build machine): the OpenFreeMap style, its TileJSON and one z0
  vector tile download with the app's User-Agent (HTTP 200).
- **Not verified (needs a device or emulator):** that the MapLibre view renders behind the transparent pane,
  gestures pass through in "Move map", the offline download runs / pauses / resumes / deletes, storage numbers,
  offline mode with no network, memory and frame rate on a head unit. The size estimate is not calibrated against
  real downloads yet.

## Next
- Device test of the MapLibre flavour (emulator gate or a tablet).
- Calibrate the size estimate from real downloads; congestion colours on the route; scrolling area list.
- Optional self-hosted tile packs (PMTiles / MBTiles) for areas beyond the free source's limits.
