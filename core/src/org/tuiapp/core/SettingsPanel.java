package org.tuiapp.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings screen, drawn by the core (same pixels on the device and in host screenshots) and hit-tested here.
 * Rows: comma address, theme, units, layout, keep screen on, right-hand drive, then one section per add-on.
 * Options are chips: one tap selects. The app handles a Hit: core rows via apply(), text rows via a text dialog,
 * add-on rows via Addon.select(). Meant for use while parked (the app does not block it; v0 has no speed gate).
 */
public final class SettingsPanel {
    public static final String CORE = "core", DONE = "done";

    /** what a tap hit: section CORE or an add-on id; text = free-text row; option = chip index */
    public static final class Hit {
        public final String section, key; public final int option; public final boolean text;
        Hit(String section, String key, int option, boolean text) { this.section = section; this.key = key; this.option = option; this.text = text; }
        @Override public String toString() { return section + "/" + key + (text ? " (text)" : " #" + option); }
    }
    private static final class Zone { final float x, y, w, h; final Hit hit; Zone(float x, float y, float w, float h, Hit hit) { this.x = x; this.y = y; this.w = w; this.h = h; this.hit = hit; } }
    private final List<Zone> zones = new ArrayList<Zone>();

    static final String[] THEMES = {"Auto", "Light", "Dark"}, UNITS = {"Auto", "mph", "km/h"}, LAYOUTS = {"Auto", "Full scene", "Split"}, OFF_ON = {"Off", "On"};

    public static List<SettingRow> coreRows(Settings s) {
        List<SettingRow> r = new ArrayList<SettingRow>();
        r.add(SettingRow.text(Settings.K_ADDR, "Comma address", s.commaAddress.isEmpty() ? "not set: demo drive" : s.commaAddress)
                .note("host:port of the comma, e.g. its hotspot address", false));
        r.add(new SettingRow(Settings.K_THEME, "Theme", THEMES, s.theme.ordinal()));
        r.add(new SettingRow(Settings.K_UNITS, "Units", UNITS, s.units.ordinal()).note("Auto follows the comma", false));
        r.add(new SettingRow(Settings.K_LAYOUT, "Layout", LAYOUTS, s.layout.ordinal()));
        r.add(new SettingRow(Settings.K_SCREEN_ON, "Keep screen on", OFF_ON, s.keepScreenOn ? 1 : 0));
        r.add(new SettingRow(Settings.K_RHD, "Right-hand drive", OFF_ON, s.rightHandDrive ? 1 : 0).note("scene on the right", false));
        SettingRow car = new SettingRow(Settings.K_EGO_COLOR, "My car colour", egoOptions(), egoSel(s));
        car.swatches = egoSwatches(); car.previewColor = s.egoColor == 0 ? -1 : s.egoColor;
        car.note(s.egoColor == 0 ? "Auto = the theme's colour. Custom: hex #RRGGBB or r,g,b" : "Your car: " + Settings.hex(s.egoColor) + " (other cars stay neutral)", false);
        r.add(car);
        r.add(new SettingRow(Settings.K_MAP_ONLINE, "Online map tiles", OFF_ON, s.mapOnline ? 1 : 0)
                .note(s.mapOnline ? "Uses the network; offline areas are used when there is none" : "Offline areas and the built-in map only", false));
        r.add(new SettingRow(Settings.K_MAP_SOURCE, "Map source", MapSources.LABELS, MapSources.index(s.mapSource)).note(MapSources.attribution(s.mapSource), false));
        if (MapSources.CUSTOM.equals(s.mapSource))
            r.add(SettingRow.text(Settings.K_MAP_URL, "Custom style URL", s.mapCustomUrl.isEmpty() ? "not set" : s.mapCustomUrl).note("MapLibre style JSON; no keys are added by the app", false));
        r.add(new SettingRow(Settings.K_OFFLINE, "Offline maps", new String[]{"Open"}, -1).note("Download areas for driving without a network", false));
        return r;
    }

    static String[] egoOptions() { String[] o = new String[Settings.EGO_PRESETS.length + 1]; o[0] = "Auto"; for (int i = 1; i < Settings.EGO_PRESETS.length; i++) o[i] = ""; o[o.length - 1] = "Custom"; return o; }
    static int[] egoSwatches() { int[] w = new int[Settings.EGO_PRESETS.length + 1]; System.arraycopy(Settings.EGO_PRESETS, 0, w, 0, Settings.EGO_PRESETS.length); return w; }
    static int egoSel(Settings s) { int i = s.egoPresetIndex(); return i < 0 ? Settings.EGO_PRESETS.length : i; }
    /** option index of the "Custom" chip (the app asks for a hex / r,g,b value) */
    public static final int EGO_CUSTOM = Settings.EGO_PRESETS.length;

    /** apply a core-row chip tap; returns true if something changed */
    public static boolean apply(Settings s, String key, int o) {
        if (Settings.K_THEME.equals(key) && o >= 0 && o < 3) { Settings.ThemePref p = Settings.ThemePref.values()[o]; boolean c = p != s.theme; s.theme = p; return c; }
        if (Settings.K_UNITS.equals(key) && o >= 0 && o < 3) { Settings.Units p = Settings.Units.values()[o]; boolean c = p != s.units; s.units = p; return c; }
        if (Settings.K_LAYOUT.equals(key) && o >= 0 && o < 3) { Layout.Mode p = Layout.Mode.values()[o]; boolean c = p != s.layout; s.layout = p; return c; }
        if (Settings.K_SCREEN_ON.equals(key) && (o == 0 || o == 1)) { boolean c = s.keepScreenOn != (o == 1); s.keepScreenOn = o == 1; return c; }
        if (Settings.K_RHD.equals(key) && (o == 0 || o == 1)) { boolean c = s.rightHandDrive != (o == 1); s.rightHandDrive = o == 1; return c; }
        if (Settings.K_EGO_COLOR.equals(key) && o >= 0 && o < Settings.EGO_PRESETS.length) { boolean c = s.egoColor != Settings.EGO_PRESETS[o]; s.egoColor = Settings.EGO_PRESETS[o]; return c; }
        if (Settings.K_MAP_ONLINE.equals(key) && (o == 0 || o == 1)) { boolean c = s.mapOnline != (o == 1); s.mapOnline = o == 1; return c; }
        if (Settings.K_MAP_SOURCE.equals(key) && o >= 0 && o < MapSources.IDS.length) { boolean c = !MapSources.IDS[o].equals(s.mapSource); s.mapSource = MapSources.IDS[o]; return c; }
        return false;
    }

    public Hit hit(float x, float y) {
        for (int i = zones.size() - 1; i >= 0; i--) { Zone z = zones.get(i); if (x >= z.x && x <= z.x + z.w && y >= z.y && y <= z.y + z.h) return z.hit; }
        return null;
    }
    public int zoneCount() { return zones.size(); }

    public void draw(Gfx g, Layout L, Theme t, Settings s, Addons addons, ViewState v, long now) {
        zones.clear();
        g.fillRect(0, 0, L.w, L.h, Theme.alpha(0xFF000000, 0.45f));
        float m = L.pxf(12), x0 = m, y0 = m, pw = L.w - 2 * m, ph = L.h - 2 * m;
        int panel = t.dark ? 0xFF1A1D22 : 0xFFF7F8FA;
        g.fillRoundRect(x0, y0, pw, ph, L.pxf(18), panel);
        g.text("Settings", x0 + L.pxf(20), y0 + L.pxf(36), L.pxf(22), Gfx.W_SEMIBOLD, Gfx.ALIGN_LEFT, t.text);
        g.text(Brand.APP_NAME + " " + Brand.VERSION_NAME + "  ·  read-only display", x0 + L.pxf(20) + g.textWidth("Settings", L.pxf(22), Gfx.W_SEMIBOLD) + L.pxf(14),
                y0 + L.pxf(35), L.pxf(12), Gfx.W_REGULAR, Gfx.ALIGN_LEFT, t.text3);
        float dw = L.pxf(92), dh = L.pxf(40), dx = x0 + pw - dw - L.pxf(14), dy = y0 + L.pxf(10);
        g.fillRoundRect(dx, dy, dw, dh, dh / 2, t.accent);
        g.text("Done", dx + dw / 2, dy + dh / 2 + L.pxf(5.5f), L.pxf(16), Gfx.W_SEMIBOLD, Gfx.ALIGN_CENTER, 0xFFFFFFFF);
        zones.add(new Zone(dx, dy, dw, dh, new Hit(DONE, DONE, 0, false)));

        // sections: core, then each add-on
        List<String> titles = new ArrayList<String>(); List<String> ids = new ArrayList<String>(); List<List<SettingRow>> rows = new ArrayList<List<SettingRow>>();
        titles.add("Display and connection"); ids.add(CORE); rows.add(coreRows(s));
        for (Addon a : addons.all()) { titles.add(a.title()); ids.add(a.id()); rows.add(a.settingRows(v, s, now)); }
        // flow layout: rows fill a column top to bottom; landscape screens >= 700 dp get a second column
        int cols = !L.portrait && L.w / L.u >= 700 ? 2 : 1;
        float top = y0 + L.pxf(62), gap = L.pxf(14), bottomLimit = y0 + ph - gap;
        float colW = (pw - (cols + 1) * gap) / cols;
        float cy = top; int col = 0;
        contentBottom = top;
        bodyTop = top; bodyBottom = bottomLimit;
        float sc = Math.max(0, Math.min(scroll, maxScroll));
        g.save(); g.clipRect(x0, top - L.pxf(4), pw, bottomLimit - top + L.pxf(8));
        top -= sc; cy = top;
        for (int sct = 0; sct < rows.size(); sct++) {
            if (rows.get(sct).isEmpty()) continue;
            if (col < cols - 1 && cy > top && cy + sc + L.pxf(28) + rowHeight(g, L, rows.get(sct).get(0), colW) > bottomLimit) { col++; cy = top; }
            float cx = x0 + gap + col * (colW + gap);
            g.text(titles.get(sct), cx + L.pxf(4), cy + L.pxf(18), L.pxf(13), Gfx.W_BOLD, Gfx.ALIGN_LEFT, sct > 0 ? t.caution : t.text2);
            cy += L.pxf(28);
            for (SettingRow r : rows.get(sct)) {
                if (col < cols - 1 && cy + sc + rowHeight(g, L, r, colW) > bottomLimit) { col++; cy = top; cx = x0 + gap + col * (colW + gap); }
                cy = row(g, L, t, r, ids.get(sct), cx, cy, colW);
                contentBottom = Math.max(contentBottom, cy);
                cy += L.pxf(6);
            }
            cy += L.pxf(8);
        }
        g.restore();
        contentBottom += sc;                       // unscrolled
        maxScroll = Math.max(0, contentBottom - bottomLimit);
        scroll = Math.min(scroll, maxScroll);
        if (maxScroll > 0) {                       // scroll bar
            float trackH = bottomLimit - bodyTop, barH = Math.max(L.pxf(24), trackH * trackH / (trackH + maxScroll));
            float by = bodyTop + (trackH - barH) * (sc / maxScroll);
            g.fillRoundRect(x0 + pw - L.pxf(7), by, L.pxf(4), barH, L.pxf(2), t.text3);
        }
    }

    private float scroll, maxScroll, bodyTop, bodyBottom;
    /** drag: dy > 0 scrolls the content down (shows lower rows) */
    public void scrollBy(float dy) { scroll = Math.max(0, Math.min(maxScroll, scroll + dy)); }
    public void scrollToEnd() { scroll = Float.MAX_VALUE; }
    public float maxScroll() { return maxScroll; }

    /** lowest drawn row edge of the last draw (px); tests check it stays on screen */
    public float contentBottom;

    static boolean isSwatch(SettingRow r, String[] opts, int i) { return r.swatches != null && i < r.swatches.length && r.swatches[i] != 0 && opts[i].isEmpty(); }
    static float chipW(Gfx g, Layout L, SettingRow r, String[] opts, int i, float fs, float chipH) {
        return isSwatch(r, opts, i) ? chipH : g.textWidth(opts[i], fs, Gfx.W_SEMIBOLD) + L.pxf(26);
    }

    /** small rear view of the user's car in the chosen colour (the same art as the drive screen) */
    private void miniCar(Gfx g, final Layout L, Theme t, final float cx, final float top, int color) {
        final float k = L.pxf(250);
        VehicleArt.Cam cam = new VehicleArt.Cam() {
            public float sx(float x, float y) { return cx + k * y / (x + 13); }
            public float sy(float x, float z) { return top + k * (1.9f - z) / (x + 13); }
            public float camX() { return -13; }
        };
        new VehicleArt().draw(g, cam, 0, 0, 0, color, false, 0, t.dark, new VehicleArt.ColorFx() { public int f(int c) { return c; } });
    }

    private static float fitW(Gfx g, String s, float size, float maxW) {
        float w = g.textWidth(s, size, Gfx.W_REGULAR); return w <= maxW ? size : Math.max(size * 0.7f, size * maxW / w);
    }

    private float rowHeight(Gfx g, Layout L, SettingRow r, float w) {
        float titleW = g.textWidth(r.title, L.pxf(15), Gfx.W_SEMIBOLD), chipsW = 0, fs = L.pxf(14), pad = L.pxf(14);
        String[] opts = r.options == null ? new String[]{"Edit"} : r.options;
        for (int i = 0; i < opts.length; i++) chipsW += chipW(g, L, r, opts, i, fs, L.pxf(36)) + L.pxf(6);
        if (r.previewColor != 0) titleW += L.pxf(70);
        float valW = r.options == null ? g.textWidth(r.value, L.pxf(13), Gfx.W_REGULAR) + L.pxf(10) : 0;
        boolean stack = titleW + chipsW + valW + 3 * pad > w;
        int lines = 1;
        if (stack) { float acc = 0, avail = w - 2 * pad; for (int i = 0; i < opts.length; i++) { float cw = chipW(g, L, r, opts, i, fs, L.pxf(36)); if (acc > 0 && acc + cw > avail) { lines++; acc = 0; } acc += cw + L.pxf(6); } }
        return L.pxf(52) + (stack ? L.pxf(44) * lines : 0) + (r.note != null && !r.note.isEmpty() ? L.pxf(16) : 0);
    }

    private float row(Gfx g, Layout L, Theme t, SettingRow r, String section, float x, float y, float w) {
        float chipH = L.pxf(36), pad = L.pxf(14), fs = L.pxf(14);
        float titleW = g.textWidth(r.title, L.pxf(15), Gfx.W_SEMIBOLD);
        float chipsW = 0;
        String[] opts = r.options == null ? new String[]{"Edit"} : r.options;
        for (int i = 0; i < opts.length; i++) chipsW += chipW(g, L, r, opts, i, fs, chipH) + L.pxf(6);
        float valW = r.options == null ? g.textWidth(r.value, L.pxf(13), Gfx.W_REGULAR) + L.pxf(10) : 0;
        boolean stack = titleW + chipsW + valW + 3 * pad > w;
        boolean hasNote = r.note != null && !r.note.isEmpty();
        float h = rowHeight(g, L, r, w);
        int card = t.dark ? 0xFF23272D : 0xFFFFFFFF;
        g.fillRoundRect(x, y, w, h, L.pxf(12), card);
        int tc = r.enabled ? t.text : t.text3;
        g.text(r.title, x + pad, y + L.pxf(31), L.pxf(15), Gfx.W_SEMIBOLD, Gfx.ALIGN_LEFT, tc);
        if (r.previewColor != 0) miniCar(g, L, t, x + pad + g.textWidth(r.title, L.pxf(15), Gfx.W_SEMIBOLD) + L.pxf(36), y + L.pxf(8), r.previewColor == -1 ? t.ego : r.previewColor);
        if (hasNote) g.text(r.note, x + pad, y + h - L.pxf(12), fitW(g, r.note, L.pxf(11.5f), w - 2 * pad), Gfx.W_REGULAR, Gfx.ALIGN_LEFT, r.warning ? t.caution : t.text3);
        float cyy = stack ? y + L.pxf(46) : y + (L.pxf(52) - chipH) / 2;
        boolean ltr = stack && r.options != null;   // stacked chips flow left to right and wrap; otherwise right-aligned
        float cx = ltr ? x + pad : x + w - pad;
        for (int k = 0; k < opts.length; k++) {
            int i = ltr ? k : opts.length - 1 - k;
            float cw = chipW(g, L, r, opts, i, fs, chipH);
            if (ltr) { if (cx + cw > x + w - pad && cx > x + pad) { cyy += chipH + L.pxf(8); cx = x + pad; } }
            else cx -= cw;
            boolean sel = r.options != null && i == r.selected, conf = i == r.confirming;
            int fill = conf ? t.caution : sel ? t.accent : (t.dark ? 0xFF2E333A : 0xFFE9ECF0);
            int fg = conf ? 0xFF1A1A1A : sel ? 0xFFFFFFFF : t.text2;
            if (!r.enabled) { fill = Theme.grey(fill, 1f); fg = t.text3; }
            if (isSwatch(r, opts, i)) {   // colour swatch: filled circle, accent ring when selected
                float rr = chipH / 2;
                if (sel) g.fillCircle(cx + rr, cyy + rr, rr, t.accent);
                g.fillCircle(cx + rr, cyy + rr, rr - L.pxf(sel ? 3.5f : 1f), r.swatches[i]);
                g.strokeCircle(cx + rr, cyy + rr, rr - L.pxf(sel ? 3.5f : 1f), Math.max(1, L.pxf(1)), Theme.alpha(t.text3, 0.6f));
            } else {
                g.fillRoundRect(cx, cyy, cw, chipH, chipH / 2, fill);
                g.text(opts[i], cx + cw / 2, cyy + chipH / 2 + fs * 0.36f, fs, Gfx.W_SEMIBOLD, Gfx.ALIGN_CENTER, fg);
            }
            if (r.enabled && cyy >= bodyTop && cyy + chipH <= bodyBottom) zones.add(new Zone(cx, cyy, cw, chipH, new Hit(section, r.key, r.options == null ? -1 : i, r.options == null)));
            if (ltr) cx += cw + L.pxf(6); else cx -= L.pxf(6);
        }
        if (r.options == null) g.text(r.value, cx - L.pxf(4), cyy + chipH / 2 + L.pxf(4.5f), L.pxf(13), Gfx.W_REGULAR, Gfx.ALIGN_RIGHT, t.text2);
        return y + h;
    }
}
