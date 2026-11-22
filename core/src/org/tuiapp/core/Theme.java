package org.tuiapp.core;

/**
 * Day / night palettes. Our own neutral palette for this project (not matched to any car maker's UI).
 * Scene-only colours (ground, fog, car) live here too.
 */
public final class Theme {
    public final boolean dark;
    public final int bg, fogTop, fog, ground, road, mark, edge, text, text2, text3, accent, alert, caution, ok,
            car, carTop, carShadow, ego, egoTop, card, cardStroke, mapBg, mapGrid, mapRoad;

    private Theme(boolean dark, int... c) {
        this.dark = dark; int i = 0;
        bg = c[i++]; fogTop = c[i++]; fog = c[i++]; ground = c[i++]; road = c[i++]; mark = c[i++]; edge = c[i++];
        text = c[i++]; text2 = c[i++]; text3 = c[i++]; accent = c[i++]; alert = c[i++]; caution = c[i++]; ok = c[i++];
        car = c[i++]; carTop = c[i++]; carShadow = c[i++]; ego = c[i++]; egoTop = c[i++]; card = c[i++]; cardStroke = c[i++];
        mapBg = c[i++]; mapGrid = c[i++]; mapRoad = c[i];
    }

    public static final Theme DAY = new Theme(false,
            0xFFF3F4F6, 0xFFF6F7F9, 0xFFEDEFF2, 0xFFD3D7DD, 0xFFE2E5EA, 0xFF9AA1AB, 0xFFB9BFC8,
            0xFF15181D, 0xFF5B626C, 0xFF8E959F, 0xFF2F74D0, 0xFFD43D3D, 0xFFE09B2A, 0xFF2E9E62,
            0xFFB7BCC4, 0xFF9AA0A9, 0x33000000, 0xFF2B2F36, 0xFF474C55, 0xE6FFFFFF, 0x14000000,
            0xFFE6E9EE, 0xFFD9DDE3, 0xFFFFFFFF);

    public static final Theme NIGHT = new Theme(true,
            0xFF0C0E11, 0xFF14171B, 0xFF181B20, 0xFF22262C, 0xFF2A2E35, 0xFF6C737D, 0xFF4A5059,
            0xFFF1F3F5, 0xFFA7AEB8, 0xFF6E757F, 0xFF4C8DE6, 0xFFE5534B, 0xFFE9A945, 0xFF43B67A,
            0xFF5B616B, 0xFF747B86, 0x55000000, 0xFFD5D9DF, 0xFFF2F4F7, 0xD91B1E23, 0x1FFFFFFF,
            0xFF16191D, 0xFF1F2328, 0xFF3A4048);

    /** colour with alpha multiplied by a (0..1) */
    public static int alpha(int argb, float a) {
        int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
        return (al << 24) | (argb & 0xFFFFFF);
    }
    /** linear mix of two colours, t = 0 -> a */
    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = 0;
        for (int sh = 0; sh <= 24; sh += 8) {
            int ca = (a >>> sh) & 0xFF, cb = (b >>> sh) & 0xFF;
            r |= (Math.round(ca + (cb - ca) * t) & 0xFF) << sh;
        }
        return r;
    }
    /** desaturated, faded version for "comma data old" */
    public static int grey(int argb, float amount) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        int l = (r * 30 + g * 59 + b * 11) / 100;
        return mix(argb, (argb & 0xFF000000) | (l << 16) | (l << 8) | l, amount);
    }
}
