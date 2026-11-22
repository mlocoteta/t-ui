package org.tuiapp.drive;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import org.tuiapp.core.Gfx;

/** Gfx on android.graphics.Canvas (thin device layer; all drawing decisions live in core). */
final class AndroidGfx implements Gfx {
    private Canvas c;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();
    private final Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL), regular = Typeface.create("sans-serif", Typeface.NORMAL),
            medium = Typeface.create("sans-serif-medium", Typeface.NORMAL), bold = Typeface.create("sans-serif", Typeface.BOLD);

    AndroidGfx() {
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeCap(Paint.Cap.ROUND); stroke.setStrokeJoin(Paint.Join.ROUND);
    }
    AndroidGfx on(Canvas canvas) { c = canvas; return this; }

    private Typeface face(int w) { return w >= W_BOLD ? bold : w >= W_SEMIBOLD ? medium : w <= W_LIGHT ? light : regular; }

    @Override public void save() { c.save(); }
    @Override public void restore() { c.restore(); }
    @Override public void clipRect(float x, float y, float w, float h) { c.clipRect(x, y, x + w, y + h); }
    @Override public void clipRoundRect(float x, float y, float w, float h, float r) {
        path.reset(); rf.set(x, y, x + w, y + h); path.addRoundRect(rf, r, r, Path.Direction.CW); c.clipPath(path);
    }
    @Override public void fillRect(float x, float y, float w, float h, int argb) { fill.setShader(null); fill.setColor(argb); c.drawRect(x, y, x + w, y + h, fill); }
    private final Paint clearP = new Paint();
    { clearP.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)); }
    @Override public void clear(float x, float y, float w, float h) { c.drawRect(x, y, x + w, y + h, clearP); }
    @Override public void fillRectV(float x, float y, float w, float h, int top, int bottom) {
        fill.setShader(new LinearGradient(0, y, 0, y + Math.max(1, h), top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(x, y, x + w, y + h, fill); fill.setShader(null);
    }
    @Override public void fillRoundRect(float x, float y, float w, float h, float r, int argb) {
        fill.setShader(null); fill.setColor(argb); rf.set(x, y, x + w, y + h); c.drawRoundRect(rf, r, r, fill);
    }
    @Override public void strokeRoundRect(float x, float y, float w, float h, float r, float s, int argb) {
        stroke.setColor(argb); stroke.setStrokeWidth(s); rf.set(x, y, x + w, y + h); c.drawRoundRect(rf, r, r, stroke);
    }
    @Override public void fillCircle(float cx, float cy, float r, int argb) { fill.setShader(null); fill.setColor(argb); c.drawCircle(cx, cy, r, fill); }
    @Override public void strokeCircle(float cx, float cy, float r, float s, int argb) { stroke.setColor(argb); stroke.setStrokeWidth(s); c.drawCircle(cx, cy, r, stroke); }
    @Override public void fillPoly(float[] xs, float[] ys, int n, int argb) {
        path.reset(); path.moveTo(xs[0], ys[0]); for (int i = 1; i < n; i++) path.lineTo(xs[i], ys[i]); path.close();
        fill.setShader(null); fill.setColor(argb); c.drawPath(path, fill);
    }
    @Override public void strokePolyline(float[] xs, float[] ys, int n, float s, int argb) {
        path.reset(); path.moveTo(xs[0], ys[0]); for (int i = 1; i < n; i++) path.lineTo(xs[i], ys[i]);
        stroke.setColor(argb); stroke.setStrokeWidth(s); c.drawPath(path, stroke);
    }
    @Override public void line(float x0, float y0, float x1, float y1, float s, int argb) {
        stroke.setColor(argb); stroke.setStrokeWidth(s); c.drawLine(x0, y0, x1, y1, stroke);
    }
    @Override public void text(String s, float x, float y, float size, int weight, int align, int argb) {
        if (s == null || s.isEmpty()) return;
        text.setTypeface(face(weight)); text.setTextSize(size); text.setColor(argb);
        text.setTextAlign(align == ALIGN_CENTER ? Paint.Align.CENTER : align == ALIGN_RIGHT ? Paint.Align.RIGHT : Paint.Align.LEFT);
        c.drawText(s, x, y, text);
    }
    @Override public float textWidth(String s, float size, int weight) {
        if (s == null || s.isEmpty()) return 0;
        text.setTypeface(face(weight)); text.setTextSize(size); return text.measureText(s);
    }
}
