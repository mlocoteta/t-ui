# Changelog

All notable changes per release. Versions follow `Brand.VERSION_NAME`; the release tag is `v<version>`.
The release workflow uses the section of the tagged version as the GitHub release notes.

## [Unreleased]
### Added
### Changed
### Fixed
### Comma side (HUD API patch / schemaVersion needed)

## [0.1.1-alpha] - 2026-11-22
Single-user completeness: a stranger with StarPilot + the comma patch + this APK gets position, guidance and route.
### Added
- Map position from the comma patch's new `gps` block (t-ui-comma-patch v0.1.1).
- Turn-by-turn guidance, remaining distance / time and the route line from StarPilot's own navigation
  (`GET /api/navigation/status`, upstream, polled ~1 Hz with routeKey).
- Optional "Use this device's GPS" setting (MapLibre build, off by default): used only when the comma gives no
  position; asks for the location permission only when turned on.
### Changed
- MapLibre build now declares the fine-location permission (requested at run time only for the setting above).
### Comma side
- Update to t-ui-comma-patch v0.1.1 for the `gps` block; v0.1.0 still works (position then from navigation status
  or the device).

## [0.1.0-alpha] - 2026-11-22
First public pre-release. Not yet tested on a real head unit or tablet with a live comma; treat it as an alpha.
### Added
- Drive view: road model with lane lines, path, lead and radar cars (one generic car shape, data-driven brake lights),
  speed, set speed, speed limit, gear, engagement chip, lane-change state, alerts with a takeover banner.
- Layouts for any screen: full scene with floating cards or split scene / map; portrait with a bottom bar.
- Settings: comma address, theme, units, layout, keep screen on, right-hand drive, my car colour, map source, offline maps.
- Map: MapLibre build with OpenFreeMap live tiles and offline areas (within the source's limits); lean build with a
  built-in renderer; position and route from the comma.
- Data: read-only HTTP to the comma's HUD API (`/api/hud`, `/api/hud/scene`, `/api/hud/route`), synthetic demo drive.
- Extension points for add-ons and head-unit modules (docs/ADDONS.md).
### Comma side
- Needs the HUD API patch for StarPilot (https://github.com/mlocoteta/t-ui-comma-patch), schemaVersion 3.
### Install
- Lean APK: Android 5+, built-in map. MapLibre APK: Android 6+, live / offline maps. Same package and key.

<!-- Template for a release:
## [0.2.0] - YYYY-MM-DD
### Added
- ...
### Changed
- ...
### Fixed
- ...
### Comma side
- Needs the HUD API with schemaVersion >= 3.
### Install
- Lean APK: Android 5+, built-in map. MapLibre APK: Android 6+, live / offline maps. Same signing key as earlier releases.
-->
