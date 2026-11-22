# Architecture (v0.3)

Three layers. Data flows one way: source -> model -> snapshot -> renderer.

```
 DataSource (core)            HudModel (core)              DriveRenderer (core)          Device layer (app)
 CommaHttpSource  ─┐  onHud    sticky-stale rules    snapshot  Layout engine ─┐            MainActivity
 ReplaySource     ─┼────────▶  scene merge     ───────────▶   SceneRenderer  ├──▶ Gfx ──▶ AndroidGfx (Canvas)
 (later: hudd, BT) ┘  onScene  drive hold, grey   ViewState    cards / alert ─┘            DriveView
                      onLink                                                     host: AwtGfx (PNG, tests)
```

## 1. Device-independent core (`core/`, package `org.tuiapp.core`)

Plain Java 8 source, no Android imports, runs on the host JVM (all host tests) and on Android API 21+.

- `Brand` the one place for name, package id, version.
- `Json` tiny dependency-free JSON reader (`org.json` is stubbed in the host android.jar; no download needed).
- `HudFrame` typed `/api/hud` (schema 3; schema 1/2 parse with fewer fields). Unknown = NaN / null / -1.
- `SceneFrame` one scene-stream NDJSON line (compact keys; `{"k":1}` keep-alive).
- `HudModel` folds frames into a `ViewState`; implements the staleness contract (below). Time is injected (ms).
- `HoldRule` pure rules: grey, scene-merge window, drive hold.
- `DataSource` interface (`start(Listener)`, `stop()`, `describe()`); read-only by design: no write method.
  - `CommaHttpSource` StarPilot Galaxy over HTTP: polls `/api/hud` every 250 ms; while driving keeps
    `/api/hud/scene?hz=10` open (server closes ~60 s, reconnect; 404 = old build, poll only, retry after 60 s;
    429 = slots busy). 401 -> `GET /api/auth/session` for a `galaxy_session` cookie, renewed every 4 min.
    The address is a setting; nothing is discovered automatically in v0.
  - `ReplaySource` JSONL replay (`{"t":ms,"hud":{..}}` / `{"t":ms,"scene":{..}}`), real-time or stepped for tests.
- `Layout` the layout engine: (width px, height px, density, mode, driver side) -> pane + card rectangles.
- `Gfx` a small drawing interface (subset of Canvas). `Theme` day / night palettes.
- `SceneRenderer` the road view (chase camera, model lane lines, road edges, path band, lead + radar cars, ego car).
- `DriveRenderer` the whole screen: scene, map pane / inset, cards, add-on card slot, portrait bottom bar, alert
  banner (icon, pulsing red edge on takeover), and the "No comma found" connection screen (no data yet).
- `Smoother` eases speed, lane lines / edges / path, lead and radar cars (matched by track id) between frames;
  snaps on shape changes, new ids, jumps > 12 m and gaps > 1 s (host-tested: no overshoot, tau, convergence).
- `Settings` persisted user settings (flat string store: SharedPreferences on Android): comma address, theme
  auto/light/dark, units auto/mph/km/h, layout auto/full/split, keep screen on, right-hand drive, add-on keys.
- `SettingsPanel` the settings screen, drawn and hit-tested by the core (same pixels on device and host);
  flows into two columns on wide landscape screens, scrolls when it overflows.
- `MapData` / `MapRenderer` self-contained heading-up vector map (demo town; real route around the comma fix when
  there are no tiles), next-turn card, mode label + attribution; leaves a transparent hole in engine modes.
- `MapChooser` online -> offline -> built-in decision; `MapSources` tile sources, User-Agent, per-source offline limits;
  `Geo` Web Mercator / tile counts / size estimates / viewport; `RouteGeo` comma route geometry;
  `OfflineModel` + `OfflinePanel` the offline maps screen (core-drawn, host-tested). Details: docs/MAP.md.

### Extension points (add-ons, docs/ADDONS.md)
- `Addon` (read extra `/api/hud` fields from `HudFrame.raw`, settings rows, write requests, drive-screen cards),
  `Addons` registry (asset `addons.txt`; absent in the default APK), `SettingRow`, `AddonCard`, `WriteRequest`.
- Car- or owner-specific features live only in `addons/<name>/` and are compiled only with `ADDONS=<name>`.
  The core contains no car-specific endpoint or field names; `tools/clean-export-check.sh` fails if any appear
  outside an excluded add-on dir, and `tools/build.sh` leak-checks every default APK.
- Writes: `CommaHttpSource.post` refuses while writes are off; the app turns them on only for one request an add-on
  returned after its own guards (setting on, capability flag, stopped, disengaged, confirm tap).

### Staleness contract (schema 3)
- `state: current` -> new base frame. `stale` -> the comma keeps `onroad` / `engaged` / `vEgo` sticky and says how old
  in `onroadAgeSeconds`; the model also keeps the last real geometry, lead and tracks. `unavailable` -> no data.
- Grey the scene ("comma data N s old") only after 2 s without a current `/api/hud` AND 1 s without a scene frame.
- A drive ends only on explicit `onroad: false`, or 10 s after the last `onroad: true`.
- A scene frame merges into the base while the base is <= 2 s old, or <= 60 s old while the stream is live.

## 2. Thin device layer (`app/`, package `org.tuiapp.drive`)

- `MainActivity` full-screen, settings persistence, picks the source (comma address set -> `CommaHttpSource`, else
  the bundled synthetic replay + demo map), loads add-ons if `addons.txt` exists, sends add-on writes off-thread.
- `DriveView` asks the core for a `Layout` on every size change (dp from `DisplayMetrics.density`), applies units,
  add-on cards and easing, redraws ~30 fps; long-press opens the core settings panel (tap chips, drag to scroll).
- `MapEngine` (optional native map behind the drive view). `map-maplibre/` (Gradle flavour only):
  `MapLibreEngine` = MapLibre Native view, puck + route layers, OfflineManager areas, one OkHttp client (generic
  User-Agent, max 4 parallel requests).
- App icon: drawn at build time by `tools/host/.../IconGen` (original artwork, no binary in the tree).
- `AndroidGfx` `Gfx` on `android.graphics.Canvas`.
Future device profiles (PLAN.md 4.2: display setup, GPU policy, vehicle bridge, yield rules, keys) plug in here,
never in core. v0 has exactly one profile: generic Android, no root, nothing native.

## 3. Layout engine v0

- Units: design in dp; `u = density * clamp(shortSideDp / 480, 0.8, 1.6)` px per design unit.
- `FLOATING`: full-screen scene; cards on the driver side; map as a rounded corner inset on the passenger side
  (moves above the alert banner on narrow screens).
- `SPLIT`: landscape = scene on the driver side + map pane (50/50; 42/58 at aspect >= 2.2); portrait = scene 55 % on
  top + map below.
- L3 portrait: scene 55 % of the height above a 56 dp bottom bar (link state, settings hint), map between.
- `AUTO`: portrait -> SPLIT; landscape >= 900 dp wide and aspect >= 1.5 -> SPLIT; else FLOATING.
- Invariants (host-tested on 10 screen profiles x 2 modes x 2 driver sides): cards inside the scene, no card
  overlaps, inset clear of cards, panes tile the screen, card sizes >= 120 dp / 56 dp.

## Not in v0.3
Device verification of the MapLibre flavour (docs/MAP.md "not verified"), L2 three-zone ultrawide (ultrawide uses a 42/58
split), auto-discovery, device adapters, Gradle, bench page, parked-only gate for the settings screen.
