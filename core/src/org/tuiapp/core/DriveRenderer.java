package org.tuiapp.core;

import java.util.Locale;

/**
 * Draws one whole screen: scene, map pane / corner inset (v0: placeholder), and the cards (speed + gear, set speed
 * + limit sign, state chip, stale label, alert banner). Takeover alerts always win: drawn last, plus a red edge
 * around the whole screen. Pure core code: the same calls draw on Android and into host PNGs.
 */
public final class DriveRenderer {
    private final SceneRenderer scene = new SceneRenderer();

    private final MapRenderer mapR = new MapRenderer();

    public void draw(Gfx g, Layout L, ViewState v, Theme t) { draw(g, L, v, t, null); }

    public void draw(Gfx g, Layout L, ViewState v, Theme t, MapData map) {
        draw(g, L, v, t, map, v.demo && map != null ? MapChooser.Mode.BUILTIN_DEMO : (v.hasFix() ? MapChooser.Mode.BUILTIN_ROUTE : MapChooser.Mode.NO_FIX), null);
    }

    /** mapMode from MapChooser; engine modes leave the map pane transparent for the native map view behind */
    public void draw(Gfx g, Layout L, ViewState v, Theme t, MapData map, MapChooser.Mode mapMode, String attribution) {
        g.fillRect(0, 0, L.w, L.h, t.bg);
        if (!v.hasData && !v.demo && v.link != LinkState.LIVE) { drawConnect(g, L, v, t); return; }
        scene.draw(g, L.scene, v, t);
        mapR.draw(g, L, L.map, L.mapInset, mapMode, map, v, t, attribution);
        drawSpeed(g, L, v, t);
        drawSetLimit(g, L, v, t);
        drawChip(g, L, v, t);
        drawStale(g, L, v, t);
        drawAddonCards(g, L, v, t);
        drawBar(g, L, v, t);
        drawAlert(g, L, v, t);
    }

    // ------------------------------------------------------------------ connection screen (no data from the comma yet)
    private void drawConnect(Gfx g, Layout L, ViewState v, Theme t) {
        boolean searching = v.link == LinkState.SEARCHING;
        String title = v.link == LinkState.NOT_SET ? "Set the comma address" : searching ? "Looking for the comma" : "No comma found";
        float cw = Math.min(L.w - L.pxf(32), L.pxf(560)), x = (L.w - cw) / 2;
        String[] help = {
                "1. Turn on the car and wait for the comma to start.",
                "2. Join this screen to the comma's Wi-Fi hotspot.",
                "3. On the comma, the HUD web server must be on (StarPilot Galaxy).",
                "4. Long-press this screen, open Settings, enter the comma address (host:port).",
                "No address set? The app shows a synthetic demo drive instead."};
        float lh = L.pxf(24), ch = L.pxf(124) + help.length * lh, y = Math.max(L.pxf(16), (L.h - ch) / 2);
        g.fillRoundRect(x, y, cw, ch, L.pxf(20), t.card);
        g.strokeRoundRect(x, y, cw, ch, L.pxf(20), Math.max(1, L.pxf(1)), t.cardStroke);
        // icon: a rounded screen with a broken link dot
        float ic = L.pxf(22), icx = x + L.pxf(36), icy = y + L.pxf(44);
        g.fillCircle(icx, icy, ic, searching ? Theme.alpha(t.accent, 0.18f) : Theme.alpha(t.caution, 0.25f));
        g.fillCircle(icx, icy, ic * 0.38f, searching ? t.accent : t.caution);
        g.text(title, x + L.pxf(72), y + L.pxf(52), L.pxf(24), Gfx.W_SEMIBOLD, Gfx.ALIGN_LEFT, t.text);
        String det = v.linkDetail == null ? "" : v.linkDetail;
        if (det.length() > 64) det = det.substring(0, 61) + "...";
        g.text(det.isEmpty() ? " " : "Trying " + det, x + L.pxf(72), y + L.pxf(76), L.pxf(13), Gfx.W_REGULAR, Gfx.ALIGN_LEFT, t.text3);
        float ty = y + L.pxf(118);
        for (int i = 0; i < help.length; i++) {
            float fs = fit(g, help[i], L.pxf(15), cw - L.pxf(48), Gfx.W_REGULAR);
            g.text(help[i], x + L.pxf(24), ty + i * lh, fs, Gfx.W_REGULAR, Gfx.ALIGN_LEFT, i == help.length - 1 ? t.text3 : t.text2);
        }
    }

    // ------------------------------------------------------------------ add-on cards (empty in the default build)
    private void drawAddonCards(Gfx g, Layout L, ViewState v, Theme t) {
        if (v.addonCards == null || v.addonCards.isEmpty()) return;
        AddonCard c = v.addonCards.get(0);   // one slot in v0.2
        Layout.R r = L.extra;
        float w = Math.min(r.w, g.textWidth(c.text, L.pxf(13), Gfx.W_SEMIBOLD) + L.pxf(24));
        float x = L.cardsLeft ? r.x : r.r() - w;
        int fill = c.kind == AddonCard.WARN ? Theme.alpha(t.caution, 0.95f) : c.kind == AddonCard.ACCENT ? t.accent : t.card;
        int fg = c.kind == AddonCard.WARN ? 0xFF1A1A1A : c.kind == AddonCard.ACCENT ? 0xFFFFFFFF : t.text2;
        g.fillRoundRect(x, r.y, w, r.h, r.h / 2f, fill);
        if (fill == t.card) g.strokeRoundRect(x, r.y, w, r.h, r.h / 2f, Math.max(1, L.pxf(1)), t.cardStroke);
        g.text(c.text, x + L.pxf(12), r.cy() + L.pxf(5), fit(g, c.text, L.pxf(13), r.w - L.pxf(24), Gfx.W_SEMIBOLD), Gfx.W_SEMIBOLD, Gfx.ALIGN_LEFT, fg);
    }

    // ------------------------------------------------------------------ portrait bottom bar (L3)
    private void drawBar(Gfx g, Layout L, ViewState v, Theme t) {
        Layout.R b = L.bar;
        if (b.h <= 0) return;
        g.fillRect(b.x, b.y, b.w, b.h, t.dark ? 0xFF121418 : 0xFFFFFFFF);
        g.line(b.x, b.y, b.r(), b.y, Math.max(1, L.pxf(1)), t.cardStroke);
        boolean live = v.link == LinkState.LIVE || v.demo;
        g.fillCircle(b.x + L.pxf(22), b.cy(), L.pxf(5), v.grey ? t.caution : live ? t.ok : t.text3);
        String l = v.demo ? "Replay: synthetic demo drive" : v.link.label;
        g.text(l, b.x + L.pxf(36), b.cy() + L.pxf(5), L.pxf(14), Gfx.W_SEMIBOLD, Gfx.ALIGN_LEFT, t.text2);
        g.text("Hold screen for settings", b.r() - L.pxf(18), b.cy() + L.pxf(5), L.pxf(13), Gfx.W_REGULAR, Gfx.ALIGN_RIGHT, t.text3);
    }

    // ------------------------------------------------------------------ cards
    private void card(Gfx g, Layout L, Layout.R r, Theme t) {
        float rad = L.pxf(14);
        g.fillRoundRect(r.x, r.y, r.w, r.h, rad, t.card);
        g.strokeRoundRect(r.x, r.y, r.w, r.h, rad, Math.max(1, L.pxf(1)), t.cardStroke);
    }

    private void drawSpeed(Gfx g, Layout L, ViewState v, Theme t) {
        Layout.R r = L.speed;
        card(g, L, r, t);
        int col = v.grey ? t.text3 : t.text;
        String sp = Double.isNaN(v.speedMps) ? "--" : String.valueOf(Math.round(Math.max(0, v.toDisplay(v.speedMps))));
        g.text(sp, r.x + L.pxf(14), r.y + L.pxf(66), L.pxf(60), Gfx.W_LIGHT, Gfx.ALIGN_LEFT, col);
        g.text(v.unit(), r.x + L.pxf(16), r.b() - L.pxf(12), L.pxf(14), Gfx.W_SEMIBOLD, Gfx.ALIGN_LEFT, t.text2);
        // gear: P R N D with the current one highlighted
        String gears = "PRND"; float gx = r.r() - L.pxf(16), gy = r.y + L.pxf(24), step = L.pxf(17);
        for (int k = 0; k < 4; k++) {
            String s = String.valueOf(gears.charAt(k));
            boolean on = s.equals(v.gear) || (k == 3 && ("S".equals(v.gear) || "L".equals(v.gear)));
            String label = k == 3 && on && v.gear != null ? v.gear : s;
            g.text(label, gx, gy + k * step, L.pxf(13), on ? Gfx.W_BOLD : Gfx.W_REGULAR, Gfx.ALIGN_RIGHT, on ? t.text : t.text3);
        }
    }

    private void drawSetLimit(Gfx g, Layout L, ViewState v, Theme t) {
        Layout.R r = L.setLimit;
        card(g, L, r, t);
        g.text("SET", r.cx(), r.y + L.pxf(18), L.pxf(11), Gfx.W_SEMIBOLD, Gfx.ALIGN_CENTER, t.text3);
        String set = Double.isNaN(v.setSpeedMps) ? "--" : String.valueOf(Math.round(v.toDisplay(v.setSpeedMps)));
        g.text(set, r.cx(), r.y + L.pxf(44), L.pxf(24), Gfx.W_SEMIBOLD, Gfx.ALIGN_CENTER, v.engaged ? t.accent : t.text2);
        if (Double.isNaN(v.limitMps)) return;
        String lim = String.valueOf(Math.round(v.toDisplay(v.limitMps)));
        float cx = r.cx(), top = r.y + L.pxf(52), sz = L.pxf(42);
        if (v.metric()) {   // round sign with a red ring
            float rad = sz / 2;
            g.fillCircle(cx, top + rad, rad, 0xFFFFFFFF);
            g.strokeCircle(cx, top + rad, rad - L.pxf(2.5f), L.pxf(5), 0xFFD0312D);
            g.text(lim, cx, top + rad + L.pxf(6), L.pxf(16), Gfx.W_BOLD, Gfx.ALIGN_CENTER, 0xFF111111);
        } else {            // rectangular sign
            float w = L.pxf(34), x = cx - w / 2;
            g.fillRoundRect(x, top, w, sz, L.pxf(4), 0xFFFFFFFF);
            g.strokeRoundRect(x + L.pxf(2), top + L.pxf(2), w - L.pxf(4), sz - L.pxf(4), L.pxf(3), L.pxf(1.5f), 0xFF111111);
            g.text("LIMIT", cx, top + L.pxf(13), L.pxf(7.5f), Gfx.W_BOLD, Gfx.ALIGN_CENTER, 0xFF111111);
            g.text(lim, cx, top + L.pxf(34), L.pxf(17), Gfx.W_BOLD, Gfx.ALIGN_CENTER, 0xFF111111);
        }
    }

    private void drawChip(Gfx g, Layout L, ViewState v, Theme t) {
        Layout.R r = L.chip;
        String text = v.chip();
        if (v.laneChangeState != null && !"off".equals(v.laneChangeState) && v.driving && !v.grey) {
            text = "Lane change" + ("left".equals(v.laneChangeDir) ? " left" : "right".equals(v.laneChangeDir) ? " right" : "");
        }
        int fill, fg;
        if ("Engaged".equals(text) || text.startsWith("Lane change")) { fill = t.accent; fg = 0xFFFFFFFF; }
        else if (v.link != LinkState.LIVE && !v.demo) { fill = Theme.alpha(t.caution, 0.95f); fg = 0xFF1A1A1A; }
        else { fill = t.card; fg = t.text; }
        if (v.grey) { fill = Theme.grey(fill, 1f); fg = fill == Theme.grey(t.card, 1f) ? t.text2 : Theme.mix(fill, 0xFFFFFFFF, 0.8f); }
        float h = r.h, rad = h / 2;
        float tw = g.textWidth(text, L.pxf(16), Gfx.W_SEMIBOLD) + L.pxf(40);
        float w = Math.min(r.w, Math.max(L.pxf(150), tw));
        float x = L.cardsLeft ? r.x : r.r() - w;
        g.fillRoundRect(x, r.y, w, h, rad, fill);
        if (fill == t.card) g.strokeRoundRect(x, r.y, w, h, rad, Math.max(1, L.pxf(1)), t.cardStroke);
        float dot = L.pxf(5);
        int dotCol = "Ready".equals(text) ? t.ok : ("Engaged".equals(text) ? 0xFFFFFFFF : t.text3);
        g.fillCircle(x + L.pxf(16), r.cy(), dot, dotCol);
        g.text(text, x + L.pxf(28), r.cy() + L.pxf(6), L.pxf(16), Gfx.W_SEMIBOLD, Gfx.ALIGN_LEFT, fg);
    }

    private void drawStale(Gfx g, Layout L, ViewState v, Theme t) {
        Layout.R r = L.staleLabel;
        String s = null; int fill = 0;
        if (v.grey) {
            s = Double.isNaN(v.dataAgeS) ? "comma data old" : String.format(Locale.US, "comma data %.0f s old", Math.max(1, v.dataAgeS));
            fill = Theme.alpha(t.caution, 0.95f);
        } else if (v.demo) { s = "Replay (synthetic data)"; fill = t.card; }
        if (s == null) return;
        float w = Math.min(r.w, g.textWidth(s, L.pxf(13), Gfx.W_SEMIBOLD) + L.pxf(24));
        float x = L.cardsLeft ? r.x : r.r() - w;
        g.fillRoundRect(x, r.y, w, r.h, r.h / 2f, fill);
        g.text(s, x + L.pxf(12), r.cy() + L.pxf(5), L.pxf(13), Gfx.W_SEMIBOLD, Gfx.ALIGN_LEFT, v.grey ? 0xFF1A1A1A : t.text2);
    }

    private void drawAlert(Gfx g, Layout L, ViewState v, Theme t) {
        boolean has = v.alert1 != null && !v.alert1.isEmpty() || v.alert2 != null && !v.alert2.isEmpty();
        if (!has) return;
        boolean crit = "critical".equals(v.alertStatus), prompt = "userPrompt".equals(v.alertStatus);
        // takeover: pulsing red edge on every layout (1 Hz, never fully off), never hidden by a card
        float pulse = 0.65f + 0.35f * (float) Math.abs(Math.cos(Math.PI * (v.nowMs % 1000) / 1000.0));
        if (crit) { float e = L.pxf(7); g.strokeRoundRect(e / 2, e / 2, L.w - e, L.h - e, L.pxf(10), e, Theme.alpha(t.alert, pulse)); }
        else if (prompt) { float e = L.pxf(4); g.strokeRoundRect(e / 2, e / 2, L.w - e, L.h - e, L.pxf(10), e, t.caution); }
        Layout.R r = L.alert;
        int fill = crit ? t.alert : prompt ? t.caution : t.card;
        int fg = crit ? 0xFFFFFFFF : prompt ? 0xFF1A1A1A : t.text;
        g.fillRoundRect(r.x + L.pxf(2), r.y + L.pxf(4), r.w, r.h, L.pxf(16), Theme.alpha(0xFF000000, 0.18f));   // shadow
        g.fillRoundRect(r.x, r.y, r.w, r.h, L.pxf(16), fill);
        if (fill == t.card) g.strokeRoundRect(r.x, r.y, r.w, r.h, L.pxf(16), Math.max(1, L.pxf(1)), t.cardStroke);
        // icon: warning triangle (critical / prompt) or info dot
        float ix = r.x + L.pxf(36), iy = r.cy(), is = L.pxf(16);
        if (crit || prompt) {
            float[] tx = {ix, ix + is, ix - is}, ty = {iy - is, iy + is * 0.8f, iy + is * 0.8f};
            g.fillPoly(tx, ty, 3, fg);
            g.text("!", ix, iy + is * 0.62f, is * 1.2f, Gfx.W_BOLD, Gfx.ALIGN_CENTER, fill);
        } else g.fillCircle(ix, iy, L.pxf(6), t.accent);
        boolean two = v.alert2 != null && !v.alert2.isEmpty() && v.alert1 != null && !v.alert1.isEmpty();
        String a1 = v.alert1 == null || v.alert1.isEmpty() ? v.alert2 : v.alert1;
        float tx0 = r.x + L.pxf(64), tw = r.r() - L.pxf(16) - tx0, tcx = tx0 + tw / 2;
        float size1 = fit(g, a1, L.pxf(two ? 22 : 24), tw, Gfx.W_SEMIBOLD);
        if (two) {
            g.text(a1, tcx, r.y + L.pxf(32), size1, Gfx.W_SEMIBOLD, Gfx.ALIGN_CENTER, fg);
            g.text(v.alert2, tcx, r.y + L.pxf(56), fit(g, v.alert2, L.pxf(15), tw, Gfx.W_REGULAR), Gfx.W_REGULAR, Gfx.ALIGN_CENTER, fg);
        } else {
            g.text(a1, tcx, r.cy() + size1 * 0.35f, size1, Gfx.W_SEMIBOLD, Gfx.ALIGN_CENTER, fg);
        }
    }

    /** shrink text to fit maxW (down to 60 %) */
    private static float fit(Gfx g, String s, float size, float maxW, int weight) {
        float w = g.textWidth(s, size, weight);
        return w <= maxW ? size : Math.max(size * 0.6f, size * maxW / w);
    }
}
