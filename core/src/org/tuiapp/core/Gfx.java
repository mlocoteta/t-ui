package org.tuiapp.core;

/**
 * The drawing surface the core renderer needs: a tiny subset of android.graphics.Canvas. Implemented by the app
 * (Android Canvas) and by the host tools (java.awt, for PNG screenshots). Colours are 0xAARRGGBB. Coordinates px.
 */
public interface Gfx {
    int ALIGN_LEFT = 0, ALIGN_CENTER = 1, ALIGN_RIGHT = 2;
    int W_LIGHT = 300, W_REGULAR = 400, W_SEMIBOLD = 600, W_BOLD = 700;

    void save();
    void restore();
    void clipRect(float x, float y, float w, float h);
    void clipRoundRect(float x, float y, float w, float h, float r);

    void fillRect(float x, float y, float w, float h, int argb);
    /** make the area fully transparent (a hole where a native map view behind this one shows); respects the clip */
    void clear(float x, float y, float w, float h);
    /** vertical gradient from top colour to bottom colour */
    void fillRectV(float x, float y, float w, float h, int top, int bottom);
    void fillRoundRect(float x, float y, float w, float h, float r, int argb);
    void strokeRoundRect(float x, float y, float w, float h, float r, float stroke, int argb);
    void fillCircle(float cx, float cy, float r, int argb);
    void strokeCircle(float cx, float cy, float r, float stroke, int argb);
    /** closed polygon of n points */
    void fillPoly(float[] xs, float[] ys, int n, int argb);
    /** open polyline of n points, round caps/joins */
    void strokePolyline(float[] xs, float[] ys, int n, float stroke, int argb);
    void line(float x0, float y0, float x1, float y1, float stroke, int argb);

    /** text with its baseline at y */
    void text(String s, float x, float y, float sizePx, int weight, int align, int argb);
    float textWidth(String s, float sizePx, int weight);
}
