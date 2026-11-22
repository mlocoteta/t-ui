package org.tuiapp.test;

import static org.tuiapp.test.HostTests.ok;
import static org.tuiapp.test.HostTests.sec;

import java.awt.image.BufferedImage;

import org.tuiapp.core.*;
import org.tuiapp.host.AwtGfx;

/** v0.5 cars: one generic rounded silhouette, neutral colour, only data-driven lights. */
public final class V04 {
    static final VehicleArt.Cam CAM = new VehicleArt.Cam() {   // simple pinhole behind (0,0), 600x400 image
        public float sx(float x, float y) { return 300 + 500 * y / Math.max(1, x + 13); }
        public float sy(float x, float z) { return 120 + 500 * (4 - z) / Math.max(1, x + 13); }
        public float camX() { return -13; }
    };
    static final VehicleArt.ColorFx ID = new VehicleArt.ColorFx() { public int f(int c) { return c; } };

    static BufferedImage car(float yaw, boolean brake, int signal) {
        BufferedImage img = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
        new VehicleArt().draw(new AwtGfx(img.createGraphics()), CAM, 0, 0, yaw, 0xFF9AA0A9, brake, signal, false, ID);
        return img;
    }
    static int count(BufferedImage img, int rgb) { int n = 0; for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) if ((img.getRGB(x, y) & 0xFFFFFF) == rgb) n++; return n; }

    static void run() {
        sec("cars");
        ok(count(car(0, true, 0), 0xFF2A22) > count(car(0, false, 0), 0xFF2A22), "brake lights brighten (data-driven)");
        ok(count(car(0, false, 1), 0xFFA51F) > 0 && count(car(0, false, 0), 0xFFA51F) == 0, "amber signal only when signalling");
        BufferedImage l = car(-0.1f, false, 0), r = car(0.1f, false, 0);
        int lx = 0, rx = 0; for (int x = 0; x < 600; x++) { if ((l.getRGB(x, 235) >>> 24) > 200) lx += x; if ((r.getRGB(x, 235) >>> 24) > 200) rx += x; }
        ok(rx != lx, "yaw changes the silhouette (curve / lane-change lean)");
        // rounded rear: the top corners of the rear face's bounding box stay empty (not a box)
        BufferedImage b = car(0, false, 0);
        float xl = CAM.sx(0, -0.92f), xr = CAM.sx(0, 0.92f), yt = CAM.sy(0, 0.92f);
        ok((b.getRGB((int) xl + 1, (int) yt + 1) & 0xFFFFFF) != (b.getRGB(300, (int) yt + 6) & 0xFFFFFF) || (b.getRGB((int) xr - 1, (int) yt + 1) >>> 24) < 255, "rounded rear corners");
        BufferedImage img = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
        new VehicleArt().draw(new AwtGfx(img.createGraphics()), CAM, 0, 0, 0, 0xFFC03030, true, -1, false, new VehicleArt.ColorFx() { public int f(int c) { return Theme.grey(c, 1f); } });
        ok(count(img, 0xFF2A22) == 0 && count(img, 0xFFA51F) == 0, "greyed (old data): no coloured lights");
        // scene: other cars all the same neutral colour; brake lights only for a car the comma marks as stopped
        ViewState v = new ViewState(); v.driving = true; v.speedMps = 25; v.hasData = true; v.link = LinkState.LIVE;
        v.tracks = new float[][]{{20, 0, -3, 5, 22, 0}, {30, 3.6f, 0, 6, 25, 0}};
        BufferedImage s0 = new BufferedImage(800, 480, BufferedImage.TYPE_INT_ARGB);
        new SceneRenderer().draw(new AwtGfx(s0.createGraphics()), new Layout.R(0, 0, 800, 480), v, Theme.DAY);
        ok(count(s0, 0xFF2A22) == 0, "tracks closing but not marked stopped: no brake lights (no guessing)");
        v.tracks = new float[][]{{20, 0, -3, 5, 0, 1}};
        BufferedImage s1 = new BufferedImage(800, 480, BufferedImage.TYPE_INT_ARGB);
        new SceneRenderer().draw(new AwtGfx(s1.createGraphics()), new Layout.R(0, 0, 800, 480), v, Theme.DAY);
        ok(count(s1, 0xFF2A22) > 0, "track marked stopped by the comma: brake lights");
        ok(count(s0, Theme.DAY.car & 0xFFFFFF) > 0, "other cars use the theme's one neutral car colour");

        sec("taillights + my car colour");
        for (Theme th : new Theme[]{Theme.DAY, Theme.NIGHT}) for (int paint : new int[]{0xFF1B1D21, 0xFFF2F3F5, 0xFF9C1C1C}) {
            BufferedImage a = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB), bl = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
            new VehicleArt().draw(new AwtGfx(a.createGraphics()), CAM, 0, 0, 0, paint, false, 0, th.dark, ID);
            new VehicleArt().draw(new AwtGfx(bl.createGraphics()), CAM, 0, 0, 0, paint, true, 0, th.dark, ID);
            ok(count(a, VehicleArt.TAIL_UNLIT & 0xFFFFFF) > 20 && count(a, VehicleArt.TAIL_LIT & 0xFFFFFF) == 0, (th.dark ? "night" : "day") + " paint " + Settings.hex(paint) + ": unlit lamps = the one dim red");
            ok(count(bl, VehicleArt.TAIL_LIT & 0xFFFFFF) > 20 && count(bl, VehicleArt.TAIL_UNLIT & 0xFFFFFF) == 0, (th.dark ? "night" : "day") + " paint " + Settings.hex(paint) + ": lit lamps = the one bright red");
        }
        ok(Settings.parseColor("#1f3f7a") == 0xFF1F3F7A && Settings.parseColor("1F3F7A") == 0xFF1F3F7A && Settings.parseColor(" 31, 63 ,122 ") == 0xFF1F3F7A, "colour parse: hex / bare hex / r,g,b");
        ok(Settings.parseColor("#12345") == 0 && Settings.parseColor("300,0,0") == 0 && Settings.parseColor("zz") == 0 && Settings.parseColor(null) == 0, "bad colours -> theme default");
        Settings st = new Settings(); st.egoColor = 0xFF9C1C1C;
        Settings rt = Settings.from(st.toMap());
        ok(rt.egoColor == 0xFF9C1C1C && "#9C1C1C".equals(rt.toMap().get(Settings.K_EGO_COLOR)), "my car colour persists");
        ok(Settings.from(new Settings().toMap()).egoColor == 0, "default = theme colour");
        Settings sp = new Settings();
        ok(SettingsPanel.apply(sp, Settings.K_EGO_COLOR, 2) && sp.egoColor == Settings.EGO_PRESETS[2] && sp.egoPresetIndex() == 2, "swatch tap sets the preset");
        ok(!SettingsPanel.apply(sp, Settings.K_EGO_COLOR, SettingsPanel.EGO_CUSTOM), "Custom chip leaves the colour to the dialog");
        ViewState vv = new ViewState(); sp.applyTo(vv); ok(vv.egoColor == sp.egoColor, "applied to the drive screen state");
        // drive screen: ego in the chosen colour, other cars unchanged
        ViewState d = new ViewState(); d.driving = true; d.speedMps = 20; d.hasData = true; d.link = LinkState.LIVE; d.egoColor = 0xFF9C1C1C;
        d.tracks = new float[][]{{25, 0, 0, 5, 20, 0}};
        BufferedImage s2 = new BufferedImage(800, 480, BufferedImage.TYPE_INT_ARGB);
        new SceneRenderer().draw(new AwtGfx(s2.createGraphics()), new Layout.R(0, 0, 800, 480), d, Theme.DAY);
        ok(count(s2, 0x9C1C1C) > 100 && count(s2, Theme.DAY.car & 0xFFFFFF) > 0, "ego painted in my colour; other car stays neutral");
        // settings screen: swatches tappable, Custom reachable, preview car drawn
        Layout L = Layout.compute(1280, 720, 1f);
        BufferedImage ps = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
        SettingsPanel pn = new SettingsPanel();
        pn.draw(new AwtGfx(ps.createGraphics()), L, Theme.DAY, sp, Addons.NONE, new ViewState(), 0);
        boolean sw = false, cu = false;
        for (int y = 0; y < 720; y += 2) for (int x = 0; x < 1280; x += 2) { SettingsPanel.Hit h = pn.hit(x, y); if (h != null && Settings.K_EGO_COLOR.equals(h.key)) { if (h.option == 5) sw = true; if (h.option == SettingsPanel.EGO_CUSTOM) cu = true; } }
        ok(sw && cu, "my car colour: swatches + Custom tappable");
        ok(count(ps, Settings.EGO_PRESETS[2] & 0xFFFFFF) > 50, "settings preview shows the chosen colour");
    }
}
