package org.tuiapp.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen text screens drawn by the core: the first-run SAFETY notice (one "I understand" button, shown until
 * accepted) and ABOUT (version, safety summary, open-source licences; "Full licence texts" + "Close").
 * Text wraps to the panel width; on small screens the font shrinks so everything fits.
 */
public final class InfoPanel {
    public enum Kind { SAFETY, ABOUT }
    public static final String ACCEPT = "accept", CLOSE = "close", LICENCES = "licences";
    private static final class Zone { final float x, y, w, h; final String id; Zone(float x, float y, float w, float h, String id) { this.x = x; this.y = y; this.w = w; this.h = h; this.id = id; } }
    private final List<Zone> zones = new ArrayList<Zone>();

    public static final String[] SAFETY = {
            Brand.APP_NAME + " is a secondary display of what openpilot reports. It is not a safety system and it does not drive or steer.",
            "Watch the road and your comma's own screen and alerts. What " + Brand.APP_NAME + " shows can be late, missing or wrong.",
            "openpilot is a driver-assistance system: you are responsible for the vehicle at all times.",
            "Do not touch this screen while driving. Settings are locked while the car moves.",
            "Check local laws on screens visible to the driver. Not affiliated with comma.ai, StarPilot or any car maker."};

    public static String[] about(String mapBuild) {
        return new String[]{
                Brand.APP_NAME + " " + Brand.VERSION_NAME + ": " + Brand.TAGLINE + ". Read-only, no account, collects nothing.",
                "Licence: Apache License 2.0. Source: github.com/mlocoteta/t-ui (comma side: t-ui-comma-patch, MIT).",
                mapBuild,
                "Map data (when shown): OpenFreeMap, (c) OpenMapTiles, data (c) OpenStreetMap contributors (ODbL).",
                "Safety: a secondary display only; you are responsible for the vehicle at all times."};
    }

    public String hit(float x, float y) {
        for (Zone z : zones) if (x >= z.x && x <= z.x + z.w && y >= z.y && y <= z.y + z.h) return z.id;
        return null;
    }

    public void draw(Gfx g, Layout L, Theme t, Kind kind, String[] lines) {
        zones.clear();
        g.fillRect(0, 0, L.w, L.h, kind == Kind.SAFETY ? t.bg : Theme.alpha(0xFF000000, 0.5f));
        float m = L.pxf(16), pw = Math.min(L.w - 2 * m, L.pxf(720)), x0 = (L.w - pw) / 2;
        float bh = L.pxf(48), pad = L.pxf(22);
        String title = kind == Kind.SAFETY ? "Before you drive" : "About " + Brand.APP_NAME;
        // font size so the wrapped text fits the available height
        float avail = L.h - 2 * m - L.pxf(64) - bh - L.pxf(36), fs = L.pxf(16);
        List<String> wrapped;
        while (true) {
            wrapped = new ArrayList<String>();
            for (String l : lines) { wrapped.addAll(wrap(g, l, fs, pw - 2 * pad)); wrapped.add(""); }
            if (wrapped.size() * fs * 1.35f <= avail || fs <= L.pxf(10)) break;
            fs *= 0.92f;
        }
        float ph = Math.min(L.h - 2 * m, L.pxf(64) + wrapped.size() * fs * 1.35f + bh + L.pxf(36)), y0 = (L.h - ph) / 2;
        g.fillRoundRect(x0, y0, pw, ph, L.pxf(18), t.dark ? 0xFF1A1D22 : 0xFFFFFFFF);
        if (kind == Kind.SAFETY) g.fillRoundRect(x0, y0, L.pxf(6), ph, L.pxf(3), t.caution);
        g.text(title, x0 + pad, y0 + L.pxf(42), L.pxf(24), Gfx.W_SEMIBOLD, Gfx.ALIGN_LEFT, t.text);
        float y = y0 + L.pxf(64) + fs;
        for (String l : wrapped) { if (!l.isEmpty()) g.text(l, x0 + pad, y, fs, Gfx.W_REGULAR, Gfx.ALIGN_LEFT, t.text2); y += fs * 1.35f; }
        float by = y0 + ph - bh - L.pxf(18);
        if (kind == Kind.SAFETY) {
            button(g, L, t, x0 + pw - pad - L.pxf(200), by, L.pxf(200), bh, "I understand", true, ACCEPT);
        } else {
            button(g, L, t, x0 + pw - pad - L.pxf(130), by, L.pxf(130), bh, "Close", true, CLOSE);
            button(g, L, t, x0 + pad, by, L.pxf(220), bh, "Full licence texts", false, LICENCES);
        }
    }

    private void button(Gfx g, Layout L, Theme t, float x, float y, float w, float h, String s, boolean primary, String id) {
        g.fillRoundRect(x, y, w, h, h / 2, primary ? t.accent : (t.dark ? 0xFF2E333A : 0xFFE6E9ED));
        g.text(s, x + w / 2, y + h / 2 + L.pxf(5.5f), L.pxf(16), Gfx.W_SEMIBOLD, Gfx.ALIGN_CENTER, primary ? 0xFFFFFFFF : t.text2);
        zones.add(new Zone(x, y, w, h, id));
    }

    static List<String> wrap(Gfx g, String s, float fs, float maxW) {
        List<String> out = new ArrayList<String>(); StringBuilder cur = new StringBuilder();
        for (String w : s.split(" ")) {
            String cand = cur.length() == 0 ? w : cur + " " + w;
            if (g.textWidth(cand, fs, Gfx.W_REGULAR) > maxW && cur.length() > 0) { out.add(cur.toString()); cur = new StringBuilder(w); }
            else { cur = new StringBuilder(cand); }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}
