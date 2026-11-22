package org.tuiapp.host;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;
import java.util.Deque;

import org.tuiapp.core.Gfx;

/** Gfx on java.awt (host screenshots and tests). */
public final class AwtGfx implements Gfx {
    private final Graphics2D g;
    private final Deque<Shape> clips = new ArrayDeque<Shape>();

    public AwtGfx(Graphics2D g) {
        this.g = g;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static Color col(int argb) { return new Color(argb, true); }
    private Font font(float size, int weight) { return new Font(Font.SANS_SERIF, weight >= W_SEMIBOLD ? Font.BOLD : Font.PLAIN, 1).deriveFont(size); }

    @Override public void save() { clips.push(g.getClip() == null ? new Rectangle2D.Float(-1e6f, -1e6f, 2e6f, 2e6f) : g.getClip()); }
    @Override public void restore() { if (!clips.isEmpty()) g.setClip(clips.pop()); }
    @Override public void clipRect(float x, float y, float w, float h) { g.clip(new Rectangle2D.Float(x, y, w, h)); }
    @Override public void clipRoundRect(float x, float y, float w, float h, float r) { g.clip(new RoundRectangle2D.Float(x, y, w, h, 2 * r, 2 * r)); }

    @Override public void fillRect(float x, float y, float w, float h, int c) { g.setPaint(col(c)); g.fill(new Rectangle2D.Float(x, y, w, h)); }
    @Override public void clear(float x, float y, float w, float h) {
        java.awt.Composite c = g.getComposite(); g.setComposite(java.awt.AlphaComposite.Clear);
        g.fill(new Rectangle2D.Float(x, y, w, h)); g.setComposite(c);
    }
    @Override public void fillRectV(float x, float y, float w, float h, int top, int bottom) {
        g.setPaint(new GradientPaint(0, y, col(top), 0, y + Math.max(1, h), col(bottom))); g.fill(new Rectangle2D.Float(x, y, w, h));
    }
    @Override public void fillRoundRect(float x, float y, float w, float h, float r, int c) { g.setPaint(col(c)); g.fill(new RoundRectangle2D.Float(x, y, w, h, 2 * r, 2 * r)); }
    @Override public void strokeRoundRect(float x, float y, float w, float h, float r, float s, int c) {
        g.setPaint(col(c)); g.setStroke(new BasicStroke(s)); g.draw(new RoundRectangle2D.Float(x, y, w, h, 2 * r, 2 * r));
    }
    @Override public void fillCircle(float cx, float cy, float r, int c) { g.setPaint(col(c)); g.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r)); }
    @Override public void strokeCircle(float cx, float cy, float r, float s, int c) {
        g.setPaint(col(c)); g.setStroke(new BasicStroke(s)); g.draw(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
    }
    @Override public void fillPoly(float[] xs, float[] ys, int n, int c) {
        GeneralPath p = new GeneralPath(); p.moveTo(xs[0], ys[0]); for (int i = 1; i < n; i++) p.lineTo(xs[i], ys[i]); p.closePath();
        g.setPaint(col(c)); g.fill(p);
    }
    @Override public void strokePolyline(float[] xs, float[] ys, int n, float s, int c) {
        GeneralPath p = new GeneralPath(); p.moveTo(xs[0], ys[0]); for (int i = 1; i < n; i++) p.lineTo(xs[i], ys[i]);
        g.setPaint(col(c)); g.setStroke(new BasicStroke(s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(p);
    }
    @Override public void line(float x0, float y0, float x1, float y1, float s, int c) {
        g.setPaint(col(c)); g.setStroke(new BasicStroke(s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(new Line2D.Float(x0, y0, x1, y1));
    }
    @Override public void text(String s, float x, float y, float size, int weight, int align, int c) {
        if (s == null || s.isEmpty()) return;
        Font f = font(size, weight); g.setFont(f); g.setPaint(col(c));
        float w = (float) f.getStringBounds(s, g.getFontRenderContext()).getWidth();
        float dx = align == ALIGN_CENTER ? -w / 2 : (align == ALIGN_RIGHT ? -w : 0);
        g.drawString(s, x + dx, y);
    }
    @Override public float textWidth(String s, float size, int weight) {
        if (s == null || s.isEmpty()) return 0;
        return (float) font(size, weight).getStringBounds(s, g.getFontRenderContext()).getWidth();
    }
}
