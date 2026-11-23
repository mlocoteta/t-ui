package org.tuiapp.test;

import static org.tuiapp.test.HostTests.near;
import static org.tuiapp.test.HostTests.ok;
import static org.tuiapp.test.HostTests.sec;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tuiapp.core.*;
import org.tuiapp.host.AwtGfx;
import org.tuiapp.host.IconGen;

/** v0.2 host tests: settings, settings panel, easing, map, add-on extension points, connection screen, writes off. */
public final class V02 {
    static void run(File fx, String replay, String mapPath) throws Exception {
        settings(); panel(); smoother(); addons(fx); writesOff(); odometer(fx);
        if (mapPath != null) map(mapPath);
        screens(replay, mapPath);
    }

    static void settings() {
        sec("settings");
        Settings d = Settings.from(null);
        ok(d.commaAddress.isEmpty() && d.theme == Settings.ThemePref.AUTO && d.units == Settings.Units.AUTO && d.layout == Layout.Mode.AUTO && d.keepScreenOn && !d.rightHandDrive, "defaults");
        Settings s = new Settings();
        s.commaAddress = "host.example:8082"; s.theme = Settings.ThemePref.DARK; s.units = Settings.Units.KMH; s.layout = Layout.Mode.SPLIT;
        s.keepScreenOn = false; s.rightHandDrive = true; s.put("x-addon", "k", "v1");
        Settings r = Settings.from(s.toMap());
        ok(r.toMap().equals(s.toMap()), "round trip through the flat store");
        ok("v1".equals(r.get("x-addon", "k", null)) && "dflt".equals(r.get("x-addon", "nope", "dflt")), "add-on keys persist");
        Map<String, String> junk = new HashMap<String, String>();
        junk.put(Settings.K_THEME, "purple"); junk.put(Settings.K_UNITS, "kmh"); junk.put(Settings.K_LAYOUT, null); junk.put(Settings.K_SCREEN_ON, "maybe");
        Settings j = Settings.from(junk);
        ok(j.theme == Settings.ThemePref.AUTO && j.units == Settings.Units.KMH && j.layout == Layout.Mode.AUTO && j.keepScreenOn, "bad values fall back, case-insensitive enums");
        Settings u = new Settings();
        ok(!u.metric(null) && u.metric(true) && !u.metric(false), "units auto follows the comma (unknown = mph)");
        u.units = Settings.Units.MPH; ok(!u.metric(true), "mph forced");
        u.units = Settings.Units.KMH; ok(u.metric(false), "km/h forced");
        ViewState v = new ViewState(); v.isMetric = false; u.applyTo(v); ok(Boolean.TRUE.equals(v.isMetric) && "km/h".equals(v.unit()), "applyTo sets display units");
        ok(new Settings().theme(true) == Theme.NIGHT && new Settings().theme(false) == Theme.DAY, "theme auto follows the system");
        Settings l = new Settings(); l.theme = Settings.ThemePref.LIGHT; ok(l.theme(true) == Theme.DAY, "light forced at night");
    }

    static void panel() {
        sec("settings-panel");
        Settings s = new Settings();
        ok(SettingsPanel.coreRows(s).size() == 11, "11 core rows (address, theme, units, layout, screen on, RHD, my car colour, online tiles, source, about, offline)");
        ok(SettingsPanel.apply(s, Settings.K_THEME, 2) && s.theme == Settings.ThemePref.DARK, "theme chip");
        ok(!SettingsPanel.apply(s, Settings.K_THEME, 2), "same value: no change");
        ok(SettingsPanel.apply(s, Settings.K_UNITS, 1) && s.units == Settings.Units.MPH, "units chip");
        ok(SettingsPanel.apply(s, Settings.K_LAYOUT, 1) && s.layout == Layout.Mode.FLOATING, "layout chip");
        ok(SettingsPanel.apply(s, Settings.K_SCREEN_ON, 0) && !s.keepScreenOn, "keep screen on");
        ok(SettingsPanel.apply(s, Settings.K_RHD, 1) && s.rightHandDrive, "RHD");
        ok(!SettingsPanel.apply(s, Settings.K_THEME, 7) && !SettingsPanel.apply(s, "nope", 0), "out of range / unknown ignored");
        for (int[] sz : new int[][]{{800, 480}, {1280, 720}, {1080, 1920}, {2400, 1080}}) {
            Layout L = Layout.compute(sz[0], sz[1], sz[0] == 1080 ? 2.625f : 1f);
            BufferedImage img = new BufferedImage(L.w, L.h, BufferedImage.TYPE_INT_ARGB);
            SettingsPanel p = new SettingsPanel();
            p.draw(new AwtGfx(img.createGraphics()), L, Theme.DAY, new Settings(), Addons.NONE, new ViewState(), 0);
            ok(p.zoneCount() >= 14, sz[0] + "x" + sz[1] + ": tap zones (" + p.zoneCount() + ")");
            // find the Done zone and a theme chip by scanning
            SettingsPanel.Hit done = null, dark = null, addr = null;
            for (int y = 0; y < L.h; y += 4) for (int x = 0; x < L.w; x += 4) {
                SettingsPanel.Hit h = p.hit(x, y); if (h == null) continue;
                if (SettingsPanel.DONE.equals(h.section)) done = h;
                if (Settings.K_THEME.equals(h.key) && h.option == 2) dark = h;
                if (h.text) addr = h;
            }
            ok(done != null && dark != null && addr != null && Settings.K_ADDR.equals(addr.key), sz[0] + "x" + sz[1] + ": Done, theme chips and address row reachable");
            boolean reach = false;
            p.scrollToEnd(); p.draw(new AwtGfx(img.createGraphics()), L, Theme.DAY, new Settings(), Addons.NONE, new ViewState(), 0);
            for (int y = 0; y < L.h; y += 4) for (int x = 0; x < L.w; x += 4) { SettingsPanel.Hit h = p.hit(x, y); if (h != null && Settings.K_OFFLINE.equals(h.key)) reach = true; }
            ok(reach, sz[0] + "x" + sz[1] + ": last core row reachable (max scroll " + (int) p.maxScroll() + ")");
        }
    }

    static void smoother() {
        sec("easing");
        Smoother sm = new Smoother();
        ViewState a = new ViewState(); a.speedMps = 10; a.leadPresent = true; a.leadDRel = 30; a.leadYRel = 0;
        a.laneLines = new float[][][]{{{0, -1.8f}, {50, -1.8f}}}; a.tracks = new float[][]{{20, 3, 0, 4, 20, 0}};
        ViewState o = sm.apply(a, 0);
        near(o.speedMps, 10, "first frame shown as is");
        ViewState b = a.copy(); b.speedMps = 20; b.leadDRel = 26; b.laneLines = new float[][][]{{{0, -1.0f}, {50, -1.0f}}}; b.tracks = new float[][]{{22, 3.5f, 0, 4, 20, 0}};
        o = sm.apply(b, 33);
        ok(o.speedMps > 10 && o.speedMps < 20, "speed eases (" + o.speedMps + ")");
        ok(o.leadDRel < 30 && o.leadDRel > 26, "lead distance eases");
        ok(o.laneLines[0][0][1] > -1.8f && o.laneLines[0][0][1] < -1.0f && o.laneLines[0][1][0] == 50, "lane y eases, x kept");
        ok(o.tracks[0][0] > 20 && o.tracks[0][0] < 22, "same track id eases");
        ok(b.speedMps == 20 && b.laneLines[0][0][1] == -1.0f, "target not modified");
        double prev = o.speedMps; boolean mono = true;
        for (long t = 66; t <= 2000; t += 33) { o = sm.apply(b, t); mono &= o.speedMps >= prev - 1e-9 && o.speedMps <= 20 + 1e-9; prev = o.speedMps; }
        ok(mono, "monotonic, no overshoot");
        ok(Math.abs(o.speedMps - 20) < 0.01 && Math.abs(o.leadDRel - 26) < 0.01, "converges in < 2 s");
        // time constant: after tau the gap is 1/e
        Smoother t2 = new Smoother(); ViewState x = new ViewState(); x.speedMps = 0; t2.apply(x, 0);
        ViewState y = new ViewState(); y.speedMps = 10; double v = t2.apply(y, 250).speedMps;
        near(Math.round(v * 1000) / 1000.0, Math.round(10 * (1 - Math.exp(-1)) * 1000) / 1000.0, "tau 0.25 s");
        // snaps
        ViewState c = b.copy(); c.tracks = new float[][]{{40, 0, 0, 9, 20, 0}}; c.laneLines = new float[][][]{{{0, 1}, {20, 1}, {40, 1}}};
        o = sm.apply(c, 2033);
        ok(o.tracks[0][0] == 40 && o.laneLines[0].length == 3 && o.laneLines[0][0][1] == 1, "new id / new shape snap");
        ViewState d = c.copy(); d.leadDRel = 5; o = sm.apply(d, 2066);
        near(o.leadDRel, 5, "lead jump > 12 m snaps");
        ViewState e = d.copy(); e.speedMps = 0; o = sm.apply(e, 5000);
        near(o.speedMps, 0, "gap > 1 s snaps");
        ViewState f = e.copy(); f.speedMps = Double.NaN; o = sm.apply(f, 5033);
        ok(Double.isNaN(o.speedMps), "unknown stays unknown");
    }

    /** a generic test add-on (no car specifics): proves the extension points without the owner-only module */
    public static final class TestAddon implements Addon {
        final String id; String seen; int selects;
        public TestAddon() { this("test-addon"); }
        TestAddon(String id) { this.id = id; }
        public String id() { return id; }
        public String title() { return "Test add-on"; }
        public void onHud(Map<String, Object> raw, long now) { seen = Json.str(raw, "extraField"); }
        public List<SettingRow> settingRows(ViewState v, Settings s, long now) { return Collections.singletonList(new SettingRow("k", "Extra", new String[]{"A", "B"}, 0)); }
        public WriteRequest select(String key, int o, ViewState v, Settings s, long now) { selects++; return o == 1 ? new WriteRequest(id(), key, "/api/test", "{}") : null; }
        public void onWriteResult(WriteRequest r, int status, String body, long now) { }
        public List<AddonCard> cards(ViewState v, Settings s, long now) { return Collections.singletonList(new AddonCard("Extra: " + seen, AddonCard.INFO)); }
    }

    static void addons(File fx) throws Exception {
        sec("addons");
        ok(Addons.NONE.isEmpty() && Addons.NONE.cards(new ViewState(), new Settings(), 0).isEmpty(), "default: no add-ons, no cards");
        List<String> err = new ArrayList<String>();
        Addons a = Addons.load(new StringReader("# list\norg.tuiapp.test.V02$TestAddon\n\nno.such.Addon\n"), err);
        ok(a.all().size() == 1 && err.size() == 1, "loader: one loaded, a missing class reported not fatal");
        HudModel m = new HudModel(); m.setAddons(a);
        String cur = new String(Files.readAllBytes(new File(fx, "hud-current.json").toPath()), StandardCharsets.UTF_8);
        m.onHud(HudFrame.parse(cur.replace("\"error\":\"\"", "\"error\":\"\",\"extraField\":\"hello\"")), 0);
        TestAddon t = (TestAddon) a.byId("test-addon");
        ok("hello".equals(t.seen), "add-on reads extra /api/hud fields from raw");
        ok(HudFrame.parse(cur).raw.containsKey("schemaVersion"), "raw map kept on the frame");
        ViewState v = m.snapshot(10); v.addonCards = a.cards(v, new Settings(), 10);
        v.driving = false;   // settings are locked while moving; this test is about the parked panel
        ok(v.addonCards.size() == 1 && "Extra: hello".equals(v.addonCards.get(0).text), "add-on card");
        Layout L = Layout.compute(800, 480, 1f);
        BufferedImage img = new BufferedImage(800, 480, BufferedImage.TYPE_INT_ARGB);
        SettingsPanel p = new SettingsPanel();
        p.draw(new AwtGfx(img.createGraphics()), L, Theme.DAY, new Settings(), a, v, 0);
        SettingsPanel.Hit hb = null;
        for (int y = 0; y < 480; y += 3) for (int x = 0; x < 800; x += 3) { SettingsPanel.Hit h = p.hit(x, y); if (h != null && "test-addon".equals(h.section) && h.option == 1) hb = h; }
        if (hb == null) {   // small screen: the add-on section may be below the fold -> scroll
            p.scrollToEnd(); p.draw(new AwtGfx(img.createGraphics()), L, Theme.DAY, new Settings(), a, v, 0);
            for (int y = 0; y < 480; y += 3) for (int x = 0; x < 800; x += 3) { SettingsPanel.Hit h = p.hit(x, y); if (h != null && "test-addon".equals(h.section) && h.option == 1) hb = h; }
        }
        ok(hb != null, "add-on row is drawn and tappable in its own section");
        // overflow: many rows -> scrollable, every zone stays inside the visible body
        List<Addon> many = new ArrayList<Addon>(); for (int i = 0; i < 4; i++) many.add(new TestAddon("t" + i));
        SettingsPanel q = new SettingsPanel(); Addons ma = new Addons(many);
        BufferedImage img2 = new BufferedImage(800, 480, BufferedImage.TYPE_INT_ARGB);
        q.draw(new AwtGfx(img2.createGraphics()), L, Theme.DAY, new Settings(), ma, v, 0);
        float ms = q.maxScroll();
        q.scrollToEnd(); q.draw(new AwtGfx(img2.createGraphics()), L, Theme.DAY, new Settings(), ma, v, 0);
        boolean lastVisible = false;
        for (int y = 0; y < 480; y += 3) for (int x = 0; x < 800; x += 3) { SettingsPanel.Hit h = q.hit(x, y); if (h != null && "t3".equals(h.section)) lastVisible = true; }
        ok(ms > 0 && lastVisible, "overflowing panel scrolls to the last add-on row (max scroll " + (int) ms + " px)");
        ok(t.select(hb.key, hb.option, v, new Settings(), 0) != null && t.selects == 1, "select routes to the add-on");
    }

    static void writesOff() {
        sec("read-only");
        CommaHttpSource s = new CommaHttpSource("127.0.0.1:9", null);
        ok(!s.writesEnabled(), "writes off by default");
        boolean threw = false; try { s.post("/api/x", "{}"); } catch (IllegalStateException e) { threw = true; } catch (Exception other) { }
        ok(threw, "post refused while writes are off (nothing sent)");
    }

    static void odometer(File fx) throws Exception {
        sec("odometer");
        HudModel m = new HudModel();
        String cur = new String(Files.readAllBytes(new File(fx, "hud-current.json").toPath()), StandardCharsets.UTF_8);
        m.onHud(HudFrame.parse(cur), 0);             // vEgo 24.5
        m.onHud(HudFrame.parse(cur), 1000);
        near(Math.round(m.snapshot(1000).distanceM * 10) / 10.0, 24.5, "distance integrates vEgo");
        ok(m.snapshot(1500).distanceM > 24.5 && m.snapshot(5000).distanceM <= 49.0 + 1e-6, "extrapolates at most 1 s");
        m.reset(); ok(m.snapshot(0).distanceM == 0 && !m.snapshot(0).hasData, "reset clears");
    }

    static void map(String path) throws Exception {
        sec("map");
        MapData d = MapData.parse(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ok(d.roads.size() > 20 && d.water.size() == 1 && d.route != null, "demo map parsed (" + d.roads.size() + " roads)");
        ok(d.routeLength() > 1500, "route long enough for the demo drive (" + (int) d.routeLength() + " m)");
        float[] p = new float[3]; d.along(0, p);
        ok(Math.abs(p[2] - Math.PI / 2) < 0.3, "starts heading north");
        float[] nt = d.nextTurn(100);
        ok(nt != null && nt[1] < 0 && nt[0] > 500 && nt[0] < 1000, "next turn: right in " + (nt == null ? -1 : (int) nt[0]) + " m");
        float[] nt2 = d.nextTurn(1100);
        ok(nt2 != null && nt2[1] > 0, "then a left turn");
        boolean threw = false; try { MapData.parse("{\"format\":\"other\"}"); } catch (IllegalArgumentException e) { threw = true; }
        ok(threw, "unknown format rejected");
        ok(MapData.parse("{\"format\":\"tui-map-1\",\"roads\":[{\"c\":\"x\",\"p\":[1]}]}").roads.isEmpty(), "bad road skipped");
        ok("0.5 mi".equals(MapRenderer.distTextForTest(805, false)) && "800 m".equals(MapRenderer.distTextForTest(803, true))
                && "1.2 km".equals(MapRenderer.distTextForTest(1200, true)) && "300 ft".equals(MapRenderer.distTextForTest(90, false)), "distance text");
    }

    static void screens(String replay, String mapPath) throws Exception {
        sec("screens");
        MapData d = mapPath == null ? null : MapData.parse(new String(Files.readAllBytes(new File(mapPath).toPath()), StandardCharsets.UTF_8));
        for (LinkState st : new LinkState[]{LinkState.SEARCHING, LinkState.NO_COMMA, LinkState.NOT_SET}) {
            ViewState v = new ViewState(); v.link = st; v.linkDetail = "host.example:8082";
            BufferedImage img = new BufferedImage(800, 480, BufferedImage.TYPE_INT_ARGB);
            new DriveRenderer().draw(new AwtGfx(img.createGraphics()), Layout.compute(800, 480, 1f), v, Theme.DAY, d);
            ok((img.getRGB(400, 240) >>> 24) == 0xFF, "connection screen " + st);
        }
        ViewState close = new ViewState(); close.leadPresent = true; close.leadDRel = 15; close.speedMps = 20; close.driving = true;
        ok(close.leadClose(), "lead 0.75 s ahead = close");
        close.leadDRel = 40; ok(!close.leadClose(), "2 s ahead = not close");
        BufferedImage ic = IconGen.draw(192);
        ok((ic.getRGB(96, 96) >>> 24) == 0xFF && (ic.getRGB(1, 1) >>> 24) == 0, "icon: opaque tile, transparent corners");
    }
}
