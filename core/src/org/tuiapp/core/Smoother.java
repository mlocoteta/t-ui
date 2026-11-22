package org.tuiapp.core;

/**
 * Eases what is drawn between comma frames (4-10 Hz data, ~30 fps drawing) so nothing jumps:
 * speed, lane lines / road edges / path (lateral y per point), the lead car, and radar cars matched by track id.
 * First-order lag: shown += (target - shown) * (1 - exp(-dt / tau)). Never overshoots. Snaps instead of easing when
 * a value appears or disappears, when array shapes change, or after a long gap (> SNAP_GAP_MS), so stale shapes never
 * morph into new ones. Pure; time is passed in (host tests: tests/host).
 */
public final class Smoother {
    public static final float TAU_SPEED = 0.25f, TAU_GEOM = 0.15f, TAU_CARS = 0.18f;
    public static final long SNAP_GAP_MS = 1000;
    /** a car whose distance jumps more than this snaps (a different object took the slot) */
    public static final float SNAP_JUMP_M = 12f;

    private ViewState shown;
    private long at = -1;

    public void reset() { shown = null; at = -1; }

    /** eased copy of target for time now (ms). target is not modified. */
    public ViewState apply(ViewState target, long now) {
        ViewState out = target.copy();
        if (shown == null || at < 0 || now - at > SNAP_GAP_MS || now < at) { shown = out; at = now; return out; }
        float dt = (now - at) / 1000f; at = now;
        out.speedMps = ease(shown.speedMps, target.speedMps, a(dt, TAU_SPEED));
        float ag = a(dt, TAU_GEOM), ac = a(dt, TAU_CARS);
        out.laneLines = lines(shown.laneLines, target.laneLines, ag);
        out.roadEdges = lines(shown.roadEdges, target.roadEdges, ag);
        out.path = pts(shown.path, target.path, ag);
        if (target.leadPresent && shown.leadPresent && !Double.isNaN(shown.leadDRel) && Math.abs(target.leadDRel - shown.leadDRel) < SNAP_JUMP_M) {
            out.leadDRel = ease(shown.leadDRel, target.leadDRel, ac);
            out.leadYRel = ease(shown.leadYRel, target.leadYRel, ac);
        }
        out.tracks = tracks(shown.tracks, target.tracks, ac);
        shown = out;
        return out;
    }

    static float a(float dt, float tau) { return (float) (1 - Math.exp(-Math.max(0, dt) / tau)); }

    static double ease(double from, double to, float a) {
        if (Double.isNaN(from) || Double.isNaN(to)) return to;
        return from + (to - from) * a;
    }

    static float[][] pts(float[][] from, float[][] to, float a) {
        if (from == null || to == null || from.length != to.length) return to;
        float[][] r = new float[to.length][];
        for (int i = 0; i < to.length; i++) {
            if (from[i].length < 2 || to[i].length < 2) return to;
            r[i] = new float[]{to[i][0], from[i][1] + (to[i][1] - from[i][1]) * a};
        }
        return r;
    }

    static float[][][] lines(float[][][] from, float[][][] to, float a) {
        if (from == null || to == null || from.length != to.length) return to;
        float[][][] r = new float[to.length][][];
        for (int i = 0; i < to.length; i++) r[i] = pts(from[i], to[i], a);
        return r;
    }

    /** track rows {dRel, yRel, vRel, id, vLead, stopped}: eased only when the same id was shown before */
    static float[][] tracks(float[][] from, float[][] to, float a) {
        if (from == null || to == null) return to;
        float[][] r = new float[to.length][];
        for (int i = 0; i < to.length; i++) {
            float[] t = to[i]; r[i] = t;
            if (t.length < 4 || t[3] < 0) continue;
            for (float[] f : from) {
                if (f.length >= 4 && f[3] == t[3] && Math.abs(f[0] - t[0]) < SNAP_JUMP_M) {
                    float[] c = t.clone(); c[0] = f[0] + (t[0] - f[0]) * a; c[1] = f[1] + (t[1] - f[1]) * a; r[i] = c; break;
                }
            }
        }
        return r;
    }
}
