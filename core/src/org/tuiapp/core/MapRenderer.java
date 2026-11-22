package org.tuiapp.core;

import java.util.Locale;

/**
 * Self-contained heading-up vector map (v0.2): roads by class, water, route (ahead in the accent colour, behind in
 * grey), puck, next-turn card. Zoom follows speed. Without MapData it draws an honest placeholder.
 * Line widths are in dp (not scaled with zoom), so roads stay readable on every screen.
 */
public final class MapRenderer {
    private float cx, cy, scale, fx, fy, rx, ry, px, py;
    private final float[] pos = new float[3];
    private float[] xs = new float[256], ys = new float[256];

    private float sx(float x, float y) { float dx = x - px, dy = y - py; return cx + (dx * rx + dy * ry) * scale; }
    private float sy(float x, float y) { float dx = x - px, dy = y - py; return cy - (dx * fx + dy * fy) * scale; }

    public void draw(Gfx g, Layout L, Layout.R m, boolean inset, MapData d, ViewState v, Theme t) {
        draw(g, L, m, inset, d != null && v.demo ? MapChooser.Mode.BUILTIN_DEMO : (d != null ? MapChooser.Mode.BUILTIN_DEMO : MapChooser.Mode.NO_FIX), d, v, t, null);
    }

    /**
     * mode decides what fills the pane (MapChooser): engine modes leave a transparent hole for the native map view
     * behind; built-in modes draw here. Turn card, mode label and attribution are drawn on top in every mode.
     */
    public void draw(Gfx g, Layout L, Layout.R m, boolean inset, MapChooser.Mode mode, MapData demo, ViewState v, Theme t, String attribution) {
        g.save();
        float rad = inset ? L.pxf(16) : 0;
        if (inset) g.clipRoundRect(m.x, m.y, m.w, m.h, rad); else g.clipRect(m.x, m.y, m.w, m.h);
        boolean turnDone = false;
        if (mode.engine()) g.clear(m.x, m.y, m.w, m.h);
        else {
            g.fillRect(m.x, m.y, m.w, m.h, t.mapBg);
            if (mode == MapChooser.Mode.BUILTIN_DEMO && demo != null && demo.route != null) turnDone = drawMap(g, L, m, inset, demo, v, t);
            else if (mode == MapChooser.Mode.BUILTIN_ROUTE && v.hasFix()) drawGeoRoute(g, L, m, inset, v, t);
            else placeholder(g, L, m, t, mode == MapChooser.Mode.NO_FIX && !v.demo ? "Waiting for GPS from the comma" : "No offline map yet",
                    mode == MapChooser.Mode.NO_FIX && !v.demo ? "The map follows the comma's GPS fix" : "Map packs come in a later version");
        }
        if (!turnDone && v.navActive && !Double.isNaN(v.navDistM)) turnCard(g, L, m, inset, new float[]{(float) v.navDistM, turnSign(v.navModifier)}, v, t);
        // mode label + attribution (bottom-left)
        String lbl = mode == MapChooser.Mode.BUILTIN_DEMO ? "synthetic demo map" : mode.engine() && attribution != null ? attribution : mode.engine() ? null : mode.label;
        if (lbl != null) {
            float fs = L.pxf(10.5f), w = Math.min(m.w - L.pxf(12), g.textWidth(lbl, fs, Gfx.W_REGULAR) + L.pxf(12));
            if (mode.engine()) g.fillRoundRect(m.x + L.pxf(4), m.b() - L.pxf(22), w, L.pxf(18), L.pxf(6), Theme.alpha(t.dark ? 0xFF000000 : 0xFFFFFFFF, 0.7f));
            g.text(lbl, m.x + L.pxf(10), m.b() - L.pxf(9), fit(g, lbl, fs, m.w - L.pxf(24)), Gfx.W_REGULAR, Gfx.ALIGN_LEFT, t.text3);
        }
        g.restore();
        if (inset) g.strokeRoundRect(m.x, m.y, m.w, m.h, rad, Math.max(1, L.pxf(1.5f)), t.cardStroke);
    }

    static float turnSign(String modifier) {
        if (modifier == null) return 0;
        return modifier.contains("left") ? 1 : modifier.contains("right") ? -1 : 0;
    }
    private static float fit(Gfx g, String s, float size, float maxW) { float w = g.textWidth(s, size, Gfx.W_REGULAR); return w <= maxW ? size : Math.max(size * 0.6f, size * maxW / w); }

    /** built-in fallback with real data: the comma's route around the fix, heading-up, no tiles */
    private void drawGeoRoute(Gfx g, Layout L, Layout.R m, boolean inset, ViewState v, Theme t) {
        float step = L.pxf(40);
        for (float x = m.x + step / 2; x < m.r(); x += step) g.line(x, m.y, x, m.b(), Math.max(1, L.pxf(1)), t.mapGrid);
        for (float y = m.y + step / 2; y < m.b(); y += step) g.line(m.x, y, m.r(), y, Math.max(1, L.pxf(1)), t.mapGrid);
        double hd = Double.isNaN(v.bearingDeg) ? 0 : v.bearingDeg;   // true north, clockwise
        double th = Math.toRadians(90 - hd);                          // math angle (0 = east, ccw)
        px = 0; py = 0; fx = (float) Math.cos(th); fy = (float) Math.sin(th); rx = fy; ry = -fx;
        cx = m.cx(); cy = m.y + m.h * 0.72f;
        double spd = Double.isNaN(v.speedMps) ? 0 : v.speedMps;
        scale = (m.h * 0.66f) / (float) (180 + Math.min(30, spd) * 10);
        RouteGeo r = v.route;
        if (r != null) {
            int n = r.size(); ensure(n);
            int k = 0;
            for (int i = 0; i < n; i++) {
                float ex = (float) Geo.eastM(r.lon[i], v.lat, v.lon), ny = (float) Geo.northM(r.lat[i], v.lat);
                if (Math.abs(ex) > 5000 || Math.abs(ny) > 5000) { if (k >= 2) { g.strokePolyline(xs, ys, k, L.pxf(7), t.accent); } k = 0; continue; }
                xs[k] = sx(ex, ny); ys[k] = sy(ex, ny); k++;
            }
            if (k >= 2) g.strokePolyline(xs, ys, k, L.pxf(7), t.accent);
        }
        float pr = L.pxf(inset ? 9 : 11);
        g.fillCircle(cx, cy, pr + L.pxf(3), 0xFFFFFFFF);
        g.fillCircle(cx, cy, pr, t.accent);
        float[] ax = {cx, cx + pr * 0.55f, cx, cx - pr * 0.55f}, ay = {cy - pr * 0.6f, cy + pr * 0.5f, cy + pr * 0.2f, cy + pr * 0.5f};
        g.fillPoly(ax, ay, 4, 0xFFFFFFFF);
    }

    private void placeholder(Gfx g, Layout L, Layout.R m, Theme t, String title, String sub) {
        float step = L.pxf(40);
        for (float x = m.x + step / 2; x < m.r(); x += step) g.line(x, m.y, x, m.b(), Math.max(1, L.pxf(1)), t.mapGrid);
        for (float y = m.y + step / 2; y < m.b(); y += step) g.line(m.x, y, m.r(), y, Math.max(1, L.pxf(1)), t.mapGrid);
        g.text(title, m.cx(), m.cy() - L.pxf(4), fit(g, title, L.pxf(16), m.w - L.pxf(20)), Gfx.W_SEMIBOLD, Gfx.ALIGN_CENTER, t.text2);
        g.text(sub, m.cx(), m.cy() + L.pxf(18), fit(g, sub, L.pxf(12), m.w - L.pxf(20)), Gfx.W_REGULAR, Gfx.ALIGN_CENTER, t.text3);
    }

    private boolean drawMap(Gfx g, Layout L, Layout.R m, boolean inset, MapData d, ViewState v, Theme t) {
        double len = d.routeLength();
        double s = len <= 0 ? 0 : v.distanceM % len;
        d.along(s, pos);
        px = pos[0]; py = pos[1];
        fx = (float) Math.cos(pos[2]); fy = (float) Math.sin(pos[2]); rx = fy; ry = -fx;
        cx = m.cx(); cy = m.y + m.h * 0.72f;
        double spd = Double.isNaN(v.speedMps) ? 0 : v.speedMps;
        float aheadM = (float) (180 + Math.min(30, spd) * 10);
        scale = (m.h * 0.66f) / aheadM;

        for (float[] w : d.water) poly(g, w, t.dark ? 0xFF16283A : 0xFFC9DDF0);
        int[] widths = {9, 6, 4};
        int[] casing = {t.dark ? 0xFF2B3038 : 0xFFC8CDD4, t.dark ? 0xFF262A31 : 0xFFD3D7DD, t.dark ? 0xFF22262C : 0xFFDADDE2};
        for (int pass = 0; pass < 2; pass++)
            for (int k = d.roads.size() - 1; k >= 0; k--) {
                int c = d.roadClass.get(k);
                float wpx = L.pxf(widths[c]) + (pass == 0 ? L.pxf(2) : 0);
                line(g, d.roads.get(k), wpx, pass == 0 ? casing[c] : (c == MapData.MAJOR ? (t.dark ? 0xFF4A505A : 0xFFFFFFFF) : t.mapRoad));
            }
        // route: travelled part grey, ahead accent (split at the puck)
        int n = d.route.length / 2, split = 1;
        while (split < n - 1 && d.routeCum[split] < s) split++;
        routePart(g, d, 0, split, true, L.pxf(6), Theme.alpha(t.text3, 0.6f));
        routePart(g, d, split, n, false, L.pxf(7), t.accent);
        // puck
        float pr = L.pxf(inset ? 9 : 11);
        g.fillCircle(cx, cy, pr + L.pxf(3), 0xFFFFFFFF);
        g.fillCircle(cx, cy, pr, t.accent);
        float[] ax = {cx, cx + pr * 0.55f, cx, cx - pr * 0.55f}, ay = {cy - pr * 0.6f, cy + pr * 0.5f, cy + pr * 0.2f, cy + pr * 0.5f};
        g.fillPoly(ax, ay, 4, 0xFFFFFFFF);
        // next turn
        float[] nt = d.nextTurn(s);
        if (nt != null && nt[0] < 2000) { turnCard(g, L, m, inset, nt, v, t); return true; }
        return false;
    }

    private void turnCard(Gfx g, Layout L, Layout.R m, boolean inset, float[] nt, ViewState v, Theme t) {
        String dist = distText(nt[0], v.metric());
        boolean left = nt[1] > 0, straight = nt[1] == 0;
        float pad = L.pxf(10), h = L.pxf(inset ? 44 : 64);
        float w = Math.min(m.w - 2 * pad, L.pxf(inset ? 150 : 260));
        float x = m.x + pad, y = m.y + pad;
        g.fillRoundRect(x, y, w, h, L.pxf(12), t.dark ? 0xF0101215 : 0xF01B1F24);
        float ar = h * 0.32f, ac = x + h / 2, ay = y + h / 2;
        // arrow: stem up then bend to the side
        float side = straight ? 0 : left ? -1 : 1;
        if (straight) {
            g.line(ac, ay + ar, ac, ay - ar * 0.3f, L.pxf(inset ? 3 : 4), 0xFFFFFFFF);
            float[] hx = {ac, ac + ar * 0.45f, ac - ar * 0.45f}, hy = {ay - ar, ay - ar * 0.3f, ay - ar * 0.3f};
            g.fillPoly(hx, hy, 3, 0xFFFFFFFF);
        } else {
            float[] sx = {ac - side * ar * 0.2f, ac - side * ar * 0.2f, ac + side * ar * 0.7f};
            float[] sy = {ay + ar, ay - ar * 0.2f, ay - ar * 0.2f};
            g.strokePolyline(sx, sy, 3, L.pxf(inset ? 3 : 4), 0xFFFFFFFF);
            float[] hx = {ac + side * ar, ac + side * ar * 0.55f, ac + side * ar * 0.55f};
            float[] hy = {ay - ar * 0.2f, ay - ar * 0.6f, ay + ar * 0.2f};
            g.fillPoly(hx, hy, 3, 0xFFFFFFFF);
        }
        float tx = x + h;
        g.text(dist, tx, y + (inset ? h * 0.62f : h * 0.47f), L.pxf(inset ? 16 : 22), Gfx.W_SEMIBOLD, Gfx.ALIGN_LEFT, 0xFFFFFFFF);
        if (!inset) g.text(straight ? "Continue" : left ? "Turn left" : "Turn right", tx, y + h * 0.80f, L.pxf(13), Gfx.W_REGULAR, Gfx.ALIGN_LEFT, 0xFFB8BEC6);
    }

    public static String distTextForTest(double m, boolean metric) { return distText(m, metric); }
    static String distText(double m, boolean metric) {
        if (metric) return m >= 1000 ? String.format(Locale.US, "%.1f km", m / 1000) : (Math.round(m / 10) * 10) + " m";
        double ft = m * 3.28084;
        return ft >= 1000 ? String.format(Locale.US, "%.1f mi", m / 1609.344) : (Math.round(ft / 50) * 50) + " ft";
    }

    private void ensure(int n) { if (xs.length < n) { xs = new float[n]; ys = new float[n]; } }

    private void line(Gfx g, float[] p, float w, int col) {
        int n = p.length / 2; ensure(n);
        for (int i = 0; i < n; i++) { xs[i] = sx(p[2 * i], p[2 * i + 1]); ys[i] = sy(p[2 * i], p[2 * i + 1]); }
        g.strokePolyline(xs, ys, n, w, col);
    }
    private void poly(Gfx g, float[] p, int col) {
        int n = p.length / 2; ensure(n);
        for (int i = 0; i < n; i++) { xs[i] = sx(p[2 * i], p[2 * i + 1]); ys[i] = sy(p[2 * i], p[2 * i + 1]); }
        g.fillPoly(xs, ys, n, col);
    }
    /** route vertices [from, to), starting or ending at the puck */
    private void routePart(Gfx g, MapData d, int from, int to, boolean behind, float w, int col) {
        ensure(to - from + 2);
        int k = 0;
        if (!behind) { xs[k] = cx; ys[k] = cy; k++; }
        for (int i = from; i < to; i++) { xs[k] = sx(d.route[2 * i], d.route[2 * i + 1]); ys[k] = sy(d.route[2 * i], d.route[2 * i + 1]); k++; }
        if (behind) { xs[k] = cx; ys[k] = cy; k++; }
        if (k >= 2) g.strokePolyline(xs, ys, k, w, col);
    }
}
