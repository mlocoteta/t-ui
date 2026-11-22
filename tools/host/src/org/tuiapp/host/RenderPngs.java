package org.tuiapp.host;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import org.tuiapp.core.*;

/**
 * Host screenshots from the synthetic replay: every screen profile x light/night x full/split for the driving state,
 * every other state in the automatic layout, the settings screen, contact sheets and an index.md.
 * Usage: RenderPngs REPLAY.jsonl OUTDIR [demo-map.json]
 */
public final class RenderPngs {
    /** name, w, h, density */
    public static final Object[][] SIZES = {
            {"800x480", 800, 480, 1.0f}, {"1280x720", 1280, 720, 1.0f}, {"1920x1080", 1920, 1080, 1.5f},
            {"1080x1920-portrait", 1080, 1920, 2.625f}, {"2400x1080-ultrawide", 2400, 1080, 2.0f}};
    /** state name -> replay time (ms); -1 = no comma (no data) */
    static final Map<String, Long> STATES = new LinkedHashMap<String, Long>();
    static {
        STATES.put("engaged", 30000L); STATES.put("ready", 6000L); STATES.put("parked", 3000L); STATES.put("lead-close", 41000L);
        STATES.put("lane-change", 47000L); STATES.put("stale", 53900L); STATES.put("takeover", 57200L); STATES.put("no-comma", -1L);
    }
    static final String[] THEMES = {"light", "night"};

    public static ViewState at(String replay, long tMs) throws Exception {
        if (tMs < 0) { ViewState v = new ViewState(); v.link = LinkState.NO_COMMA; v.linkDetail = "192.0.2.10:8082 (example address)"; return v; }
        ReplaySource rs = ReplaySource.read(new InputStreamReader(new FileInputStream(replay), "UTF-8"), false, null);
        HudModel m = new HudModel(); m.setDemo(true);
        rs.runTo(tMs, 0, m);
        return m.snapshot(tMs);
    }

    public static MapData map(String path) throws Exception {
        return path == null ? null : MapData.parse(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
    }

    public static BufferedImage render(ViewState v, int w, int h, float d, Layout.Mode mode, boolean driverLeft, Theme t, MapData map) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        new DriveRenderer().draw(new AwtGfx(g), Layout.compute(w, h, d, mode, driverLeft), v, t, map);
        g.dispose();
        return img;
    }

    public static BufferedImage settings(int w, int h, float d, Theme t, Settings s, Addons a, ViewState v, MapData map) { return settings(w, h, d, t, s, a, v, map, false); }

    /** scrollEnd: show the bottom of the list (small screens with add-on rows) */
    public static BufferedImage settings(int w, int h, float d, Theme t, Settings s, Addons a, ViewState v, MapData map, boolean scrollEnd) {
        Layout L = Layout.compute(w, h, d, s.layout, !s.rightHandDrive);
        SettingsPanel p = new SettingsPanel();
        if (scrollEnd) { BufferedImage tmp = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); p.draw(new AwtGfx(tmp.createGraphics()), L, t, s, a, v, v.nowMs); p.scrollToEnd(); }
        BufferedImage img = render(v, w, h, d, s.layout, !s.rightHandDrive, t, map);
        Graphics2D g = img.createGraphics();
        p.draw(new AwtGfx(g), L, t, s, a, v, v.nowMs);
        g.dispose();
        return img;
    }

    /** grid of thumbnails (each scaled to cellW wide) with captions */
    public static BufferedImage sheet(String title, List<BufferedImage> imgs, List<String> caps, int cols, int cellW) {
        int rowsN = (imgs.size() + cols - 1) / cols, capH = 22, pad = 12, top = 44;
        int[] rowH = new int[rowsN];
        for (int k = 0; k < imgs.size(); k++) rowH[k / cols] = Math.max(rowH[k / cols], imgs.get(k).getHeight() * cellW / imgs.get(k).getWidth());
        int[] rowY = new int[rowsN]; int y0 = top;
        for (int r = 0; r < rowsN; r++) { rowY[r] = y0; y0 += rowH[r] + capH + pad; }
        BufferedImage out = new BufferedImage(cols * (cellW + pad) + pad, y0 + pad, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x2B2F36)); g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.setColor(Color.WHITE); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20)); g.drawString(title, pad, 30);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        for (int k = 0; k < imgs.size(); k++) {
            BufferedImage i = imgs.get(k); int x = pad + (k % cols) * (cellW + pad), y = rowY[k / cols];
            int ih = i.getHeight() * cellW / i.getWidth();
            g.drawImage(i, x, y, cellW, ih, null);
            g.setColor(new Color(0xD5D9DF)); g.drawString(caps.get(k), x, y + ih + 16);
        }
        g.dispose();
        return out;
    }

    public static void main(String[] a) throws Exception {
        String replay = a[0]; File dir = new File(a[1]); dir.mkdirs();
        MapData map = map(a.length > 2 ? a[2] : null);
        Map<String, ViewState> st = new LinkedHashMap<String, ViewState>();
        for (Map.Entry<String, Long> e : STATES.entrySet()) st.put(e.getKey(), at(replay, e.getValue()));
        int n = 0;
        StringBuilder idx = new StringBuilder();
        idx.append("# T-UI v0.2 host screenshots\n\nSynthetic replay and synthetic demo map only (no real drive, no real place). ")
           .append("Rendered on the host with the same core renderer the app uses (java.awt instead of Android Canvas; fonts differ slightly).\n\n")
           .append("Contact sheets: `sheet-*.png`. Files: `<state>/<size>-<theme>-<mode>.png`.\n\n");
        List<BufferedImage> sheetImgs = new ArrayList<BufferedImage>(); List<String> sheetCaps = new ArrayList<String>();
        List<BufferedImage> portraitImgs = new ArrayList<BufferedImage>(); List<String> portraitCaps = new ArrayList<String>();
        // 1. driving (engaged): every size x theme x mode
        File dd = new File(dir, "engaged"); dd.mkdirs();
        idx.append("## engaged (all sizes, light/night, full + split)\n\n");
        for (Object[] s : SIZES) for (String th : THEMES) for (Layout.Mode mode : new Layout.Mode[]{Layout.Mode.FLOATING, Layout.Mode.SPLIT}) {
            String name = (String) s[0]; int w = (Integer) s[1], h = (Integer) s[2]; float d = (Float) s[3];
            boolean dflt = Layout.compute(w, h, d).mode == mode;
            String f = name + "-" + th + "-" + (mode == Layout.Mode.FLOATING ? "full" : "split") + (dflt ? "-default" : "") + ".png";
            BufferedImage img = render(st.get("engaged"), w, h, d, mode, true, th.equals("night") ? Theme.NIGHT : Theme.DAY, map);
            ImageIO.write(img, "png", new File(dd, f)); n++;
            idx.append("- engaged/").append(f).append('\n');
            if (th.equals("light") || name.startsWith("1920")) {
                String cap = name + " " + th + " " + (mode == Layout.Mode.FLOATING ? "full" : "split");
                if (name.contains("portrait")) { portraitImgs.add(img); portraitCaps.add(cap); } else { sheetImgs.add(img); sheetCaps.add(cap); }
            }
        }
        sheetImgs.addAll(portraitImgs); sheetCaps.addAll(portraitCaps);
        ImageIO.write(sheet("T-UI v0.2: engaged, all sizes (full = floating cards, split = scene + map)", sheetImgs, sheetCaps, 4, 420), "png", new File(dir, "sheet-engaged.png")); n++;
        // 2. other states: every size x theme, automatic layout
        List<BufferedImage> si = new ArrayList<BufferedImage>(); List<String> sc = new ArrayList<String>();
        for (String state : st.keySet()) {
            if (state.equals("engaged")) continue;
            File sd = new File(dir, state); sd.mkdirs();
            idx.append("\n## ").append(state).append(" (all sizes, light/night, automatic layout)\n\n");
            for (Object[] s : SIZES) for (String th : THEMES) {
                String name = (String) s[0]; int w = (Integer) s[1], h = (Integer) s[2]; float d = (Float) s[3];
                String f = name + "-" + th + ".png";
                BufferedImage img = render(st.get(state), w, h, d, Layout.Mode.AUTO, true, th.equals("night") ? Theme.NIGHT : Theme.DAY, map);
                ImageIO.write(img, "png", new File(sd, f)); n++;
                idx.append("- ").append(state).append('/').append(f).append('\n');
                if (name.equals("800x480") && th.equals("light") || name.equals("1920x1080") && th.equals("night")) { si.add(img); sc.add(state + " " + name + " " + th); }
            }
        }
        ImageIO.write(sheet("T-UI v0.2: states (800x480 light, 1920x1080 night)", si, sc, 4, 420), "png", new File(dir, "sheet-states.png")); n++;
        // 3. settings screen (default build: no add-on section)
        File sd = new File(dir, "settings"); sd.mkdirs();
        idx.append("\n## settings (default build)\n\n");
        Settings s = new Settings();
        for (Object[] z : new Object[][]{{"800x480", 800, 480, 1f}, {"1920x1080", 1920, 1080, 1.5f}, {"1080x1920-portrait", 1080, 1920, 2.625f}})
            for (String th : THEMES) {
                String f = z[0] + "-" + th + ".png";
                ImageIO.write(settings((Integer) z[1], (Integer) z[2], (Float) z[3], th.equals("night") ? Theme.NIGHT : Theme.DAY, s, Addons.NONE, st.get("parked"), map), "png", new File(sd, f)); n++;
                idx.append("- settings/").append(f).append('\n');
            }
        try (PrintWriter pw = new PrintWriter(new File(dir, "index.md"), "UTF-8")) { pw.print(idx); }
        System.out.println("rendered " + n + " PNGs -> " + dir);
    }
}
