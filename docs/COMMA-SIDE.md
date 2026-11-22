# What the comma needs to run T-UI

T-UI reads these read-only HTTP endpoints from the comma (contract: docs/HUD-CONTRACT.md):
`GET /api/hud` (snapshot, polled ~4 Hz), `GET /api/hud/scene?hz=10` (NDJSON road-model stream while driving),
`GET /api/navigation/status` (StarPilot's own navigation: position, instruction, route; polled ~1 Hz). The display connects to the comma over its Wi-Fi hotspot.

## Status (2026-11)
- **Stock openpilot**: does not serve these endpoints. Its data lives in cereal messages on the device only.
- **Stock StarPilot**: has the Galaxy web server (port 8082) but not the HUD endpoints.
- **HUD API patch for StarPilot Galaxy** (MIT): https://github.com/mlocoteta/t-ui-comma-patch: `GET /api/hud` and
  `GET /api/hud/scene`, car-independent, read-only, host-tested on StarPilot `stable`, including a `gps` block (the
  comma's position). Navigation guidance + route: StarPilot's own `GET /api/navigation/status` (upstream, no patch).
  Next: an upstream pull request to StarPilot.
- **Standalone daemon (`hudd`, planned)**: the same JSON from a small process that subscribes to cereal
  (carState, selfdriveState/controlsState, modelV2, radarState, liveTracks, livePose, navInstruction where present), so
  stock openpilot, sunnypilot and FrogPilot users can run T-UI without StarPilot. Fields a fork lacks stay null; T-UI
  already hides unknown values.

## Rules the comma side follows
Read-only by default; no subscription until a client asks; leaves after 30 s without a client; null = unknown;
additive schema changes within a major `schemaVersion`; writes (if any) behind capability flags and the comma's own
confirmation rules, never enabled by default.
