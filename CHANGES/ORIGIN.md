# Provenance

All code in this tree was written for this project in 2026-11, by the project owner's assistant, except the
items below that were copied or adapted from the owner's earlier private head-unit project ("private HU project").
That project is the owner's own work; nothing below comes from a car maker, a head-unit vendor or a third party.
No decompiled, vendor or third-party code was copied.

| File here | Source (private HU project) | What was taken |
|---|---|---|
| `core/.../HoldRule.java` `grey`, `sceneUsable` | upper-screen helper `HoldRule.java` | copied verbatim (logic + constants), comments rewritten |
| `core/.../HoldRule.java` `drivingStep` | skin link layer `Link.drivingStep` + `ONROAD_HOLD_MS` | copied, signature changed to take `Boolean onroad` |
| `core/.../HudFrame.java` | upper-screen helper `Hud.java` (field list / JSON key names) | field names of the published `/api/hud` contract; parser rewritten |
| `core/.../SceneFrame.java` | upper-screen helper `Hud.java` scene-frame merge | key names of the scene-stream contract; parser rewritten |
| `core/.../CommaHttpSource.java` | skin link layer `Link.java` | the client contract only (cookie from `/api/auth/session`, read the status before the body on 401, renew before ~5 min expiry, reconnect after the 60 s server close, 404 = poll only); code new |
| `core/.../SceneRenderer.java` | upper-screen helper `Scene.java` | design ideas only (fog-fading road, lane lines faded by probability, accent lines while steering, soft box cars, tracks drawn far to near, lead de-duplicated by track id); code new, new palette, generic car shape |
| `core/.../HudModel.java` | skin `Link` + upper `Helper` staleness behaviour | the rules (sticky stale, 2 s / 1 s grey, 10 s drive hold) re-implemented in one class |
| `tools/build.sh` | skin `build.sh` | the toolchain recipe (aapt2 link, javac -source 8, d8, zipalign, apksigner); script new, own debug key |
| `tools/host/.../AwtGfx.java` | upper host-test Java2D shim | the idea of rendering on the host with java.awt; code new (implements our `Gfx`, not a copy of android.graphics) |

| `core/.../Smoother.java` | upper-screen helper scene easing (tau ~0.18 s) | idea only; code new |
| `core/.../SettingsPanel.java`, `MapData.java`, `MapRenderer.java`, `tools/host/.../IconGen.java` | none | new in v0.2 |

| `core/.../Geo.java`, `MapChooser.java`, `MapSources.java`, `OfflineModel.java`, `OfflinePanel.java`, `RouteGeo.java`, `map-maplibre/...`, `build.gradle` | none (gps / nav / route field names from the comma contract) | new in v0.3 |

Add-ons under `addons/` are listed in `.export-exclude`; their provenance is in each add-on's README.

Not taken (car- and owner-specific parts, by design): steering-board switch, head-unit vendor services (fuel, range, climate),
LaneWatch / camera yield, Honda Wi-Fi packs, root helper, second-screen code, relay / server endpoints, keys,
tokens, SSIDs, recorded drives, the private palette tokens and car artwork.
