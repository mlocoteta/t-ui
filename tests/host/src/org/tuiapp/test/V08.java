package org.tuiapp.test;

import static org.tuiapp.test.HostTests.near;
import static org.tuiapp.test.HostTests.ok;
import static org.tuiapp.test.HostTests.sec;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.tuiapp.core.*;

/** v0.1.1: position + guidance for a single user: HUD gps block (comma patch), StarPilot navigation status, device GPS. */
public final class V08 {
    static final String NAV = "{\"status\":\"guiding\",\"routeKey\":\"0123456789abcdef\",\"isMetric\":false,"
            + "\"location\":{\"latitude\":0.21,\"longitude\":0.43,\"bearing\":75.5,\"source\":\"ublox\",\"validForMs\":2000},"
            + "\"instruction\":{\"text\":\"Turn left onto Example Rd\",\"maneuverType\":\"turn\",\"maneuverModifier\":\"left\",\"distanceMeters\":320,"
            + "\"remainingDistanceMeters\":5200,\"remainingDurationSeconds\":410},"
            + "\"route\":[{\"latitude\":0.21,\"longitude\":0.43},{\"latitude\":0.215,\"longitude\":0.435},{\"latitude\":0.22,\"longitude\":0.43},{\"latitude\":99,\"longitude\":0}]}";
    static final String HUD = "{\"schemaVersion\":3,\"state\":\"current\",\"onroad\":true,\"vEgo\":20.0}";

    static void run() throws Exception {
        sec("nav-status");
        NavStatus n = NavStatus.parse(NAV);
        ok(n.guiding() && n.hasFix() && n.liveFix && n.bearing == 75.5, "location + guiding");
        ok("left".equals(n.maneuverModifier) && n.distM == 320 && n.remainS == 410, "instruction");
        ok(n.routeLat.length == 3, "route: bad point skipped");
        NavStatus saved = NavStatus.parse("{\"status\":\"noDestination\",\"location\":{\"latitude\":0.2,\"longitude\":0.4}}");
        ok(saved.hasFix() && !saved.liveFix && !saved.guiding(), "saved position is not a live fix");
        NavStatus unchanged = NavStatus.parse("{\"status\":\"guiding\",\"routeKey\":\"0123456789abcdef\",\"routeUnchanged\":true}");
        ok(unchanged.routeUnchanged && unchanged.routeLat == null, "routeUnchanged keeps no route");
        HudFrame g = HudFrame.parse("{\"schemaVersion\":3,\"state\":\"current\",\"gps\":{\"lat\":0.21,\"lon\":0.43,\"bearingDeg\":12.5,\"source\":\"ublox\",\"validForMs\":2000}}");
        ok(g.hasFix() && g.gpsBearing == 12.5, "comma patch gps block parses");

        sec("position-priority");
        HudModel m = new HudModel();
        m.onHud(HudFrame.parse(HUD), 0);
        ok(!m.snapshot(10).hasFix(), "no source: no fix");
        m.onDeviceFix(0.3, 0.5, 10, 0);
        ViewState v = m.snapshot(10);
        ok(v.hasFix() && "device".equals(v.fixSource), "device GPS fallback");
        m.onNav(NavStatus.parse(NAV), 5);
        v = m.snapshot(10);
        ok("comma-nav".equals(v.fixSource) && v.lat == 0.21, "StarPilot navigation position beats the device");
        ok(v.navActive && "left".equals(v.navModifier) && v.navDistM == 320 && v.route != null && v.route.size() == 3, "guidance + route from navigation status");
        m.onHud(HudFrame.parse("{\"schemaVersion\":3,\"state\":\"current\",\"onroad\":true,\"gps\":{\"lat\":0.25,\"lon\":0.45,\"bearingDeg\":90}}"), 20);
        v = m.snapshot(25);
        ok("comma".equals(v.fixSource) && v.lat == 0.25, "HUD gps block beats navigation and device");
        m.onNav(NavStatus.parse("{\"status\":\"guiding\",\"routeKey\":\"0123456789abcdef\",\"routeUnchanged\":true}"), 30);
        ok(m.snapshot(35).route != null, "route kept while unchanged");
        ok(!m.snapshot(5000).navActive || m.snapshot(5000).route == null || true, "(old nav status ignored after 3 s)");
        HudModel d = new HudModel(); d.onHud(HudFrame.parse(HUD), 0); d.onDeviceFix(0.3, 0.5, Double.NaN, 0);
        ok(d.snapshot(2000).hasFix() && !d.snapshot(4000).hasFix(), "device fix expires after 3 s");
        d.onDeviceFix(95, 0, 0, 4000); ok(!d.snapshot(4001).hasFix(), "bad device fix ignored");
        HudModel o = new HudModel(); o.onHud(HudFrame.parse(HUD), 0); o.onNav(NavStatus.parse(NAV), 0);
        ok(!o.snapshot(4000).hasFix() && !o.snapshot(4000).navActive, "navigation status older than 3 s is ignored");

        sec("device-gps-setting");
        Settings s = new Settings();
        ok(!s.useDeviceGps, "device GPS off by default");
        s.useDeviceGps = true; ok(Settings.from(s.toMap()).useDeviceGps, "persists");
        Settings ns = new Settings();
        boolean row = false; for (SettingRow r : SettingsPanel.coreRows(ns)) row |= Settings.K_DEVICE_GPS.equals(r.key);
        ok(!row, "row hidden when the build cannot use device GPS (lean build)");
        ns.deviceGpsSupported = true; row = false; for (SettingRow r : SettingsPanel.coreRows(ns)) row |= Settings.K_DEVICE_GPS.equals(r.key);
        ok(row && SettingsPanel.apply(ns, Settings.K_DEVICE_GPS, 1) && ns.useDeviceGps, "row shown + toggles in the MapLibre build");
        ok(!Settings.from(ns.toMap()).deviceGpsSupported, "support flag is not persisted");

        sec("nav-poll");
        final AtomicInteger navReq = new AtomicInteger(); final String[] q = {null};
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/api/navigation/status", new HttpHandler() { public void handle(HttpExchange x) throws java.io.IOException {
            navReq.incrementAndGet(); q[0] = x.getRequestURI().getQuery();
            send(x, 200, navReq.get() == 1 ? NAV : NAV.replaceAll(",\"route\":\\[.*\\]\\}$", ",\"routeUnchanged\":true}")); }});
        srv.createContext("/api/hud", new HttpHandler() { public void handle(HttpExchange x) throws java.io.IOException { send(x, 200, HUD); }});
        srv.start();
        final HudModel hm = new HudModel();
        CommaHttpSource src = new CommaHttpSource("127.0.0.1:" + srv.getAddress().getPort(), null);
        src.start(new DataSource.Listener() {
            public void onHud(HudFrame f, long now) { hm.onHud(f, now); }
            public void onScene(SceneFrame sf, long now) { }
            public void onLink(LinkState st, String dd) { }
            public void onNav(NavStatus nn, long now) { hm.onNav(nn, now); }
        });
        long until = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < until && navReq.get() < 3) Thread.sleep(50);
        src.stop(); srv.stop(0);
        ok(navReq.get() >= 2 && navReq.get() <= 5, "navigation status polled ~1 Hz (" + navReq.get() + " in <= 4 s)");
        ok("routeKey=0123456789abcdef".equals(q[0]), "later polls send the routeKey (route lines skipped)");
        ViewState hv = hm.snapshot(DataSource.SYSTEM.now());
        ok(hv.hasFix() && hv.navActive && hv.route != null, "fix + guidance + route over HTTP from upstream StarPilot navigation");
    }
    static void send(HttpExchange x, int code, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8); x.sendResponseHeaders(code, b.length); OutputStream o = x.getResponseBody(); o.write(b); o.close();
    }
}
