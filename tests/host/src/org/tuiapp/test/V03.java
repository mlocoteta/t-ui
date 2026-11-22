package org.tuiapp.test;

import static org.tuiapp.test.HostTests.near;
import static org.tuiapp.test.HostTests.ok;
import static org.tuiapp.test.HostTests.sec;

import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.tuiapp.core.*;
import org.tuiapp.host.AwtGfx;

/**
 * v0.3 map tests: gps/nav parsing, route geometry, geo math + offline size estimates, source limits, map choice
 * (online -> offline -> built-in), offline screen hits, map settings. All coordinates are synthetic, near 0,0.
 */
public final class V03 {
    /** synthetic position in the open ocean near 0,0 (no real place) */
    static final double LAT = 0.21, LON = 0.43;

    static String hud(boolean nav, int gen) {
        return "{\"schemaVersion\":3,\"state\":\"current\",\"onroad\":true,\"vEgo\":20.0,"
                + "\"gps\":{\"lat\":" + LAT + ",\"lon\":" + LON + ",\"bearingDeg\":90.0,\"speedMs\":20.0,\"accM\":4.0,\"ageS\":0.2},"
                + "\"nav\":" + (nav ? "{\"active\":true,\"gen\":" + gen + ",\"maneuver\":{\"type\":\"turn\",\"modifier\":\"slight left\",\"distM\":320,\"street\":\"Example Rd\",\"text\":\"Turn left\"},\"remainM\":5000,\"remainS\":400,\"dest\":\"Somewhere\"}" : "{\"active\":false}") + "}";
    }
    static String route(int gen) {
        return "{\"gen\":" + gen + ",\"coords\":[[" + LON + "," + LAT + "],[" + (LON + 0.01) + "," + LAT + "],[" + (LON + 0.01) + "," + (LAT + 0.01) + "],[\"bad\",1],[200,0]],\"cong\":[\"low\",\"heavy\"]}";
    }

    static void run() throws Exception {
        parse(); geo(); sources(); chooser(); model(); http(); panel(); settings(); renderModes();
    }

    static void parse() {
        sec("gps-nav");
        HudFrame f = HudFrame.parse(hud(true, 7));
        ok(f.hasFix() && Math.abs(f.gpsLat - LAT) < 1e-9 && f.gpsBearing == 90.0, "gps block");
        ok(f.navActive && f.navGen == 7 && "slight left".equals(f.navModifier) && f.navDistM == 320, "nav block + maneuver");
        HudFrame n = HudFrame.parse("{\"schemaVersion\":3,\"state\":\"current\",\"gps\":null,\"nav\":{\"active\":false}}");
        ok(!n.hasFix() && !n.navActive && n.navGen == -1, "no fix / nav off");
        ok(!HudFrame.parse("{\"gps\":{\"lat\":95,\"lon\":0}}").hasFix(), "out-of-range lat = no fix");
        RouteGeo r = RouteGeo.parse(route(7));
        ok(r.gen == 7 && r.size() == 3, "route: bad points skipped (" + r.size() + ")");
        ok(r.cong != null && r.cong.length == 2, "congestion per segment");
        boolean threw = false; try { RouteGeo.parse("{\"gen\":1,\"coords\":[[0,0]]}"); } catch (IllegalArgumentException e) { threw = true; }
        ok(threw, "route with < 2 points rejected");
    }

    static void geo() {
        sec("geo");
        ok(Geo.tilesAt(new double[]{85, 180, -85, -180}, 0) == 1 && Geo.tilesAt(new double[]{85, 180, -85, -180}, 2) == 16, "world: 1 tile z0, 16 z2");
        double[] b = Geo.around(LAT, LON, 10000);
        near(Math.round((b[0] - b[2]) * 111.32 * 10) / 10.0, 20.0, "10 km radius -> 20 km tall box");
        long t14 = Geo.tilesAt(b, 14);
        ok(t14 > 60 && t14 < 120, "z14 tiles (2.4 km at the equator) for a 20x20 km box (" + t14 + ")");
        ok(Geo.tiles(b, 5, 14) > t14 && Geo.tiles(b, 5, 12) < t14, "pyramid sums; lower detail = fewer tiles");
        ok(Geo.estimateBytes(b, 5, 14) > Geo.FIXED_BYTES, "size estimate > fixed style/font bytes");
        Geo.Viewport vp = new Geo.Viewport(LAT, LON, 12, 0, 0, 800, 480);
        ok(Math.abs(vp.sx(LON) - 400) < 0.01 && Math.abs(vp.sy(LAT) - 240) < 0.01, "viewport centre");
        ok(Math.abs(vp.lonAt(vp.sx(LON + 0.02)) - (LON + 0.02)) < 1e-6 && Math.abs(vp.latAt(vp.sy(LAT - 0.02)) - (LAT - 0.02)) < 1e-6, "screen <-> geo round trip");
        double[] bb = vp.bounds(100, 50, 700, 430);
        ok(bb[0] > bb[2] && bb[1] > bb[3] && Geo.contains(bb, LAT, LON), "screen rect -> bounds (n > s, e > w)");
        ok(vp.zoomBy(1).zoom == 13 && Math.abs(vp.panBy(100, 0).lon - vp.lonAt(500)) < 1e-12, "zoom / pan");
        near(Math.round(Geo.eastM(LON + 0.001, LAT, LON)), 111, "local metres east");
    }

    static void sources() {
        sec("map-sources");
        ok(MapSources.styleUrl(MapSources.OFM_LIBERTY, null).startsWith("https://") && MapSources.styleUrl(MapSources.CUSTOM, " https://x/s.json ").equals("https://x/s.json"), "style urls (custom trimmed)");
        ok(!MapSources.styleUrl(MapSources.OFM_LIBERTY, null).contains("key="), "no API key in default style URL");
        ok(MapSources.USER_AGENT.startsWith(Brand.APP_NAME) && !MapSources.USER_AGENT.contains("@"), "generic app User-Agent");
        ok(MapSources.MAX_PARALLEL == 4, "<= 4 parallel requests");
        long[] l = MapSources.limits(MapSources.OFM_LIBERTY, 999);
        ok(l[0] == 3000 && l[1] == 50000000L, "free source limits 3,000 tiles / 50 MB");
        double[] small = Geo.around(LAT, LON, 3000), big = Geo.around(LAT, LON, 50000);
        ok(MapSources.refuse(MapSources.OFM_LIBERTY, 500, small, 5, 14) == null, "3 km radius at z14 allowed");
        String why = MapSources.refuse(MapSources.OFM_LIBERTY, 500, big, 5, 14);
        ok(why != null && why.startsWith("Too big"), "100x100 km at z14 refused: " + why);
        ok(MapSources.refuse(MapSources.OFM_LIBERTY, 500, Geo.around(LAT, LON, 90000), 5, 14).contains("3,000"), "tile limit message");
        ok(MapSources.refuse(MapSources.OFM_LIBERTY, 500, big, 5, 10) == null || MapSources.refuse(MapSources.OFM_LIBERTY, 500, big, 5, 11) != null, "lower detail shrinks it");
        ok(MapSources.refuse(MapSources.CUSTOM, 500, big, 5, 14) == null, "custom source: own cap (500 MB)");
        ok(MapSources.refuse(MapSources.CUSTOM, 10, big, 5, 14) != null, "custom cap enforced");
        ok(MapSources.refuse(MapSources.OFM_LIBERTY, 500, null, 5, 14) != null, "no selection refused");
    }

    static void chooser() {
        sec("map-choice");
        int L = MapChooser.STYLE_LOADED, F = MapChooser.STYLE_FAILED, W = MapChooser.STYLE_LOADING;
        ok(MapChooser.choose(true, true, true, L, true, true, true) == MapChooser.Mode.BUILTIN_DEMO, "demo -> demo map");
        ok(MapChooser.choose(false, false, true, W, true, true, false) == MapChooser.Mode.BUILTIN_ROUTE, "lean build + fix -> built-in route");
        ok(MapChooser.choose(false, false, false, W, true, true, false) == MapChooser.Mode.NO_FIX, "lean build, no fix -> waiting for GPS");
        ok(MapChooser.choose(false, true, false, L, true, true, false) == MapChooser.Mode.NO_FIX, "engine, no fix -> waiting for GPS");
        ok(MapChooser.choose(false, true, true, L, true, true, false) == MapChooser.Mode.ENGINE_ONLINE, "online");
        ok(MapChooser.choose(false, true, true, L, true, false, true) == MapChooser.Mode.ENGINE_OFFLINE, "no network, inside offline area -> offline");
        ok(MapChooser.choose(false, true, true, L, false, true, true) == MapChooser.Mode.ENGINE_OFFLINE, "online tiles off -> offline");
        ok(MapChooser.choose(false, true, true, L, true, false, false) == MapChooser.Mode.ENGINE_CACHED, "no network, outside areas, style cached -> cached");
        ok(MapChooser.choose(false, true, true, W, true, false, false) == MapChooser.Mode.BUILTIN_ROUTE, "nothing loaded -> built-in route");
        ok(MapChooser.choose(false, true, true, F, true, true, true) == MapChooser.Mode.BUILTIN_ROUTE, "style failed -> built-in route");
        ok(!MapChooser.engineConnected(false, true) && !MapChooser.engineConnected(true, false) && MapChooser.engineConnected(true, true), "engine network requests only when allowed and up");
    }

    static void model() {
        sec("map-model");
        HudModel m = new HudModel();
        m.onHud(HudFrame.parse(hud(true, 7)), 0);
        m.onRoute(RouteGeo.parse(route(7)), 10);
        ViewState v = m.snapshot(20);
        ok(v.hasFix() && v.bearingDeg == 90 && v.navActive && v.navDistM == 320 && v.route != null && v.route.gen == 7, "fix + nav + route in the view state");
        m.onHud(HudFrame.parse(hud(true, 8)), 300);
        ok(m.snapshot(310).route == null, "route of an older generation is not shown");
        m.onHud(HudFrame.parse("{\"schemaVersion\":3,\"state\":\"stale\",\"onroad\":true,\"gps\":null,\"nav\":null}"), 600);
        ViewState s = m.snapshot(610);
        ok(s.hasFix() && s.navActive, "stale blip keeps the last fix and nav (sticky)");
        OfflineModel om = new OfflineModel();
        OfflineModel.Region r = new OfflineModel.Region(); r.id = 1; r.bounds = Geo.around(LAT, LON, 3000); r.state = OfflineModel.State.DOWNLOADING; r.doneRes = 50; r.needRes = 200; r.bytes = 3000000;
        om.put(r);
        ok(!om.covers(LAT, LON) && om.busy(), "downloading area does not count as coverage");
        r.state = OfflineModel.State.READY; om.put(r);
        ok(om.covers(LAT, LON) && !om.covers(LAT + 1, LON) && !om.busy(), "ready area covers the fix");
        near(om.regions().get(0).progress(), 0.25, "progress");
        OfflineModel.Region r2 = r.copy(); r2.id = 2; r2.bytes = 1000000; om.put(r2);
        ok(om.bytesUsed() == 4000000, "storage used sums areas");
        om.remove(1); ok(om.regions().size() == 1, "delete");
    }

    static void http() throws Exception {
        sec("route-fetch");
        final AtomicInteger routeReq = new AtomicInteger(); final String[] q = {null};
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/api/hud/route", new HttpHandler() { public void handle(HttpExchange x) throws java.io.IOException {
            routeReq.incrementAndGet(); q[0] = x.getRequestURI().getQuery(); send(x, 200, route(7)); }});
        srv.createContext("/api/hud", new HttpHandler() { public void handle(HttpExchange x) throws java.io.IOException { send(x, 200, hud(true, 7)); }});
        srv.start();
        final RouteGeo[] got = {null};
        CommaHttpSource src = new CommaHttpSource("127.0.0.1:" + srv.getAddress().getPort(), null);
        src.start(new DataSource.Listener() {
            public void onHud(HudFrame f, long now) { }
            public void onScene(SceneFrame s, long now) { }
            public void onLink(LinkState st, String d) { }
            public void onRoute(RouteGeo r, long now) { got[0] = r; }
        });
        long until = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < until && src.hudOk < 6) Thread.sleep(50);
        src.stop(); srv.stop(0);
        ok(got[0] != null && got[0].gen == 7 && "gen=7".equals(q[0]), "route fetched for nav gen 7");
        ok(routeReq.get() == 1, "fetched once per generation (" + routeReq.get() + " requests over " + src.hudOk + " polls)");
    }
    static void send(HttpExchange x, int code, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8); x.sendResponseHeaders(code, b.length); OutputStream o = x.getResponseBody(); o.write(b); o.close();
    }

    static void panel() {
        sec("offline-panel");
        for (int[] sz : new int[][]{{800, 480}, {1920, 1080}, {1080, 1920}, {1280, 720}}) {
            float d = sz[0] == 1920 ? 1.5f : sz[0] == 1080 ? 2.625f : 1f;
            Layout L = Layout.compute(sz[0], sz[1], d);
            float[] r = OfflinePanel.viewportRect(L);
            ok(r[2] > L.w * 0.5f || r[3] > L.pxf(200), sz[0] + "x" + sz[1] + ": map viewport is large (" + (int) r[2] + "x" + (int) r[3] + ")");
            OfflinePanel p = new OfflinePanel();
            Geo.Viewport vp = new Geo.Viewport(LAT, LON, 11, r[0], r[1], r[2], r[3]);
            OfflineModel om = new OfflineModel(); om.available = true;
            OfflineModel.Region rg = new OfflineModel.Region(); rg.id = 5; rg.name = "Area"; rg.state = OfflineModel.State.DOWNLOADING; rg.needRes = 10; rg.doneRes = 4; om.put(rg);
            BufferedImage img = new BufferedImage(L.w, L.h, BufferedImage.TYPE_INT_ARGB);
            p.draw(new AwtGfx(img.createGraphics()), L, Theme.DAY, vp, false, null, null, om, true, LAT, LON, false);
            Map<OfflinePanel.Act, Integer> found = new HashMap<OfflinePanel.Act, Integer>();
            for (int y = 0; y < L.h; y += 3) for (int x = 0; x < L.w; x += 3) { OfflinePanel.Hit h = p.hit(x, y); if (h != null) found.put(h.act, 1); }
            boolean all = true;
            for (OfflinePanel.Act a : new OfflinePanel.Act[]{OfflinePanel.Act.DONE, OfflinePanel.Act.MODE_DRAW, OfflinePanel.Act.PRESET_VISIBLE, OfflinePanel.Act.PRESET_10,
                    OfflinePanel.Act.DETAIL, OfflinePanel.Act.PAUSE, OfflinePanel.Act.DELETE}) all &= found.containsKey(a);
            ok(all && !found.containsKey(OfflinePanel.Act.DOWNLOAD), sz[0] + "x" + sz[1] + ": every control reachable; Download hidden with no selection " + found.keySet());
            ok(p.inViewport(r[0] + 5, r[1] + 5) && !p.inViewport(r[0] - 5, r[1] - 5), "viewport hit area");
            p.selectVisible();
            ok(p.selection != null && Geo.contains(p.selection, LAT, LON) && p.refusal() == null, sz[0] + "x" + sz[1] + ": visible area at zoom 11 allowed (" + p.estTiles() + " tiles)");
            p.draw(new AwtGfx(img.createGraphics()), L, Theme.NIGHT, vp, true, null, null, om, true, LAT, LON, false);
            boolean dl = false; for (int y = 0; y < L.h; y += 3) for (int x = 0; x < L.w; x += 3) { OfflinePanel.Hit h = p.hit(x, y); if (h != null && h.act == OfflinePanel.Act.DOWNLOAD) dl = true; }
            ok(dl && (img.getRGB((int) (r[0] + r[2] * 0.75f), (int) (r[1] + r[3] * 0.3f)) >>> 24) == 0, "Download shown; engine mode leaves the viewport transparent");
        }
        OfflinePanel p = new OfflinePanel();
        p.viewport = new Geo.Viewport(LAT, LON, 11, 0, 0, 800, 480);
        p.selectRect(100, 100, 104, 104); ok(p.selection == null, "tiny drag ignored");
        p.selectRect(500, 400, 100, 100); ok(p.selection != null && p.selection[0] > p.selection[2], "drag in any direction");
        p.selectAround(LAT, LON, 50000); p.detail = 2;
        ok(p.refusal() != null, "50 km at street detail refused");
        p.detail = 0; long t12 = p.estTiles(); p.detail = 2;
        ok(t12 < p.estTiles(), "overview detail has fewer tiles");
        OfflinePanel q = new OfflinePanel(); OfflineModel none = new OfflineModel(); q.viewport = p.viewport; q.selectVisible();
        Layout L = Layout.compute(800, 480, 1f);
        BufferedImage img = new BufferedImage(800, 480, BufferedImage.TYPE_INT_ARGB);
        q.draw(new AwtGfx(img.createGraphics()), L, Theme.DAY, new Geo.Viewport(LAT, LON, 11, 10, 56, 450, 400), false, null, null, none, false, Double.NaN, Double.NaN, false);
        boolean dl = false, pre10 = false;
        for (int y = 0; y < 480; y += 3) for (int x = 0; x < 800; x += 3) { OfflinePanel.Hit h = q.hit(x, y); if (h != null && h.act == OfflinePanel.Act.DOWNLOAD) dl = true; if (h != null && h.act == OfflinePanel.Act.PRESET_10) pre10 = true; }
        ok(!dl && !pre10, "no engine: no Download; no fix: no radius presets");
        ok(OfflinePanel.areaName(Geo.around(LAT, LON, 5000), 14).startsWith("10 × 10 km"), "area name " + OfflinePanel.areaName(Geo.around(LAT, LON, 5000), 14));
    }

    static void settings() {
        sec("map-settings");
        Settings s = new Settings();
        ok(s.mapOnline && MapSources.OFM_LIBERTY.equals(s.mapSource) && s.offlineCapMb == 500, "map defaults");
        s.mapOnline = false; s.mapSource = MapSources.CUSTOM; s.mapCustomUrl = "https://example.invalid/style.json"; s.offlineCapMb = 120;
        Settings r = Settings.from(s.toMap());
        ok(!r.mapOnline && MapSources.CUSTOM.equals(r.mapSource) && r.styleUrl().equals("https://example.invalid/style.json") && r.offlineCapMb == 120, "map settings round trip");
        Map<String, String> bad = new HashMap<String, String>(); bad.put(Settings.K_MAP_SOURCE, "evil"); bad.put(Settings.K_OFFLINE_CAP, "x");
        ok(MapSources.OFM_LIBERTY.equals(Settings.from(bad).mapSource) && Settings.from(bad).offlineCapMb == 500, "bad map values fall back");
        Settings c = new Settings();
        ok(SettingsPanel.apply(c, Settings.K_MAP_ONLINE, 0) && !c.mapOnline, "online tiles toggle");
        ok(SettingsPanel.apply(c, Settings.K_MAP_SOURCE, 2) && MapSources.CUSTOM.equals(c.mapSource), "source chip");
        ok(SettingsPanel.coreRows(c).size() == 11, "custom source adds the URL row");
    }

    static void renderModes() {
        sec("map-render");
        HudModel m = new HudModel();
        m.onHud(HudFrame.parse(hud(true, 7)), 0); m.onRoute(RouteGeo.parse(route(7)), 0);
        ViewState v = m.snapshot(10);
        for (MapChooser.Mode mode : MapChooser.Mode.values()) {
            Layout L = Layout.compute(1280, 720, 1f);
            BufferedImage img = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
            new DriveRenderer().draw(new AwtGfx(img.createGraphics()), L, v, Theme.DAY, null, mode, MapSources.attribution(MapSources.OFM_LIBERTY));
            int a = img.getRGB(L.map.cx(), L.map.cy() + L.map.h / 4) >>> 24;
            ok(mode.engine() ? a == 0 : a == 0xFF, mode + ": " + (mode.engine() ? "transparent hole for the live map" : "drawn by the built-in renderer"));
        }
    }
}
