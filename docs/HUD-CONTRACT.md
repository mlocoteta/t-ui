# HUD data contract (what v0 reads)

Source: a StarPilot comma running the Galaxy web server (default port 8082) with the HUD endpoints.
Everything is read-only. Units are SI on the wire (m, m/s, rad/s); the app converts with `isMetric`.
Absent or `null` means unknown: the app shows "--" or hides the item, it never invents a value.

## GET /api/hud (polled every 250 ms)
Fields v0 uses: `schemaVersion`, `state` (`current` | `stale` | `unavailable`), `seq`, `ageSeconds`, `onroad`,
`onroadAgeSeconds`, `isMetric`, `engaged`, `active`, `available`, `status`, `vEgo`, `vCruise`, `vCruiseCluster`,
`lead {dRel, yRel (right +), vRel, vLead, trackId}`, `steering {latActive}`, `alert {text1, text2, status, size}`,
`slc {limit, offset}`, `gearLetter`, `blinker`, `personality`, `distanceBars`, `laneChange {state, direction}`,
`model {laneLines[4][14][x,y], roadEdges[2][14][x,y], path[14][x,y], laneLineProbs[4], roadEdgeStds[2]}`,
`tracks [{id, dRel, yRel, vRel, vLead, stopped}]`. Model frame: x forward m, y right m.

Sticky-stale (schema 3): while `state` is `stale`, `onroad` / `engaged` / `vEgo` keep the last real sample and
`onroadAgeSeconds` says how old it is. Client rules: ARCHITECTURE.md "Staleness contract".

## GET /api/hud/scene?hz=10 (NDJSON stream while driving)
One line per new model frame, rate-limited to `hz`; `{"k":1}` keep-alive after 2 s idle; the server closes after
~60 s (reconnect). `{"s","a","v","yr","c","m":{"ll","re","pa","pr","sd"}|null,"l":{"d","y","vr","id"}|null,"lc","ld"}`;
point lists are flat `[x0,y0,x1,y1,...]`; `yr` and `c` are positive when turning left.
Status codes: 401 session needed, 404 older build (poll only), 429 two streams already open, 400 bad query.

## Auth
On the comma's own hotspot Galaxy treats the client as local. If a request answers 401, the app calls
`GET /api/auth/session` once, keeps the `galaxy_session` cookie and renews it every 4 minutes.

## Writes
None in v0. (Later, opt-in only: follow-distance personality. Never: steering or openpilot on/off.)
