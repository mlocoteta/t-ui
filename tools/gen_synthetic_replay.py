#!/usr/bin/env python3
"""Synthetic replay for TUI: a made-up 60 s drive on an imaginary 3-lane road, NO real route, NO coordinates.

Output: JSONL, one {"t": ms, "hud": {...}} (4 Hz, /api/hud schema 3 shape) or {"t": ms, "scene": {...}} (10 Hz,
scene-stream shape) per line. Deterministic (no randomness), so tests and screenshots are reproducible.

Script of the drive (seconds):
  0-4 parked (P)   4-8 pulling away (D)   8 engaged   12 lead appears   20-35 left bend   35-45 right bend
  46-50 lane change right   52-54 comma sampler blip (state "stale", sticky values)   56-58 takeover alert   58+ slowing
Usage: gen_synthetic_replay.py OUT.jsonl [seconds]  |  gen_synthetic_replay.py --map OUT.json (synthetic demo town)
"""
import json
import math
import sys

LANE_W = 3.6
# x of the 14 model points (openpilot X_IDXS grid indices 0,2,..,26)
XS = [192.0 * (i / 32.0) ** 2 for i in range(0, 27, 2)]


def speed(t):
    if t < 4:
        return 0.0
    if t < 16:
        return min(27.0, (t - 4) * 2.4)
    if t < 58:
        return 27.0
    return max(8.0, 27.0 - (t - 58) * 3.0)


def curvature(t):  # 1/m, + = left (openpilot convention in the scene stream)
    if 20 <= t < 35:
        return 0.0028 * math.sin(math.pi * (t - 20) / 15)
    if 35 <= t < 45:
        return -0.0035 * math.sin(math.pi * (t - 35) / 10)
    return 0.0


def lane_offset(t):  # ego lateral position relative to the starting lane centre (m, right +)
    if t < 46:
        return 0.0
    if t < 50:
        return LANE_W * (0.5 - 0.5 * math.cos(math.pi * (t - 46) / 4))
    return LANE_W


def road(t):
    k = curvature(t)
    off = lane_offset(t)

    def y(x, base):
        return round(base - off - k * x * x / 2.0, 2)

    lines = [[[round(x, 1), y(x, d * LANE_W)] for x in XS] for d in (-1.5, -0.5, 0.5, 1.5)]
    edges = [[[round(x, 1), y(x, d)] for x in XS] for d in (-1.5 * LANE_W - 0.4, 1.5 * LANE_W + 0.4)]
    # path (relative to the ego car): the current lane centre, easing into the target lane during a change
    target = LANE_W if t >= 45 else 0.0
    path = [[round(x, 1), round((target - off) * min(1.0, x / 40.0) - k * x * x / 2.0, 2)] for x in XS]
    return lines, edges, path


def lead(t):
    if t < 12 or t > 70:
        return None
    d = 34.0 + 6.0 * math.sin(t / 5.0)
    if 38 <= t < 44:  # the lead brakes: gap closes to ~11 m (lead-close state), then opens again
        d = d - (d - 11.0) * math.sin(math.pi * (t - 38) / 6.0)
    k = curvature(t)
    off = lane_offset(t)
    base = 0.0 if t < 48 else LANE_W  # the lead stays in the original lane; after the change it is in our new lane
    return {"dRel": round(d, 1), "yRel": round(base - off - k * d * d / 2, 2), "vRel": round(math.cos(t / 5.0) * 1.2, 2),
            "vLead": round(speed(t) + math.cos(t / 5.0) * 1.2, 2), "trackId": 7}


def tracks(t):
    if t < 6:
        return []
    k, off = curvature(t), lane_offset(t)
    out = []
    for tid, d, lane, dv in ((3, 22.0 + 4 * math.sin(t / 7), -1, 0.4), (11, 58.0 - (t % 30), 1, -0.9)):
        out.append({"id": tid, "dRel": round(d, 1), "yRel": round(lane * LANE_W - off - k * d * d / 2, 2),
                    "vRel": dv, "vLead": round(speed(t) + dv, 2), "stopped": False})
    ld = lead(t)
    if ld:
        out.append({"id": 7, "dRel": ld["dRel"], "yRel": ld["yRel"], "vRel": ld["vRel"], "vLead": ld["vLead"], "stopped": False})
    return sorted(out, key=lambda p: p["dRel"])


def lane_change(t):
    if 45 <= t < 46:
        return "preLaneChange", "right"
    if 46 <= t < 49:
        return "laneChangeStarting", "right"
    if 49 <= t < 50:
        return "laneChangeFinishing", "right"
    return "off", "none"


def hud(t, seq):
    v = speed(t)
    onroad = t >= 4
    engaged = 8 <= t < 57
    stale = 52 <= t < 54
    lines, edges, path = road(t)
    lc, ld = lane_change(t)
    alert = None
    if 56 <= t < 58:
        alert = {"text1": "TAKE CONTROL IMMEDIATELY", "text2": "Steering unavailable", "status": "critical", "size": "full"}
    elif 4 <= t < 8:
        alert = {"text1": "Ready to engage", "text2": "", "status": "normal", "size": "small"}
    h = {
        "schemaVersion": 3, "state": "stale" if stale else "current", "seq": seq,
        "ageSeconds": 1.8 if stale else 0.12, "onroad": onroad, "onroadAgeSeconds": round(t - 52 + 1.6, 1) if stale else 0.1,
        "isMetric": False, "engaged": engaged, "active": engaged, "available": onroad,
        "status": "engaged" if engaged else ("enabled" if onroad else "disabled"),
        "vEgo": round(v, 2), "vCruise": 29.06 if engaged else None, "vCruiseCluster": 29.06 if engaged else None,
        "lead": lead(t), "steering": {"latActive": engaged, "source": "model", "detail": None, "fault": None},
        "alert": alert, "experimental": False, "personality": "standard", "personalityWrite": False,
        "slc": {"limit": 29.06 if t < 40 else 24.59, "offset": 0, "source": "map"} if onroad else None,
        "blinker": "right" if 45 <= t < 50 else "none", "gearLetter": "P" if t < 4 else "D",
        "model": {"laneLines": lines, "roadEdges": edges, "path": path, "laneLineProbs": [0.6, 0.95, 0.95, 0.6],
                  "roadEdgeStds": [0.3, 0.3]} if t >= 4 else None,
        "tracks": tracks(t), "laneChange": {"state": lc, "direction": ld} if onroad else None,
        "distanceBars": 2, "error": "",
    }
    if stale:  # the comma keeps onroad/engaged/vEgo sticky; the rest is null
        for k in ("model", "lead", "tracks", "alert", "slc", "laneChange"):
            h[k] = None
    return h


def scene(t, seq):
    lines, edges, path = road(t)
    flat = lambda pts: [c for p in pts for c in p]
    ld = lead(t)
    lc, ldir = lane_change(t)
    v = speed(t)
    return {"s": seq, "a": 0.03, "v": round(v, 2), "yr": round(curvature(t) * v, 4), "c": round(curvature(t), 5),
            "m": {"ll": [flat(l) for l in lines], "re": [flat(e) for e in edges], "pa": flat(path),
                  "pr": [0.6, 0.95, 0.95, 0.6], "sd": [0.3, 0.3]},
            "l": {"d": ld["dRel"], "y": ld["yRel"], "vr": ld["vRel"], "id": ld["trackId"]} if ld else None,
            "lc": lc, "ld": ldir}


def demo_map():
    """Synthetic town in local metres (x east, y north): street grid, a major road, a river. NOT a real place."""
    def r1(v):
        return round(v, 1)

    def major_x(y):
        return 40.0 * math.sin(y / 300.0)

    major = [[r1(major_x(y)), r1(y)] for y in range(-400, 1901, 25)]
    roads = [{"c": "major", "p": major},
             {"c": "minor", "p": [[r1(major_x(900)), 900.0], [1500.0, 900.0]]},
             {"c": "minor", "p": [[400.0, -400.0], [400.0, 1900.0]]}]
    for x in range(-600, 1501, 150):
        if x != 0:
            roads.append({"c": "street", "p": [[float(x), -400.0], [float(x), 1900.0]]})
    for y in range(-300, 1801, 150):
        roads.append({"c": "street", "p": [[-600.0, float(y)], [1500.0, float(y)]]})
    river = [[-700.0, -400.0], [-420.0, -400.0], [-300.0, 400.0], [-380.0, 1100.0], [-250.0, 1900.0], [-700.0, 1900.0]]
    route = [[r1(major_x(y)), r1(y)] for y in range(-60, 901, 25)]
    route += [[200.0, 900.0], [400.0, 900.0], [400.0, 1200.0], [400.0, 1500.0], [400.0, 1900.0]]
    return {"format": "tui-map-1", "name": "synthetic demo town", "roads": roads, "water": [river], "route": route}


def main():
    if sys.argv[1] == "--map":
        with open(sys.argv[2], "w") as f:
            json.dump(demo_map(), f, separators=(",", ":"))
        print(f"wrote demo map -> {sys.argv[2]}")
        return
    out = sys.argv[1]
    secs = float(sys.argv[2]) if len(sys.argv) > 2 else 62.0
    rows = []
    for i in range(int(secs * 4)):
        t = i / 4.0
        rows.append((int(t * 1000), 0, {"t": int(t * 1000), "hud": hud(t, i + 1)}))
    for i in range(int(secs * 10)):
        t = i / 10.0
        if t < 8 or 52 <= t < 54.5:  # stream only while driving; it also drops during the blip
            continue
        rows.append((int(t * 1000), 1, {"t": int(t * 1000), "scene": scene(t, i + 1)}))
    rows.sort(key=lambda r: (r[0], r[1]))
    with open(out, "w") as f:
        f.write("# TUI synthetic replay (generated by tools/gen_synthetic_replay.py; no real drive data)\n")
        for _, _, r in rows:
            f.write(json.dumps(r, separators=(",", ":")) + "\n")
    print(f"wrote {len(rows)} frames, {secs:.0f} s -> {out}")


if __name__ == "__main__":
    main()
