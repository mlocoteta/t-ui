package org.tuiapp.core;

/**
 * One generic car silhouette for the road view (original, no maker shape): rounded rear, tapered cabin and roof, soft
 * shadow and subtle shading. Every other car uses the same shape and one neutral colour, because the comma's data says
 * nothing about vehicle type, size or paint. Only data-driven states are drawn: brake lights (caller decides from the
 * comma data), turn signal (ego only, from blinker / lane-change data), grey when the data is old (caller's colour fx),
 * and a small yaw (road curvature from the model path, ego lane-change lean).
 * Car frame: u forward from the rear bumper (m), v right of the centre line (m), z up (m).
 */
public final class VehicleArt {
    /** projection supplied by the scene: world x forward / y right / z up -> screen */
    public interface Cam { float sx(float x, float y); float sy(float x, float z); float camX(); }
    /** the scene's colour filter (identity, or greyed when comma data is old) */
    public interface ColorFx { int f(int argb); }

    public static final int TAIL_UNLIT = 0xFF6A1814, TAIL_LIT = 0xFFFF2A22, GLOW = 0x55FF2A22, SIGNAL = 0xFFFFA51F;

    // one generic car: length, half width, shoulder height, cabin start/end, roof height, ground clearance
    static final float LEN = 4.7f, HW = 0.92f, ZB = 0.92f, CU0 = 1.05f, CU1 = 3.15f, ZR = 1.42f, ZC = 0.24f;

    private final float[] qx = new float[16], qy = new float[16];
    private Cam cam;
    private float xr, yr, cs, sn;

    private float wx(float u, float v) { return xr + u * cs - v * sn; }
    private float wy(float u, float v) { return yr + u * sn + v * cs; }

    /** polygon in the car frame from (u, v, z) triples */
    private void poly(Gfx g, int col, float... p) {
        int n = p.length / 3;
        for (int i = 0; i < n; i++) { float x = wx(p[3 * i], p[3 * i + 1]); qx[i] = cam.sx(x, wy(p[3 * i], p[3 * i + 1])); qy[i] = cam.sy(x, p[3 * i + 2]); }
        g.fillPoly(qx, qy, n, col);
    }

    /**
     * @param body   paint (one neutral colour for all other cars; the theme's ego colour for our car)
     * @param brake  brake lights on (data-driven); signal -1 left, +1 right, 0 off (blink phase decided by the caller)
     */
    public void draw(Gfx g, Cam c, float xRear, float yCentre, float yawRad, int body, boolean brake, int signal, boolean dark, ColorFx fx) {
        cam = c; xr = xRear; yr = yCentre; cs = (float) Math.cos(yawRad); sn = (float) Math.sin(yawRad);
        float L = LEN, hw = HW;
        int base = fx.f(body);
        int lower = fx.f(Theme.mix(body, 0xFF000000, dark ? 0.30f : 0.16f));     // rear face, slightly darker
        int upper = fx.f(Theme.mix(body, 0xFFFFFFFF, dark ? 0.08f : 0.14f));     // deck + roof catch the light
        int side = fx.f(Theme.mix(body, 0xFF000000, dark ? 0.40f : 0.26f));
        int glass = fx.f(Theme.mix(body, dark ? 0xFF000000 : 0xFF1C2128, dark ? 0.75f : 0.62f));
        int sh = fx.f(dark ? 0x3A000000 : 0x1C000000);
        // the same lamp colours on every car in both themes: unlit = dim dark red, lit = bright red; amber = turn signal only
        int tail = fx.f(brake ? TAIL_LIT : TAIL_UNLIT), glow = fx.f(GLOW), amber = fx.f(SIGNAL);

        // soft shadow: two rounded layers
        poly(g, sh, -0.35f, -hw * 0.8f, 0, -0.2f, -hw - 0.2f, 0, L + 0.1f, -hw - 0.2f, 0, L + 0.25f, -hw * 0.7f, 0, L + 0.25f, hw * 0.7f, 0, L + 0.1f, hw + 0.2f, 0, -0.2f, hw + 0.2f, 0, -0.35f, hw * 0.8f, 0);
        poly(g, sh, -0.1f, -hw * 0.85f, 0, 0.05f, -hw - 0.04f, 0, L * 0.9f, -hw - 0.04f, 0, L * 0.9f, hw + 0.04f, 0, 0.05f, hw + 0.04f, 0, -0.1f, hw * 0.85f, 0);

        // side of the car that faces the camera (gives depth on curves and in other lanes)
        float midX = wx(L / 2, 0), midY = wy(L / 2, 0);
        int vis = (-sn * (c.camX() - midX) + cs * (-midY)) > 0 ? 1 : -1;
        float vs = vis * hw;
        poly(g, side, 0.25f, vs, ZC, L - 0.35f, vs, ZC, L - 0.15f, vs * 0.97f, ZB - 0.1f, L - 0.6f, vs * 0.98f, ZB, 0.15f, vs, ZB - 0.02f, 0.02f, vs * 0.98f, ZB * 0.6f);

        // rear face: rounded outline (chamfered corners, slight taper towards the bottom)
        float r = 0.16f;
        poly(g, lower, 0, -hw + r, ZC, 0, hw - r, ZC, 0, hw - 0.03f, ZC + r, 0, hw, ZB - r, 0, hw - r * 0.6f, ZB, 0, -hw + r * 0.6f, ZB, 0, -hw, ZB - r, 0, -hw + 0.03f, ZC + r);
        // trunk deck: rounded rear edge, narrows a little towards the cabin
        poly(g, upper, 0, -hw + r * 0.6f, ZB, 0.12f, -hw + 0.02f, ZB + 0.02f, CU0, -hw + 0.05f, ZB + 0.04f, CU0, hw - 0.05f, ZB + 0.04f, 0.12f, hw - 0.02f, ZB + 0.02f, 0, hw - r * 0.6f, ZB);
        // cabin: tapered (roof narrower than the shoulders), sloped rear glass, rounded roof corners
        float rw = hw - 0.22f, ru0 = CU0 + 0.55f, ru1 = CU1 - 0.45f;
        poly(g, side, CU0, vs * 0.95f, ZB + 0.04f, CU1, vs * 0.95f, ZB + 0.04f, ru1, vis * rw, ZR, ru0, vis * rw, ZR);
        poly(g, base, CU0, -hw + 0.06f, ZB + 0.04f, CU0, hw - 0.06f, ZB + 0.04f, ru0, rw, ZR - 0.02f, ru0 + 0.05f, rw - 0.08f, ZR, ru0 + 0.05f, -rw + 0.08f, ZR, ru0, -rw, ZR - 0.02f);
        poly(g, glass, CU0 + 0.06f, -hw + 0.2f, ZB + 0.09f, CU0 + 0.06f, hw - 0.2f, ZB + 0.09f, ru0 - 0.06f, rw - 0.1f, ZR - 0.07f, ru0 - 0.06f, -rw + 0.1f, ZR - 0.07f);
        poly(g, upper, ru0, -rw + 0.08f, ZR, ru0 + 0.08f, -rw, ZR, ru1 - 0.08f, -rw, ZR, ru1, -rw + 0.08f, ZR, ru1, rw - 0.08f, ZR, ru1 - 0.08f, rw, ZR, ru0 + 0.08f, rw, ZR, ru0, rw - 0.08f, ZR);

        // tail lights (thin bars that follow the rounded corners) + brake glow; signal only from data
        float zl = ZB - 0.26f, lh = 0.11f, lw = 0.42f;
        if (brake) {
            poly(g, glow, -0.01f, -hw - 0.05f, zl - 0.07f, -0.01f, -hw + lw + 0.06f, zl - 0.07f, -0.01f, -hw + lw + 0.06f, zl + lh + 0.07f, -0.01f, -hw - 0.05f, zl + lh + 0.07f);
            poly(g, glow, -0.01f, hw - lw - 0.06f, zl - 0.07f, -0.01f, hw + 0.05f, zl - 0.07f, -0.01f, hw + 0.05f, zl + lh + 0.07f, -0.01f, hw - lw - 0.06f, zl + lh + 0.07f);
        }
        int lc = signal < 0 ? amber : tail, rc = signal > 0 ? amber : tail;
        poly(g, lc, -0.02f, -hw + 0.04f, zl, -0.02f, -hw + lw, zl + 0.01f, -0.02f, -hw + lw, zl + lh, -0.02f, -hw + 0.02f, zl + lh);
        poly(g, rc, -0.02f, hw - lw, zl + 0.01f, -0.02f, hw - 0.04f, zl, -0.02f, hw - 0.02f, zl + lh, -0.02f, hw - lw, zl + lh);
        if (brake) poly(g, tail, CU0 + 0.08f, -0.28f, ZB + 0.12f, CU0 + 0.08f, 0.28f, ZB + 0.12f, CU0 + 0.1f, 0.28f, ZB + 0.16f, CU0 + 0.1f, -0.28f, ZB + 0.16f);
        // dark lower edge under the bumper (ground contact)
        poly(g, fx.f(dark ? 0xFF0E1013 : 0xFF2A2E33), 0.02f, -hw + 0.2f, 0.02f, 0.02f, hw - 0.2f, 0.02f, 0.02f, hw - 0.18f, ZC, 0.02f, -hw + 0.18f, ZC);
    }
}
