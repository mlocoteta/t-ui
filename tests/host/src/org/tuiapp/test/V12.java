package org.tuiapp.test;

import static org.tuiapp.test.HostTests.ok;
import static org.tuiapp.test.HostTests.sec;

import java.awt.image.BufferedImage;

import org.tuiapp.core.*;
import org.tuiapp.host.AwtGfx;

/** v0.1.2: first-run safety notice, about screen, settings lock while driving, three-zone ultrawide render. */
public final class V12 {
    static void run() {
        sec("safety+about");
        Settings s = new Settings();
        ok(!s.safetyAccepted, "safety notice shown until accepted");
        s.safetyAccepted = true; ok(Settings.from(s.toMap()).safetyAccepted, "acceptance persists");
        for (int[] z : new int[][]{{800, 480}, {1920, 1080}, {1080, 1920}, {2400, 1080}}) {
            Layout L = Layout.compute(z[0], z[1], z[0] == 1080 ? 2.625f : z[0] == 2400 ? 2f : z[0] == 1920 ? 1.5f : 1f);
            for (InfoPanel.Kind k : InfoPanel.Kind.values()) {
                InfoPanel p = new InfoPanel();
                BufferedImage img = new BufferedImage(L.w, L.h, BufferedImage.TYPE_INT_ARGB);
                p.draw(new AwtGfx(img.createGraphics()), L, Theme.DAY, k, k == InfoPanel.Kind.SAFETY ? InfoPanel.SAFETY : InfoPanel.about("x"));
                boolean accept = false, close = false, lic = false;
                for (int y = 0; y < L.h; y += 4) for (int x = 0; x < L.w; x += 4) { String h = p.hit(x, y); accept |= InfoPanel.ACCEPT.equals(h); close |= InfoPanel.CLOSE.equals(h); lic |= InfoPanel.LICENCES.equals(h); }
                ok(k == InfoPanel.Kind.SAFETY ? accept && !close : close && lic && !accept, z[0] + "x" + z[1] + " " + k + ": buttons reachable");
            }
        }
        boolean about = false; for (SettingRow r : SettingsPanel.coreRows(new Settings())) about |= Settings.K_ABOUT.equals(r.key);
        ok(about, "settings has About and licences");

        sec("drive-lock");
        ViewState v = new ViewState();
        ok(!SettingsPanel.locked(v), "parked: unlocked");
        v.driving = true; v.speedMps = 0.3; ok(!SettingsPanel.locked(v), "driving but stopped: unlocked");
        v.speedMps = 5; ok(SettingsPanel.locked(v), "moving: locked");
        v.speedMps = Double.NaN; ok(SettingsPanel.locked(v), "driving, speed unknown: locked");
        Layout L = Layout.compute(1280, 720, 1f);
        SettingsPanel p = new SettingsPanel();
        BufferedImage img = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
        v.speedMps = 20;
        p.draw(new AwtGfx(img.createGraphics()), L, Theme.DAY, new Settings(), Addons.NONE, v, 0);
        boolean done = false, other = false;
        for (int y = 0; y < 720; y += 3) for (int x = 0; x < 1280; x += 3) { SettingsPanel.Hit h = p.hit(x, y); if (h != null) { if (SettingsPanel.DONE.equals(h.section)) done = true; else other = true; } }
        ok(done && !other, "moving: only Done is tappable");

        sec("three-zone render");
        HudModel m = new HudModel(); m.setDemo(true);
        m.onHud(HudFrame.parse("{\"schemaVersion\":3,\"state\":\"current\",\"onroad\":true,\"engaged\":true,\"vEgo\":25,\"alert\":{\"text1\":\"TAKE CONTROL\",\"text2\":\"\",\"status\":\"critical\"}}"), 0);
        ViewState dv = m.snapshot(10);
        Layout U = Layout.compute(2400, 1080, 2f);
        BufferedImage u = new BufferedImage(2400, 1080, BufferedImage.TYPE_INT_ARGB);
        new DriveRenderer().draw(new AwtGfx(u.createGraphics()), U, dv, Theme.NIGHT);
        ok((u.getRGB(U.info.cx(), U.info.b() - 20) >>> 24) == 0xFF && (u.getRGB(U.map.cx(), U.map.cy()) >>> 24) == 0xFF, "info column + map drawn");
    }
}
