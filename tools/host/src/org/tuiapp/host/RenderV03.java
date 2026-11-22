package org.tuiapp.host;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.tuiapp.core.*;

/**
 * v0.3 host screenshots: the offline maps screen (select, over limit, downloading, paused, ready, lean build) and the
 * drive screen's map fallbacks. All positions synthetic (near 0,0; the demo town is drawn there as the preview).
 * The live MapLibre map cannot be rendered on the host: where it would show, a hatched "device only" pattern is drawn.
 * Usage: RenderV03 OUTDIR demo-map.json REPLAY.jsonl
 */
public final class RenderV03 {
    static final double LAT = 0.21, LON = 0.43;
    static final Object[][] SIZES = {{"800x480", 800, 480, 1f}, {"1920x1080", 1920, 1080, 1.5f}};

    static OfflineModel.Region region(long id, double radiusM, int maxZ, OfflineModel.State st, float prog, long bytes) {
        OfflineModel.Region r = new OfflineModel.Region();
        r.id = id; r.bounds = Geo.around(LAT + (id - 2) * 0.03, LON + (id - 2) * 0.05, radiusM); r.minZ = OfflinePanel.MIN_Z; r.maxZ = maxZ;
        r.name = OfflinePanel.areaName(r.bounds, maxZ); r.state = st; r.needRes = 1000; r.doneRes = (long) (prog * 1000); r.bytes = bytes;
        return r;
    }

    static BufferedImage offline(String state, int w, int h, float d, Theme t, MapData town) {
        Layout L = Layout.compute(w, h, d);
        float[] r = OfflinePanel.viewportRect(L);
        double zoom = 13.3 + Math.log(r[2] / 460.0) / Math.log(2);
        Geo.Viewport vp = new Geo.Viewport(LAT, LON, zoom, r[0], r[1], r[2], r[3]);
        OfflinePanel p = new OfflinePanel(); p.viewport = vp;
        OfflineModel m = new OfflineModel(); m.available = !state.equals("lean-build");
        boolean fix = true;
        if (state.equals("select")) { p.drawMode = true; p.selectRect(r[0] + r[2] * 0.22f, r[1] + r[3] * 0.18f, r[0] + r[2] * 0.8f, r[1] + r[3] * 0.78f); }
        if (state.equals("over-limit")) { p.selectAround(LAT, LON, 50000); p.detail = 2; vp = vp.zoomBy(-5.2); }
        if (state.equals("downloading")) { m.put(region(1, 4000, 14, OfflineModel.State.DOWNLOADING, 0.42f, 17400000)); m.put(region(2, 9000, 12, OfflineModel.State.READY, 1f, 8900000)); }
        if (state.equals("paused")) { m.put(region(1, 4000, 14, OfflineModel.State.PAUSED, 0.63f, 26100000)); }
        if (state.equals("ready")) { m.put(region(1, 4000, 14, OfflineModel.State.READY, 1f, 41200000)); m.put(region(2, 9000, 12, OfflineModel.State.READY, 1f, 8900000)); m.put(region(3, 3000, 13, OfflineModel.State.READY, 1f, 6300000)); }
        if (state.equals("lean-build")) { p.selectVisible(); }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        p.draw(new AwtGfx(g), L, t, vp, false, town, new double[]{LAT, LON}, m, fix, LAT, LON, false);
        g.dispose();
        return img;
    }

    /** where the live map would be (transparent pixels): hatched placeholder so the PNG is honest */
    static BufferedImage fillHole(BufferedImage img, boolean night) {
        BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(night ? 0x1C2530 : 0xDCE6EF)); g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(new Color(night ? 0x26323F : 0xC9D6E2));
        for (int x = -img.getHeight(); x < img.getWidth(); x += 18) g.drawLine(x, img.getHeight(), x + img.getHeight(), 0);
        g.setColor(new Color(night ? 0x8FA0B3 : 0x52606E)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(12, img.getHeight() / 40)));
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return out;
    }
    static void holeLabel(BufferedImage img, Layout.R m, boolean night) {
        Graphics2D g = img.createGraphics();
        String s = "live MapLibre map here (device only)";
        int fs = Math.max(10, m.h / 18);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fs));
        while (g.getFontMetrics().stringWidth(s) > m.w - 20 && fs > 9) g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, --fs));
        g.setColor(new Color(night ? 0xB8C4D2 : 0x3A4552));
        int sw = g.getFontMetrics().stringWidth(s);
        g.drawString(s, m.x + (m.w - sw) / 2, m.y + m.h / 2 + 40);
        g.dispose();
    }

    public static void main(String[] a) throws Exception {
        File dir = new File(a[0]); dir.mkdirs();
        MapData town = RenderPngs.map(a[1]);
        String replay = a[2];
        StringBuilder idx = new StringBuilder("# T-UI v0.3 map screenshots (host-rendered)\n\n"
                + "All positions are synthetic (open ocean near 0,0; the synthetic demo town is drawn there as the map preview).\n"
                + "On the device the map area of the offline screen shows the live MapLibre map (pan / pinch); the host cannot run\n"
                + "MapLibre, so it draws a tile-grid preview instead. Drive screens marked `engine-*` show a hatched area where the\n"
                + "live map would be.\n\nStart with `sheet-offline.png` and `sheet-drive-map.png`.\n\n## Offline maps screen\n\n");
        List<BufferedImage> si = new ArrayList<BufferedImage>(); List<String> sc = new ArrayList<String>();
        String[] states = {"select", "over-limit", "downloading", "paused", "ready", "lean-build"};
        int n = 0;
        for (String st : states) for (Object[] z : SIZES) for (String th : new String[]{"light", "night"}) {
            BufferedImage img = offline(st, (Integer) z[1], (Integer) z[2], (Float) z[3], th.equals("night") ? Theme.NIGHT : Theme.DAY, town);
            String f = "offline-" + st + "-" + z[0] + "-" + th + ".png";
            ImageIO.write(img, "png", new File(dir, f)); n++;
            idx.append("- ").append(f).append('\n');
            if (z[0].equals("800x480") && th.equals("light") || z[0].equals("1920x1080") && th.equals("night") && !st.equals("lean-build")) { si.add(img); sc.add(st + " " + z[0] + " " + th); }
        }
        BufferedImage port = offline("select", 1080, 1920, 2.625f, Theme.DAY, town);
        ImageIO.write(port, "png", new File(dir, "offline-select-1080x1920-portrait-light.png")); n++;
        idx.append("- offline-select-1080x1920-portrait-light.png (tablet portrait)\n");
        ImageIO.write(RenderPngs.sheet("T-UI v0.3: offline maps screen (synthetic preview map; live MapLibre map on the device)", si, sc, 4, 420), "png", new File(dir, "sheet-offline.png")); n++;

        // drive screen map modes
        idx.append("\n## Drive screen: map fallbacks (online -> offline -> built-in)\n\n");
        HudModel hm = new HudModel();
        String hud = "{\"schemaVersion\":3,\"state\":\"current\",\"onroad\":true,\"engaged\":true,\"available\":true,\"vEgo\":24.0,\"isMetric\":false,"
                + "\"vCruise\":29.06,\"steering\":{\"latActive\":true},\"gearLetter\":\"D\",\"slc\":{\"limit\":24.59},"
                + "\"gps\":{\"lat\":" + LAT + ",\"lon\":" + LON + ",\"bearingDeg\":60.0,\"speedMs\":24.0},"
                + "\"nav\":{\"active\":true,\"gen\":3,\"maneuver\":{\"type\":\"turn\",\"modifier\":\"right\",\"distM\":450,\"street\":\"Example Rd\"},\"remainM\":8000,\"remainS\":600}}";
        hm.onLink(LinkState.LIVE, "");
        hm.onHud(HudFrame.parse(hud), 0);
        StringBuilder rj = new StringBuilder("{\"gen\":3,\"coords\":[");
        double[][] pts = {{-0.004, -0.0025}, {0, 0}, {0.0035, 0.002}, {0.006, 0.0036}, {0.0075, 0.0015}, {0.009, -0.001}, {0.013, -0.002}};
        for (int i = 0; i < pts.length; i++) rj.append(i > 0 ? "," : "").append("[").append(LON + pts[i][0]).append(",").append(LAT + pts[i][1]).append("]");
        hm.onRoute(RouteGeo.parse(rj.append("]}").toString()), 0);
        ViewState v = hm.snapshot(10);
        ViewState nofix = RenderPngs.at(replay, 30000).copy(); nofix.demo = false; nofix.link = LinkState.LIVE;
        List<BufferedImage> di = new ArrayList<BufferedImage>(); List<String> dc = new ArrayList<String>();
        Object[][] modes = {{"builtin-route", MapChooser.Mode.BUILTIN_ROUTE, v}, {"no-fix", MapChooser.Mode.NO_FIX, nofix},
                {"engine-online", MapChooser.Mode.ENGINE_ONLINE, v}, {"engine-offline", MapChooser.Mode.ENGINE_OFFLINE, v}};
        for (Object[] md : modes) for (Object[] z : SIZES) for (String th : new String[]{"light", "night"}) for (Layout.Mode lm : new Layout.Mode[]{Layout.Mode.SPLIT, Layout.Mode.FLOATING}) {
            if (lm == Layout.Mode.FLOATING && !z[0].equals("800x480")) continue;
            int w = (Integer) z[1], h = (Integer) z[2]; float d = (Float) z[3];
            Layout L = Layout.compute(w, h, d, lm, true);
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            MapChooser.Mode mode = (MapChooser.Mode) md[1];
            Graphics2D g = img.createGraphics();
            new DriveRenderer().draw(new AwtGfx(g), L, (ViewState) md[2], th.equals("night") ? Theme.NIGHT : Theme.DAY, null, mode, MapSources.attribution(MapSources.OFM_LIBERTY));
            g.dispose();
            if (mode.engine()) { img = fillHole(img, th.equals("night")); holeLabel(img, L.map, th.equals("night")); }
            String f = "drive-" + md[0] + "-" + z[0] + "-" + th + "-" + (lm == Layout.Mode.SPLIT ? "split" : "full") + ".png";
            ImageIO.write(img, "png", new File(dir, f)); n++;
            idx.append("- ").append(f).append('\n');
            if (th.equals("light") && lm == Layout.Mode.SPLIT || z[0].equals("800x480") && th.equals("night") && lm == Layout.Mode.FLOATING) { di.add(img); dc.add(md[0] + " " + z[0] + " " + th + " " + (lm == Layout.Mode.SPLIT ? "split" : "full")); }
        }
        ImageIO.write(RenderPngs.sheet("T-UI v0.3: map on the drive screen (built-in fallbacks; hatched = live MapLibre map on device)", di, dc, 4, 420), "png", new File(dir, "sheet-drive-map.png")); n++;
        try (PrintWriter pw = new PrintWriter(new File(dir, "index.md"), "UTF-8")) { pw.print(idx); }
        System.out.println("rendered " + n + " PNGs -> " + dir);
    }
}
