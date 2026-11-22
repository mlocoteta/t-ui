package org.tuiapp.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Offline maps" screen, drawn and hit-tested by the core (same pixels on the device and in host screenshots).
 * Left (landscape) or top (portrait): the map viewport. On the device the live map engine shows through a transparent
 * hole (pan / pinch in "Move map"; drag a rectangle in "Draw area"); on the host a synthetic preview is drawn.
 * Right / bottom: mode + presets (visible area, 10 / 50 km around the car), detail level, size estimate against the
 * map source's limits, Download, and the list of offline areas with progress, pause / resume, delete, storage used.
 */
public final class OfflinePanel {
    public enum Act { DONE, MODE_MOVE, MODE_DRAW, PRESET_VISIBLE, PRESET_10, PRESET_50, DETAIL, DOWNLOAD, PAUSE, RESUME, DELETE }
    public static final class Hit {
        public final Act act; public final long arg;
        Hit(Act a, long arg) { act = a; this.arg = arg; }
        @Override public String toString() { return act + (arg != 0 ? " " + arg : ""); }
    }
    private static final class Zone { final float x, y, w, h; final Hit hit; Zone(float x, float y, float w, float h, Hit hit) { this.x = x; this.y = y; this.w = w; this.h = h; this.hit = hit; } }
    private final List<Zone> zones = new ArrayList<Zone>();

    public static final int[] DETAIL_Z = {12, 13, 14};
    static final String[] DETAIL_LABEL = {"Overview", "Roads", "Streets"};
    public static final int MIN_Z = 5;

    // state (UI thread)
    public boolean drawMode;
    public int detail = 2;
    public double[] selection;       // {n, e, s, w} or null
    public Geo.Viewport viewport;    // last viewport (set by draw)
    public String sourceId = MapSources.OFM_LIBERTY;
    public long customCapMb = 500;
    public String note = "";

    public int maxZoom() { return DETAIL_Z[Math.max(0, Math.min(2, detail))]; }
    public String refusal() { return MapSources.refuse(sourceId, customCapMb, selection, MIN_Z, maxZoom()); }
    public long estTiles() { return selection == null ? 0 : Geo.tiles(selection, MIN_Z, maxZoom()); }
    public long estBytes() { return selection == null ? 0 : Geo.estimateBytes(selection, MIN_Z, maxZoom()); }

    /** the viewport rectangle for a screen (the app sizes the map view to it) */
    public static float[] viewportRect(Layout L) {
        float top = L.pxf(56), m = L.pxf(10);
        boolean side = !L.portrait && L.w / L.u >= 600;
        if (side) { float vw = L.w * 0.58f; return new float[]{m, top, vw - m, L.h - top - m}; }
        return new float[]{m, top, L.w - 2 * m, (L.h - top) * 0.48f};
    }

    public void selectVisible() { if (viewport != null) { float in = viewport.w * 0.08f; selection = viewport.bounds(viewport.x + in, viewport.y + in, viewport.x + viewport.w - in, viewport.y + viewport.h - in); } }
    public void selectAround(double lat, double lon, double radiusM) { selection = Geo.around(lat, lon, radiusM); }
    /** drag in draw mode, screen px */
    public void selectRect(float x0, float y0, float x1, float y1) { if (viewport != null && Math.abs(x1 - x0) > 8 && Math.abs(y1 - y0) > 8) selection = viewport.bounds(x0, y0, x1, y1); }

    public Hit hit(float x, float y) {
        for (int i = zones.size() - 1; i >= 0; i--) { Zone z = zones.get(i); if (x >= z.x && x <= z.x + z.w && y >= z.y && y <= z.y + z.h) return z.hit; }
        return null;
    }
    public boolean inViewport(float x, float y) { return viewport != null && x >= viewport.x && x <= viewport.x + viewport.w && y >= viewport.y && y <= viewport.y + viewport.h; }

    public static String areaName(double[] b, int maxZ) {
        double hKm = (b[0] - b[2]) * 111.32, wKm = (b[1] - b[3]) * 111.32 * Math.cos(Math.toRadians((b[0] + b[2]) / 2));
        return String.format(Locale.US, "%.0f × %.0f km, detail z%d", Math.max(1, wKm), Math.max(1, hKm), maxZ);
    }

    /**
     * @param vp      current map camera for the viewport rect (from the engine on the device; any center/zoom on host)
     * @param hole    true = leave the viewport transparent (the engine's map view is behind); false = draw preview
     * @param preview synthetic preview map for the host (may be null), drawn in local metres around previewOrigin
     */
    public void draw(Gfx g, Layout L, Theme t, Geo.Viewport vp, boolean hole, MapData preview, double[] previewOrigin,
                     OfflineModel model, boolean hasFix, double fixLat, double fixLon, boolean metric) {
        zones.clear();
        viewport = vp;
        int panel = t.dark ? 0xFF15181C : 0xFFF3F4F6, card = t.dark ? 0xFF23272D : 0xFFFFFFFF;
        g.fillRect(0, 0, L.w, L.h, panel);
        // header
        g.text("Offline maps", L.pxf(18), L.pxf(36), L.pxf(21), Gfx.W_SEMIBOLD, Gfx.ALIGN_LEFT, t.text);
        g.text(MapSources.LABELS[MapSources.index(sourceId)] + " · tiles stay on this device", L.pxf(18) + g.textWidth("Offline maps", L.pxf(21), Gfx.W_SEMIBOLD) + L.pxf(12),
                L.pxf(35), L.pxf(12), Gfx.W_REGULAR, Gfx.ALIGN_LEFT, t.text3);
        float dw = L.pxf(88), dh = L.pxf(38), dx = L.w - dw - L.pxf(12), dy = L.pxf(9);
        g.fillRoundRect(dx, dy, dw, dh, dh / 2, t.accent);
        g.text("Done", dx + dw / 2, dy + dh / 2 + L.pxf(5.5f), L.pxf(16), Gfx.W_SEMIBOLD, Gfx.ALIGN_CENTER, 0xFFFFFFFF);
        zones.add(new Zone(dx, dy, dw, dh, new Hit(Act.DONE, 0)));

        // viewport
        float vx = vp.x, vy = vp.y, vw = vp.w, vh = vp.h, r = L.pxf(14);
        g.save(); g.clipRoundRect(vx, vy, vw, vh, r);
        if (hole) g.clear(vx, vy, vw, vh);
        else drawPreview(g, L, t, vp, preview, previewOrigin);
        if (selection != null) {
            float sx0 = vp.sx(selection[3]), sx1 = vp.sx(selection[1]), sy0 = vp.sy(selection[0]), sy1 = vp.sy(selection[2]);
            int dim = Theme.alpha(0xFF000000, 0.35f);
            g.fillRect(vx, vy, vw, Math.max(0, sy0 - vy), dim);
            g.fillRect(vx, sy1, vw, Math.max(0, vy + vh - sy1), dim);
            g.fillRect(vx, sy0, Math.max(0, sx0 - vx), sy1 - sy0, dim);
            g.fillRect(sx1, sy0, Math.max(0, vx + vw - sx1), sy1 - sy0, dim);
            g.strokeRoundRect(sx0, sy0, sx1 - sx0, sy1 - sy0, L.pxf(4), L.pxf(2.5f), t.accent);
            float hs = L.pxf(6);
            for (float[] c : new float[][]{{sx0, sy0}, {sx1, sy0}, {sx0, sy1}, {sx1, sy1}}) { g.fillCircle(c[0], c[1], hs + L.pxf(2), 0xFFFFFFFF); g.fillCircle(c[0], c[1], hs, t.accent); }
        }
        if (hasFix) {
            float px = vp.sx(fixLon), py = vp.sy(fixLat);
            g.fillCircle(px, py, L.pxf(9), 0xFFFFFFFF); g.fillCircle(px, py, L.pxf(6.5f), t.accent);
        }
        // in-map hint
        String hint = drawMode ? "Drag a rectangle to choose the area" : "Pan and zoom, then tap Draw area or a preset";
        float hw = g.textWidth(hint, L.pxf(12), Gfx.W_SEMIBOLD) + L.pxf(20);
        g.fillRoundRect(vx + (vw - hw) / 2, vy + vh - L.pxf(38), hw, L.pxf(28), L.pxf(14), Theme.alpha(0xFF101215, 0.82f));
        g.text(hint, vx + vw / 2, vy + vh - L.pxf(19.5f), L.pxf(12), Gfx.W_SEMIBOLD, Gfx.ALIGN_CENTER, 0xFFFFFFFF);
        g.restore();
        g.strokeRoundRect(vx, vy, vw, vh, r, Math.max(1, L.pxf(1)), t.cardStroke);

        // controls column (right) or block (below)
        boolean side = vx + vw < L.w * 0.7f;
        float cx = side ? vx + vw + L.pxf(12) : vx, cy = side ? vy : vy + vh + L.pxf(10), cw = side ? L.w - cx - L.pxf(12) : vw;
        float chipH = L.pxf(34);
        cy = chips(g, L, t, cx, cy, cw, chipH, new String[]{"Move map", "Draw area"}, drawMode ? 1 : 0,
                new Hit[]{new Hit(Act.MODE_MOVE, 0), new Hit(Act.MODE_DRAW, 0)}, true);
        // area presets: the visible map, or a box around the car's position (needs the comma's GPS fix)
        g.text("Area", cx + L.pxf(2), cy + L.pxf(26), L.pxf(12), Gfx.W_BOLD, Gfx.ALIGN_LEFT, t.text2);
        cy = chips(g, L, t, cx + L.pxf(54), cy + L.pxf(8), cw - L.pxf(54), chipH, new String[]{"Visible", "10 km", "50 km"}, -1,
                new Hit[]{new Hit(Act.PRESET_VISIBLE, 0), hasFix ? new Hit(Act.PRESET_10, 0) : null, hasFix ? new Hit(Act.PRESET_50, 0) : null}, true);
        g.text("Detail", cx + L.pxf(2), cy + L.pxf(20), L.pxf(12), Gfx.W_BOLD, Gfx.ALIGN_LEFT, t.text2);
        cy = chips(g, L, t, cx + L.pxf(54), cy + L.pxf(2), cw - L.pxf(54), chipH, DETAIL_LABEL, detail,
                new Hit[]{new Hit(Act.DETAIL, 0), new Hit(Act.DETAIL, 1), new Hit(Act.DETAIL, 2)}, true);

        // estimate + download
        String why = refusal();
        float eh = L.pxf(62);
        g.fillRoundRect(cx, cy + L.pxf(8), cw, eh, L.pxf(12), card);
        String est = selection == null ? "No area selected" :
                String.format(Locale.US, "≈ %,d tiles · ≈ %s · zoom %d-%d", estTiles(), Geo.mb(estBytes()), MIN_Z, maxZoom());
        g.text(est, cx + L.pxf(12), cy + L.pxf(32), fit(g, est, L.pxf(15), cw - L.pxf(24), Gfx.W_SEMIBOLD), Gfx.W_SEMIBOLD, Gfx.ALIGN_LEFT, t.text);
        long[] lim = MapSources.limits(sourceId, customCapMb);
        String limTxt = why != null && selection != null ? why : "Limit per area for this source: " + (lim[0] == Long.MAX_VALUE ? "" : String.format(Locale.US, "%,d tiles / ", lim[0])) + Geo.mb(lim[1]) + ". Size is an estimate.";
        g.text(limTxt, cx + L.pxf(12), cy + L.pxf(54), fit(g, limTxt, L.pxf(11.5f), cw - L.pxf(24), Gfx.W_REGULAR), Gfx.W_REGULAR, Gfx.ALIGN_LEFT,
                why != null && selection != null ? t.alert : t.text3);
        cy += eh + L.pxf(16);
        float bh = L.pxf(44);
        boolean can = why == null && model.available;
        g.fillRoundRect(cx, cy, cw, bh, bh / 2, can ? t.accent : (t.dark ? 0xFF2E333A : 0xFFDDE1E6));
        String bt = !model.available ? "Offline download needs the map engine (not in this build)" : "Download this area";
        g.text(bt, cx + cw / 2, cy + bh / 2 + L.pxf(5.5f), fit(g, bt, L.pxf(16), cw - L.pxf(20), Gfx.W_SEMIBOLD), Gfx.W_SEMIBOLD, Gfx.ALIGN_CENTER, can ? 0xFFFFFFFF : t.text3);
        if (can) zones.add(new Zone(cx, cy, cw, bh, new Hit(Act.DOWNLOAD, 0)));
        cy += bh + L.pxf(12);
        if (note != null && !note.isEmpty()) { g.text(note, cx + L.pxf(4), cy + L.pxf(4), fit(g, note, L.pxf(12), cw, Gfx.W_REGULAR), Gfx.W_REGULAR, Gfx.ALIGN_LEFT, t.caution); cy += L.pxf(16); }

        // areas
        List<OfflineModel.Region> regs = model.regions();
        String ah = "Offline areas" + (regs.isEmpty() ? ": none yet" : ""), su = "Storage used " + Geo.mb(model.bytesUsed());
        g.text(ah, cx + L.pxf(2), cy + L.pxf(14), L.pxf(12), Gfx.W_BOLD, Gfx.ALIGN_LEFT, t.text2);
        if (g.textWidth(ah, L.pxf(12), Gfx.W_BOLD) + g.textWidth(su, L.pxf(12), Gfx.W_REGULAR) + L.pxf(16) > cw) cy += L.pxf(16);
        g.text(su, cx + cw, cy + L.pxf(14), L.pxf(12), Gfx.W_REGULAR, Gfx.ALIGN_RIGHT, t.text3);
        cy += L.pxf(22);
        float rh = L.pxf(58), limitY = L.h - L.pxf(10);
        for (int ri = 0; ri < regs.size(); ri++) {
            OfflineModel.Region rg = regs.get(ri);
            if (cy + rh > limitY) { g.text("+ " + (regs.size() - ri) + " more (list scrolling comes later)", cx, cy + L.pxf(12), L.pxf(11), Gfx.W_REGULAR, Gfx.ALIGN_LEFT, t.text3); break; }
            g.fillRoundRect(cx, cy, cw, rh, L.pxf(12), card);
            String st = rg.state == OfflineModel.State.DOWNLOADING ? String.format(Locale.US, "Downloading %d %%", Math.round(rg.progress() * 100))
                    : rg.state == OfflineModel.State.PAUSED ? String.format(Locale.US, "Paused at %d %%", Math.round(rg.progress() * 100))
                    : rg.state == OfflineModel.State.READY ? "Ready offline" : rg.state == OfflineModel.State.ERROR ? "Error: " + rg.error : "Waiting";
            float bw = L.pxf(72);
            float tw = cw - 2 * bw - L.pxf(30);
            g.text(rg.name, cx + L.pxf(12), cy + L.pxf(22), fit(g, rg.name, L.pxf(13.5f), tw, Gfx.W_SEMIBOLD), Gfx.W_SEMIBOLD, Gfx.ALIGN_LEFT, t.text);
            String sub = st + " · " + Geo.mb(rg.bytes);
            g.text(sub, cx + L.pxf(12), cy + L.pxf(40), fit(g, sub, L.pxf(12), tw, Gfx.W_REGULAR), Gfx.W_REGULAR, Gfx.ALIGN_LEFT,
                    rg.state == OfflineModel.State.READY ? t.ok : rg.state == OfflineModel.State.ERROR ? t.alert : t.text2);
            float pw = cw - L.pxf(24);
            g.fillRoundRect(cx + L.pxf(12), cy + rh - L.pxf(10), pw, L.pxf(4), L.pxf(2), t.dark ? 0xFF33383F : 0xFFE3E6EA);
            g.fillRoundRect(cx + L.pxf(12), cy + rh - L.pxf(10), pw * (rg.state == OfflineModel.State.READY ? 1f : rg.progress()), L.pxf(4), L.pxf(2),
                    rg.state == OfflineModel.State.READY ? t.ok : t.accent);
            float by = cy + L.pxf(10), bhh = L.pxf(30), bx = cx + cw - L.pxf(10) - bw;
            chip(g, L, t, bx, by, bw, bhh, "Delete", false, false);
            zones.add(new Zone(bx, by, bw, bhh, new Hit(Act.DELETE, rg.id)));
            if (rg.state != OfflineModel.State.READY) {
                boolean pause = rg.state == OfflineModel.State.DOWNLOADING || rg.state == OfflineModel.State.QUEUED;
                bx -= bw + L.pxf(6);
                chip(g, L, t, bx, by, bw, bhh, pause ? "Pause" : "Resume", !pause, false);
                zones.add(new Zone(bx, by, bw, bhh, new Hit(pause ? Act.PAUSE : Act.RESUME, rg.id)));
            }
            cy += rh + L.pxf(6);
        }
    }

    private float chips(Gfx g, Layout L, Theme t, float x, float y, float w, float h, String[] labels, int sel, Hit[] hits, boolean fitRow) {
        float gap = L.pxf(6), cw = (w - gap * (labels.length - 1)) / labels.length;
        for (int i = 0; i < labels.length; i++) {
            float cx = x + i * (cw + gap);
            chip(g, L, t, cx, y, cw, h, labels[i], i == sel, hits[i] == null);
            if (hits[i] != null) zones.add(new Zone(cx, y, cw, h, hits[i]));
        }
        return y + h + L.pxf(4);
    }

    private void chip(Gfx g, Layout L, Theme t, float x, float y, float w, float h, String s, boolean sel, boolean disabled) {
        int fill = sel ? t.accent : (t.dark ? 0xFF2E333A : 0xFFE6E9ED);
        int fg = sel ? 0xFFFFFFFF : disabled ? Theme.alpha(t.text3, 0.6f) : t.text2;
        g.fillRoundRect(x, y, w, h, h / 2, fill);
        float fs = fit(g, s, L.pxf(13), w - L.pxf(12), Gfx.W_SEMIBOLD);
        g.text(s, x + w / 2, y + h / 2 + fs * 0.36f, fs, Gfx.W_SEMIBOLD, Gfx.ALIGN_CENTER, fg);
    }

    /** host preview: tile grid of the current zoom + the synthetic demo town placed at previewOrigin */
    private void drawPreview(Gfx g, Layout L, Theme t, Geo.Viewport vp, MapData d, double[] origin) {
        g.fillRect(vp.x, vp.y, vp.w, vp.h, t.mapBg);
        int z = (int) Math.floor(vp.zoom);
        double x0 = Geo.tileX(vp.lonAt(vp.x), z), x1 = Geo.tileX(vp.lonAt(vp.x + vp.w), z);
        for (double tx = Math.floor(x0); tx <= x1; tx++) { float sx = vp.sx(tx / (1 << z) * 360 - 180); g.line(sx, vp.y, sx, vp.y + vp.h, 1, t.mapGrid); }
        double y0 = Geo.tileY(vp.latAt(vp.y), z), y1 = Geo.tileY(vp.latAt(vp.y + vp.h), z);
        for (double ty = Math.floor(y0); ty <= y1; ty++) {
            double n = Math.PI - 2 * Math.PI * ty / (1 << z); float sy = vp.sy(Math.toDegrees(Math.atan(Math.sinh(n))));
            g.line(vp.x, sy, vp.x + vp.w, sy, 1, t.mapGrid);
        }
        if (d != null && origin != null) {
            double mPerDegLat = 111320.0, mPerDegLon = 111320.0 * Math.cos(Math.toRadians(origin[0]));
            for (float[] w : d.water) poly(g, vp, w, origin, mPerDegLat, mPerDegLon, t.dark ? 0xFF16283A : 0xFFC9DDF0);
            for (int k = d.roads.size() - 1; k >= 0; k--) {
                int c = d.roadClass.get(k);
                line(g, vp, d.roads.get(k), origin, mPerDegLat, mPerDegLon, L.pxf(c == MapData.MAJOR ? 4 : c == MapData.MINOR ? 3 : 1.5f),
                        c == MapData.MAJOR ? (t.dark ? 0xFF5A616B : 0xFFFFFFFF) : t.mapRoad);
            }
        }
        g.text("Host preview: synthetic map (on the device: live map tiles)", vp.x + L.pxf(10), vp.y + L.pxf(20), L.pxf(11), Gfx.W_REGULAR, Gfx.ALIGN_LEFT, t.text3);
    }
    private static void line(Gfx g, Geo.Viewport vp, float[] p, double[] o, double mLat, double mLon, float w, int col) {
        int n = p.length / 2; float[] xs = new float[n], ys = new float[n];
        for (int i = 0; i < n; i++) { xs[i] = vp.sx(o[1] + p[2 * i] / mLon); ys[i] = vp.sy(o[0] + p[2 * i + 1] / mLat); }
        g.strokePolyline(xs, ys, n, w, col);
    }
    private static void poly(Gfx g, Geo.Viewport vp, float[] p, double[] o, double mLat, double mLon, int col) {
        int n = p.length / 2; float[] xs = new float[n], ys = new float[n];
        for (int i = 0; i < n; i++) { xs[i] = vp.sx(o[1] + p[2 * i] / mLon); ys[i] = vp.sy(o[0] + p[2 * i + 1] / mLat); }
        g.fillPoly(xs, ys, n, col);
    }

    private static float fit(Gfx g, String s, float size, float maxW, int weight) {
        float w = g.textWidth(s, size, weight); return w <= maxW ? size : Math.max(size * 0.6f, size * maxW / w);
    }
}
