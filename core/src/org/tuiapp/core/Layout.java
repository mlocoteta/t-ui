package org.tuiapp.core;

/**
 * Layout engine v0: one screen of any size and density -> pane and card rectangles in pixels.
 * Everything is designed in dp and scaled by `u` (px per design unit) = density * clamp(shortSideDp / 480, 0.8, 1.6),
 * so a 7" 800x480 unit, a 10" 1280x720 unit and a phone in portrait all get readable cards.
 *
 * Modes
 *  - FLOATING: full-screen scene, cards float on it, map as a rounded corner inset on the passenger side.
 *  - SPLIT: landscape = scene pane on the driver side + map pane (50/50) (L1); aspect >= 2.2 = three zones
 *           info column (cards) | scene | map (L2);
 *           portrait  = scene on top (55 % of the height above the bar) + map + 56 dp bottom bar (L3).
 *  - AUTO: portrait -> SPLIT; landscape >= 900 dp wide and aspect >= 1.5 -> SPLIT; else FLOATING.
 * Cards (speed, state chip, set/limit, stale label) sit in the scene's top corner on the driver side; the alert
 * banner sits at the bottom of the scene, centred. Cards never overlap each other (tests/host LayoutTest).
 */
public final class Layout {
    public enum Mode { AUTO, FLOATING, SPLIT }

    public static final class R {
        public final int x, y, w, h;
        public R(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = Math.max(0, w); this.h = Math.max(0, h); }
        public int r() { return x + w; }
        public int b() { return y + h; }
        public int cx() { return x + w / 2; }
        public int cy() { return y + h / 2; }
        public boolean intersects(R o) { return o != null && x < o.r() && o.x < r() && y < o.b() && o.y < b(); }
        public boolean inside(R o) { return x >= o.x && y >= o.y && r() <= o.r() && b() <= o.b(); }
        @Override public String toString() { return x + "," + y + " " + w + "x" + h; }
    }

    public final int w, h;
    public final float density, u;
    public final boolean portrait, driverLeft;
    public final Mode mode;
    public final R screen, scene, map, speed, chip, setLimit, alert, staleLabel, extra, bar;
    /** L2 three-zone ultrawide: the info column on the driver side that holds the cards (width 0 otherwise) */
    public final R info;
    public boolean threeZone() { return info.w > 0; }
    /** cards (and chip / stale pills) anchor on the left edge of the scene */
    public final boolean cardsLeft;
    /** map is a floating corner inset (true) or a full pane (false) */
    public final boolean mapInset;

    public static final float MIN_TOUCH_DP = 56;

    private Layout(int w, int h, float density, Mode pref, boolean driverLeft) {
        if (w <= 0 || h <= 0 || density <= 0) throw new IllegalArgumentException("bad screen " + w + "x" + h + "@" + density);
        this.w = w; this.h = h; this.density = density; this.driverLeft = driverLeft;
        portrait = h > w;
        float wDp = w / density, hDp = h / density, shortDp = Math.min(wDp, hDp);
        u = density * clamp(shortDp / 480f, 0.8f, 1.6f);
        float aspect = Math.max(w, h) / (float) Math.min(w, h);
        Mode m = pref == null ? Mode.AUTO : pref;
        if (m == Mode.AUTO) m = portrait || (wDp >= 900 && aspect >= 1.5f) ? Mode.SPLIT : Mode.FLOATING;
        mode = m;
        screen = new R(0, 0, w, h);
        int pad = px(12);
        R mp; R inf = new R(0, 0, 0, 0);

        if (mode == Mode.FLOATING) {
            scene = screen;
            mapInset = true;
            int mw = (int) clampF(w * 0.30f, px(160), Math.min(px(420), w * 0.42f));
            int mh = (int) Math.min(mw * 0.75f, h * 0.45f);
            int mx = driverLeft ? w - pad - mw : pad;
            mp = new R(mx, pad, mw, mh);
        } else if (portrait) {   // L3 portrait stack: scene on top, map, bottom bar
            int bh = px(56);
            int sh = Math.round((h - bh) * 0.55f);
            scene = new R(0, 0, w, sh);
            mp = new R(0, sh, w, h - bh - sh);
            mapInset = false;
        } else if (aspect >= 2.2f) {   // L2 three-zone ultrawide: info column | scene | map (mirrored for RHD)
            int iw = Math.max(px(190), Math.round(w * 0.2f)), sw = Math.round(w * 0.4f);
            if (driverLeft) { inf = new R(0, 0, iw, h); scene = new R(iw, 0, sw, h); mp = new R(iw + sw, 0, w - iw - sw, h); }
            else { inf = new R(w - iw, 0, iw, h); scene = new R(w - iw - sw, 0, sw, h); mp = new R(0, 0, w - iw - sw, h); }
            mapInset = false;
        } else {
            int sw = Math.round(w * 0.5f);
            scene = driverLeft ? new R(0, 0, sw, h) : new R(w - sw, 0, sw, h);
            mp = driverLeft ? new R(sw, 0, w - sw, h) : new R(0, 0, w - sw, h);
            mapInset = false;
        }
        info = inf;
        R ca = threeZone() ? info : scene;   // where the cards go

        // cards, relative to the scene, on the driver side; chip / stale rects are the space a pill may use,
        // the renderer anchors the pill on the driver side (cardsLeft)
        cardsLeft = driverLeft;
        int sw = px(150), shh = px(100);
        int sx = driverLeft ? ca.x + pad : ca.r() - pad - sw;
        speed = new R(sx, ca.y + pad, sw, shh);
        int lw = px(84);
        int lx = driverLeft ? speed.r() + px(8) : speed.x - px(8) - lw;
        setLimit = threeZone() ? new R(driverLeft ? sx : ca.r() - pad - lw, speed.b() + px(8), lw, shh)   // stacked in the column
                : new R(lx, speed.y, lw, shh);
        int cw = Math.min(px(220), ca.w - 2 * pad), stw = Math.min(px(240), ca.w - 2 * pad);
        chip = new R(driverLeft ? sx : ca.r() - pad - cw, (threeZone() ? setLimit.b() : speed.b()) + px(8), cw, px(40));
        staleLabel = new R(driverLeft ? sx : ca.r() - pad - stw, chip.b() + px(8), stw, px(28));
        extra = new R(staleLabel.x, staleLabel.b() + px(8), stw, px(28));   // add-on card slot
        bar = mode == Mode.SPLIT && portrait ? new R(0, h - px(56), w, px(56)) : new R(0, h, w, 0);
        int aw = Math.min(scene.w - 2 * pad, px(560)), ah = px(72);
        alert = new R(scene.cx() - aw / 2, scene.b() - pad - ah, aw, ah);
        if (mapInset) {
            boolean fits = driverLeft ? mp.x >= setLimit.r() + pad : mp.r() + pad <= setLimit.x;
            if (!fits) {   // narrow screen: the inset moves to the passenger side just above the alert banner
                int my = Math.max(extra.b() + pad, alert.y - pad - mp.h);
                mp = new R(mp.x, my, mp.w, Math.min(mp.h, alert.y - pad - my));
            }
        }
        map = mp;
    }

    public static Layout compute(int wPx, int hPx, float density, Mode pref, boolean driverLeft) {
        return new Layout(wPx, hPx, density, pref, driverLeft);
    }
    public static Layout compute(int wPx, int hPx, float density) { return compute(wPx, hPx, density, Mode.AUTO, true); }

    /** dp -> px including the size scale */
    public int px(float dp) { return Math.round(dp * u); }
    public float pxf(float dp) { return dp * u; }

    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
    private static float clampF(float v, float lo, float hi) { return hi < lo ? hi : clamp(v, lo, hi); }

    public R[] cards() { return new R[]{speed, setLimit, chip, staleLabel, extra, alert}; }

    @Override public String toString() {
        return w + "x" + h + "@" + density + " " + mode + (portrait ? " portrait" : "") + " u=" + String.format(java.util.Locale.US, "%.2f", u)
                + " scene=" + scene + " map=" + map + (mapInset ? "(inset)" : "");
    }
}
