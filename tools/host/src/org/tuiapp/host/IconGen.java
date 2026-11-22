package org.tuiapp.host;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import org.tuiapp.core.Gfx;

/**
 * Draws the app icon (original artwork: a rounded tile, a road in perspective with two lane lines converging on a
 * horizon and a blue path band; no car shape, no maker or third-party marks) into Android mipmap folders.
 * Usage: IconGen RES_DIR [preview.png]   (writes RES_DIR/mipmap-<dpi>/ic_launcher.png)
 */
public final class IconGen {
    static final String[] DPI = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
    static final int[] SIZE = {48, 72, 96, 144, 192};

    public static BufferedImage draw(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Gfx g = new AwtGfx(img.createGraphics());
        float m = s * 0.04f, w = s - 2 * m, r = s * 0.22f;
        g.save(); g.clipRoundRect(m, m, w, w, r);
        g.fillRectV(m, m, w, w * 0.45f, 0xFF2A3340, 0xFF3B4757);
        g.fillRectV(m, m + w * 0.45f, w, w * 0.55f, 0xFF1A2029, 0xFF11151B);
        float hy = m + w * 0.45f, cx = s / 2f;
        // road surface
        float[] rx = {cx - w * 0.04f, cx + w * 0.04f, cx + w * 0.62f, cx - w * 0.62f}, ry = {hy, hy, m + w, m + w};
        g.fillPoly(rx, ry, 4, 0xFF2C333D);
        // path band
        float[] px = {cx - w * 0.012f, cx + w * 0.012f, cx + w * 0.17f, cx - w * 0.17f}, py = {hy + w * 0.02f, hy + w * 0.02f, m + w, m + w};
        g.fillPoly(px, py, 4, 0x994C8DE6);
        // lane lines
        g.line(cx - w * 0.03f, hy + w * 0.02f, cx - w * 0.36f, m + w, Math.max(1.5f, s * 0.045f), 0xFF4C8DE6);
        g.line(cx + w * 0.03f, hy + w * 0.02f, cx + w * 0.36f, m + w, Math.max(1.5f, s * 0.045f), 0xFF4C8DE6);
        // horizon glow dot
        g.fillCircle(cx, hy - w * 0.10f, s * 0.07f, 0xFFF1F3F5);
        g.restore();
        return img;
    }

    public static void main(String[] a) throws Exception {
        File res = new File(a[0]);
        for (int i = 0; i < DPI.length; i++) {
            File d = new File(res, "mipmap-" + DPI[i]); d.mkdirs();
            ImageIO.write(draw(SIZE[i]), "png", new File(d, "ic_launcher.png"));
        }
        if (a.length > 1) ImageIO.write(draw(512), "png", new File(a[1]));
    }
}
