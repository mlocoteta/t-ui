package org.tuiapp.core;

import java.util.Map;

/**
 * One compact frame of `GET /api/hud/scene?hz=N` (NDJSON, one object per line). Keys are short on purpose:
 * s seq, a age s, v vEgo m/s, yr yaw rate rad/s (+ = turning LEFT), c curvature 1/m (+ = LEFT),
 * m {ll 4 lane lines, re 2 road edges, pa path: flat [x0,y0,...]; pr probs; sd stds} | null,
 * l {d dRel, y yRel right+, vr vRel, id trackId} | null, lc laneChangeState, ld laneChangeDirection.
 * A keep-alive line {"k":1} parses to {@link #KEEPALIVE}.
 */
public final class SceneFrame {
    public static final SceneFrame KEEPALIVE = new SceneFrame();

    public long seq = -1;
    public double age = Double.NaN, vEgo = Double.NaN, yawRate = Double.NaN, curvature = Double.NaN;
    public boolean hasModel;
    public float[][][] laneLines, roadEdges;
    public float[][] path;
    public float[] laneLineProbs, roadEdgeStds;
    /** the frame carried an "l" key (lead block or null = definitely no lead) */
    public boolean hasLeadKey, leadPresent;
    public double leadDRel = Double.NaN, leadYRel = Double.NaN, leadVRel = Double.NaN;
    public int leadTrackId = -1;
    public String laneChangeState, laneChangeDir;

    public static SceneFrame parse(String line) {
        return from(Json.parseObject(line));
    }

    public static SceneFrame from(Map<String, Object> f) {
        if (f.containsKey("k") && !f.containsKey("s")) return KEEPALIVE;
        SceneFrame s = new SceneFrame();
        s.seq = Json.integer(f, "s", -1);
        s.age = Json.num(f, "a"); s.vEgo = Json.num(f, "v"); s.yawRate = Json.num(f, "yr"); s.curvature = Json.num(f, "c");
        Map<String, Object> m = Json.obj(f, "m");
        if (m != null) {
            s.laneLines = Json.lines(m.get("ll")); s.roadEdges = Json.lines(m.get("re"));
            s.path = Json.points(m.get("pa"));
            s.laneLineProbs = Json.floats(m.get("pr")); s.roadEdgeStds = Json.floats(m.get("sd"));
            s.hasModel = s.laneLines != null && s.roadEdges != null;
        }
        if (f.containsKey("l")) {
            s.hasLeadKey = true;
            Map<String, Object> l = Json.obj(f, "l");
            s.leadDRel = Json.num(l, "d"); s.leadPresent = !Double.isNaN(s.leadDRel);
            s.leadYRel = Json.num(l, "y"); s.leadVRel = Json.num(l, "vr"); s.leadTrackId = Json.integer(l, "id", -1);
        }
        s.laneChangeState = Json.str(f, "lc"); s.laneChangeDir = Json.str(f, "ld");
        return s;
    }
}
