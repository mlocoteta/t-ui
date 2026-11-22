package org.tuiapp.core;

/**
 * The road view: a chase camera behind the ego car looking down the model's lane lines.
 * Model frame (openpilot): x forward m, y right m, z up. Ego car spans x = EGO_REAR..EGO_FRONT.
 * Without model data (schema 1, or parked) it draws a straight schematic lane. Allocates only a few
 * 4-point arrays per car per frame (v0; pool them if a slow unit shows GC pauses). Ideas (ghost road fading into fog, probability-faded lane lines,
 * soft box cars, tracks drawn back to front) follow the private head-unit scene; the code is new (CHANGES/ORIGIN.md).
 */
public final class SceneRenderer {
    static final float CAM_BACK = 10f, CAM_H = 4.0f, EGO_REAR = -3.2f, EGO_FRONT = 1.5f, EGO_W = 1.86f;
    static final float LANE_W = 3.6f, MAX_X = 120f, NEAR = 1.0f;
    /** nearest x drawn (depth 4 m: below the bottom of the view for every pane shape) */
    static final float X_NEAR = EGO_REAR - CAM_BACK + 4f;

    // projection state for the current frame
    private float px0, horizon, f, sx0, sy0, sw, sh;
    private final float[] xs = new float[64], ys = new float[64];
    private Theme t; private float greyAmt;

    /** project world point -> screen x; depth d = x - camera x */
    private float depth(float x) { return Math.max(NEAR, x - (EGO_REAR - CAM_BACK)); }
    private float sx(float x, float y) { return px0 + f * y / depth(x); }
    private float sy(float x, float z) { return horizon + f * (CAM_H - z) / depth(x); }
    private int c(int argb) { return greyAmt > 0 ? Theme.alpha(Theme.grey(argb, greyAmt), 1f - 0.35f * greyAmt) : argb; }

    public void draw(Gfx g, Layout.R r, ViewState v, Theme theme) {
        t = theme; greyAmt = v.grey ? 1f : 0f;
        sx0 = r.x; sy0 = r.y; sw = r.w; sh = r.h;
        horizon = sy0 + sh * 0.30f;
        f = Math.min(sh, sw * 0.8f);
        px0 = sx0 + sw / 2f;
        g.save();
        g.clipRect(r.x, r.y, r.w, r.h);
        g.fillRectV(r.x, r.y, r.w, horizon - r.y + 1, c(t.fogTop), c(t.fog));
        g.fillRectV(r.x, horizon, r.w, r.b() - horizon, c(t.fog), c(t.ground));

        boolean model = v.laneLines != null && v.laneLines.length >= 4 && v.roadEdges != null && v.roadEdges.length >= 2;
        if (model) drawModelRoad(g, v); else drawSchematicRoad(g);
        if (model && (v.engaged || v.latActive) && v.path != null) drawPath(g, v.path);

        // other cars, far to near; the lead is drawn with its own colour
        if (v.tracks != null) {
            float[][] tr = v.tracks.clone();
            java.util.Arrays.sort(tr, new java.util.Comparator<float[]>() { public int compare(float[] a, float[] b) { return Float.compare(b[0], a[0]); } });
            for (float[] p : tr) {
                if (p[0] < 2 || p[0] > MAX_X) continue;
                if (v.leadPresent && (int) p[3] == v.leadTrackId && v.leadTrackId >= 0) continue;
                if (v.leadPresent && Math.abs(p[0] - v.leadDRel) < 3 && Math.abs(p[1] - leadY(v)) < 1.2f) continue;
                boolean brk = p.length > 5 && p[5] > 0.5f;   // the comma marks the track as stopped
                art.draw(g, camera, p[0], p[1], slope(v, p[0]), t.car, brk, 0, t.dark, fx);
            }
        }
        if (v.leadPresent && !Double.isNaN(v.leadDRel) && v.leadDRel > 1 && v.leadDRel < MAX_X) {
            // brake lights only from data: lead close (time gap < 1 s) or closing faster than 1.5 m/s
            boolean brk = v.leadClose() || (!Double.isNaN(v.leadVRel) && v.leadVRel < -1.5);
            float lx = (float) v.leadDRel + EGO_FRONT;
            art.draw(g, camera, lx, leadY(v), slope(v, lx), t.car, brk, 0, t.dark, fx);
        }
        // ego: tilts a little into a lane change (eased), signals with the blinker / lane change, brake lights when held stopped
        String dir = v.laneChangeDir;
        boolean lcOn = v.laneChangeState != null && (v.laneChangeState.equals("laneChangeStarting") || v.laneChangeState.equals("laneChangeFinishing"));
        float target = lcOn && "right".equals(dir) ? 0.075f : lcOn && "left".equals(dir) ? -0.075f : 0f;
        if ("laneChangeFinishing".equals(v.laneChangeState)) target *= 0.4f;
        float dt = lastMs < 0 || v.nowMs < lastMs ? 1f : Math.min(1f, (v.nowMs - lastMs) / 1000f);
        lastMs = v.nowMs;
        egoYaw += (target - egoYaw) * (float) (1 - Math.exp(-dt / 0.35f));
        if (Math.abs(target) > 0 && lastMs == v.nowMs && dt >= 1f) egoYaw = target;   // stills / first frame: show the pose
        int sig = "left".equals(v.blinker) || (v.laneChangeState != null && !"off".equals(v.laneChangeState) && "left".equals(dir)) ? -1
                : "right".equals(v.blinker) || (v.laneChangeState != null && !"off".equals(v.laneChangeState) && "right".equals(dir)) ? 1 : 0;
        boolean blinkOn = (v.nowMs / 380) % 2 == 0;
        boolean held = v.driving && !Double.isNaN(v.speedMps) && v.speedMps < 0.3 && !"P".equals(v.gear);
        art.draw(g, camera, EGO_REAR, 0f, egoYaw, v.egoColor != 0 ? v.egoColor : t.ego, held, blinkOn ? sig : 0, t.dark, fx);
        g.restore();
    }

    private final VehicleArt art = new VehicleArt();
    private float egoYaw; private long lastMs = -1;
    private final VehicleArt.Cam camera = new VehicleArt.Cam() {
        public float sx(float x, float y) { return SceneRenderer.this.sx(x, y); }
        public float sy(float x, float z) { return SceneRenderer.this.sy(x, z); }
        public float camX() { return EGO_REAR - CAM_BACK; }
    };
    private final VehicleArt.ColorFx fx = new VehicleArt.ColorFx() { public int f(int argb) { return c(argb); } };
    /** heading of the road at distance x, from the model path (rad, + = towards the right) */
    private float slope(ViewState v, float x) {
        if (v.path == null || v.path.length < 2) return 0f;
        float a = yAt(v.path, x - 2), b = yAt(v.path, x + 2);
        return (float) Math.atan2(b - a, 4f);
    }

    private float leadY(ViewState v) {
        if (!Double.isNaN(v.leadYRel)) return (float) v.leadYRel;
        return v.path != null ? yAt(v.path, (float) v.leadDRel) : 0f;
    }

    // ------------------------------------------------------------------ road
    private void drawSchematicRoad(Gfx g) {
        float half = LANE_W * 1.5f;
        quad(g, -half, half, c(t.road));
        int mark = c(Theme.alpha(t.mark, 0.8f));
        for (float y : new float[]{-LANE_W / 2, LANE_W / 2}) straightLine(g, y, mark, true);
        for (float y : new float[]{-half, half}) straightLine(g, y, c(t.edge), false);
    }

    private void quad(Gfx g, float yl, float yr, int col) {
        float x0 = X_NEAR;
        float[] qx = {sx(x0, yl), sx(MAX_X, yl), sx(MAX_X, yr), sx(x0, yr)};
        float[] qy = {sy(x0, 0), sy(MAX_X, 0), sy(MAX_X, 0), sy(x0, 0)};
        g.fillPoly(qx, qy, 4, col);
    }

    private void straightLine(Gfx g, float y, int col, boolean dashed) {
        float x = X_NEAR;
        while (x < MAX_X) {
            float x1 = Math.min(MAX_X, x + (dashed ? 3f : 6f));
            float wpx = Math.max(1f, f * 0.15f / depth(x));
            g.line(sx(x, y), sy(x, 0), sx(x1, y), sy(x1, 0), wpx, col);
            x = x1 + (dashed ? 6f : 0f);
        }
    }

    private void drawModelRoad(Gfx g, ViewState v) {
        float[][] le = v.roadEdges[0], re = v.roadEdges[1];
        // road surface: left edge forward, right edge back; extend to the near edge of the view with the first point's y
        int n = 0;
        n = addNear(le, n);
        for (float[] p : le) if (p[0] <= MAX_X && n < 30) { xs[n] = sx(p[0], p[1]); ys[n] = sy(p[0], 0); n++; }
        for (int k = re.length - 1; k >= 0; k--) if (re[k][0] <= MAX_X && n < 62) { xs[n] = sx(re[k][0], re[k][1]); ys[n] = sy(re[k][0], 0); n++; }
        n = addNear(re, n);
        if (n >= 3) g.fillPoly(xs, ys, n, c(t.road));

        boolean steer = v.engaged || v.latActive;
        for (int i = 0; i < 4; i++) {
            float prob = v.laneLineProbs != null && v.laneLineProbs.length > i && !Float.isNaN(v.laneLineProbs[i]) ? v.laneLineProbs[i] : 0.7f;
            if (prob < 0.15f) continue;
            boolean ego = i == 1 || i == 2;
            int col = ego && steer ? t.accent : t.mark;
            modelLine(g, v.laneLines[i], c(Theme.alpha(col, Math.min(1f, 0.25f + prob))), ego ? 0.16f : 0.12f, !ego);
        }
        for (float[][] e : v.roadEdges) modelLine(g, e, c(t.edge), 0.2f, false);
    }

    private int addNear(float[][] edge, int n) {
        if (edge.length == 0) return n;
        float x = X_NEAR;
        xs[n] = sx(x, edge[0][1]); ys[n] = sy(x, 0);
        return n + 1;
    }

    private void modelLine(Gfx g, float[][] pts, int col, float widthM, boolean dashed) {
        if (pts == null || pts.length < 2) return;
        float nearX = X_NEAR;
        float px = nearX, py = pts[0][1];
        for (int k = 0; k < pts.length; k++) {
            float x = pts[k][0], y = pts[k][1];
            if (x > MAX_X) break;
            boolean draw = !dashed || (k % 2 == 0);
            if (draw) g.line(sx(px, py), sy(px, 0), sx(x, y), sy(x, 0), Math.max(1f, f * widthM / depth((px + x) / 2)), col);
            px = x; py = y;
        }
    }

    private void drawPath(Gfx g, float[][] path) {
        int n = 0; float half = 0.9f;
        for (float[] p : path) if (p[0] <= 80 && n < 30) { xs[n] = sx(p[0], p[1] - half); ys[n] = sy(p[0], 0); n++; }
        int m = n;
        for (int k = m - 1; k >= 0 && n < 62; k--) { float[] p = path[k]; xs[n] = sx(p[0], p[1] + half); ys[n] = sy(p[0], 0); n++; }
        if (n >= 3) g.fillPoly(xs, ys, n, c(Theme.alpha(t.accent, 0.22f)));
    }

    static float yAt(float[][] pts, float x) {
        if (pts == null || pts.length == 0) return 0f;
        if (x <= pts[0][0]) return pts[0][1];
        for (int k = 1; k < pts.length; k++) {
            if (x <= pts[k][0]) {
                float a = (x - pts[k - 1][0]) / Math.max(1e-3f, pts[k][0] - pts[k - 1][0]);
                return pts[k - 1][1] + a * (pts[k][1] - pts[k - 1][1]);
            }
        }
        return pts[pts.length - 1][1];
    }

}
