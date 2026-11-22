# Add-ons

The default app is a read-only display with no car-specific features. Anything that only makes sense for one car,
one piece of hardware or one owner is an **add-on**: a separate source directory under `addons/<name>/`, compiled
only into an add-on build, never into the default APK.

## Extension points at a glance
| Extension point | Where | Use it for |
|---|---|---|
| `Addon` | core | extra `/api/hud` fields, settings rows, small drive-screen cards, opt-in writes |
| `SourceProvider` | core | another way to get the data (address scheme `name:...`), e.g. a vendor feed or a different transport |
| `HeadUnitAdapter` | app (Android) | a device family's hooks: auto-start, steering-wheel / hard keys, vehicle day / night, camera yield, second display |
| `MapEngine` | app (Android) | a native map behind the drive view (MapLibre flavour); the built-in renderer is the fallback |
All are listed by class name in the asset `addons.txt` (map engines in `map-engines.txt`) of a build that includes
them. The default public APK ships none of the device-specific ones.

## Head-unit enhancement modules
Head units differ completely (Android version, vendor services, keys, screens). The core never knows about a vendor.
A head-unit module is an add-on directory `addons/<family>/` with a `HeadUnitAdapter` (and optionally `Addon`s and a
`SourceProvider`), its own README (which units / firmware it was tested on, who maintains it, what it changes) and
host tests. Rules: no root needed by default (a module that needs root says so and is opt-in), never writes to the
comma, never hides openpilot alerts, undo = uninstall the app. Owner- or car-specific modules stay outside the public
repo (`.export-exclude`).

## What the core offers (extension points, `core/src/org/tuiapp/core/`)
- `Addon` interface:
  - `onHud(raw, now)`: every `/api/hud` frame as parsed JSON, so an add-on can read fields the core does not know.
  - `settingRows(...)` / `select(...)`: rows in their own section of the settings screen; a tap returns either
    nothing (local change, refusal with a reason in the row note) or a `WriteRequest`.
  - `onWriteResult(...)`: the comma's answer to a write.
  - `cards(...)`: small pills on the drive screen (one slot below the state chip; never over the alert banner).
- `Addons`: registry, loaded from an optional asset `addons.txt` (class names). The default APK has no such asset,
  so the registry is empty and nothing extra is drawn, parsed or sent.
- `CommaHttpSource.post(...)`: refuses unless writes were switched on; the app switches them on only for the
  duration of one request that an add-on returned after passing its own guards.

## Rules for an add-on
- Read-only by default. Writes need the add-on's own "allow changes" setting (default off), the comma's capability
  flag, the car stopped and openpilot disengaged, and a second confirming tap.
- Never weaken or hide openpilot alerts; never draw over the alert banner.
- Owner-only add-ons are listed in `.export-exclude`: they are not part of a public export, and their identifiers
  (endpoint and field names) must not appear anywhere else in the tree. `tools/clean-export-check.sh` enforces both;
  `tools/build.sh` leak-checks every default APK (`clean-export-check.sh --apk`).
- Each add-on has `addon.txt` (class names), `README.md` (what, who, risks), `src/`, `test/`, `test.sh`
  (run by `tools/hosttest.sh`).

## Building
    tools/build.sh                          # default APK, no add-ons, leak-checked
    ADDONS="<name>" tools/build.sh          # add-on APK: /tmp/tui-build/tui-<name>-debug.apk (same package id)

## Current add-ons
None in the public repository. Car- or owner-specific modules are kept outside it.
