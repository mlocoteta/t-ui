package org.tuiapp.test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.tuiapp.core.*;
import org.tuiapp.host.AwtGfx;

/**
 * Host tests (plain main, no JUnit). Usage: HostTests FIXTURES_DIR REPLAY.jsonl
 * Sections: json/hud parsing, stale + sticky rules, replay source, layout engine, render smoke, HTTP source vs a fake comma.
 */
public final class HostTests {
    static int pass, fail; static String section = "";
    static final List<String> failures = new ArrayList<String>();

    static void ok(boolean c, String what) {
        if (c) pass++; else { fail++; failures.add(section + ": " + what); System.out.println("  FAIL " + what); }
    }
    static void near(double a, double b, String what) { ok(Math.abs(a - b) < 1e-6, what + " (" + a + " vs " + b + ")"); }
    static void sec(String s) { section = s; System.out.println("[" + s + "]"); }
    static String read(File f) throws Exception { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }

    public static void main(String[] a) throws Exception {
        File fx = new File(a[0]); String replay = a[1];
        json(); hudParse(fx); stale(fx); replay(replay); layout(); render(replay); http(fx);
        V02.run(fx, replay, a.length > 2 ? a[2] : null);
        V03.run();
        V04.run();
        System.out.println("\nHOST TESTS: " + pass + " passed, " + fail + " failed");
        for (String f : failures) System.out.println("  - " + f);
        System.exit(fail == 0 ? 0 : 1);
    }

    // ---------------------------------------------------------------- JSON
    static void json() {
        sec("json");
        Map<String, Object> m = Json.parseObject("{\"a\":1.5,\"b\":\"x\\\"y\\u0041\",\"c\":[1,2],\"d\":null,\"e\":true,\"f\":{\"g\":-2e1}}");
        near(Json.num(m, "a"), 1.5, "number");
        ok("x\"yA".equals(Json.str(m, "b")), "string escapes");
        ok(Json.arr(m, "c").size() == 2, "array");
        ok(Json.has(m, "d") && Double.isNaN(Json.num(m, "d")), "null -> NaN");
        ok(Boolean.TRUE.equals(Json.bool(m, "e")), "bool");
        near(Json.num(Json.obj(m, "f"), "g"), -20, "exponent");
        ok(Double.isNaN(Json.num(m, "b")) && Json.str(m, "a") == null, "wrong type -> unknown");
        for (String bad : new String[]{"{", "{\"a\":}", "[1,]x", "{\"a\" 1}", "nul", ""}) {
            boolean threw = false; try { Json.parse(bad); } catch (IllegalArgumentException e) { threw = true; }
            ok(threw, "malformed throws: " + bad);
        }
        float[][] nested = Json.points(Json.parse("[[0,1],[2,3]]")), flat = Json.points(Json.parse("[0,1,2,3]"));
        ok(Arrays.deepEquals(nested, flat), "flat and nested points agree");
        ok(Json.points(Json.parse("[0,1,2]")) == null, "odd flat list rejected");
    }

    // ---------------------------------------------------------------- /api/hud + scene parsing
    static void hudParse(File fx) throws Exception {
        sec("hud-parse");
        HudFrame h = HudFrame.parse(read(new File(fx, "hud-current.json")));
        ok(h.schemaVersion == 3 && h.isCurrent(), "schema 3 current");
        near(h.vEgo, 24.5, "vEgo"); near(h.vCruiseCluster, 29.06, "vCruiseCluster");
        ok(Boolean.TRUE.equals(h.onroad) && Boolean.TRUE.equals(h.engaged) && Boolean.TRUE.equals(h.latActive), "onroad/engaged/latActive");
        ok(h.leadPresent && h.leadTrackId == 5, "lead + trackId"); near(h.leadYRel, 0.4, "lead yRel");
        ok("D".equals(h.gearLetter), "gearLetter");
        ok(h.laneLines.length == 4 && h.laneLines[1].length == 3 && h.roadEdges.length == 2, "model lines/edges");
        near(h.laneLines[1][2][1], -2.3, "lane point y");
        ok(h.tracks.length == 2, "tracks: null dRel skipped (" + h.tracks.length + ")");
        near(h.slcLimit, 29.06, "slc limit");
        ok(!h.hasAlert(), "empty alert = none");
        ok("off".equals(h.laneChangeState), "laneChange");

        HudFrame s = HudFrame.parse(read(new File(fx, "hud-stale.json")));
        ok(s.isStale() && Boolean.TRUE.equals(s.onroad) && Boolean.TRUE.equals(s.engaged), "stale keeps sticky onroad/engaged");
        near(s.onroadAgeSeconds, 1.7, "onroadAgeSeconds");
        ok(s.laneLines == null && !s.leadPresent && s.isMetric == null && Double.isNaN(s.vCruise), "stale nulls stay unknown");

        HudFrame u = HudFrame.parse(read(new File(fx, "hud-unavailable.json")));
        ok("unavailable".equals(u.state) && u.onroad == null && Double.isNaN(u.vEgo), "unavailable");
        ok("unavailable".equals(HudFrame.parse("{\"state\":\"weird\"}").state), "unknown state -> unavailable");

        HudFrame one = HudFrame.parse(read(new File(fx, "hud-schema1.json")));
        ok(one.schemaVersion == 1 && one.laneLines == null && one.leadPresent && Double.isNaN(one.leadYRel), "schema 1: no model, lead without yRel");
        ok("critical".equals(one.alertStatus) && "Steering Unavailable".equals(one.alertText1), "schema 1 alert");
        ok(Boolean.TRUE.equals(one.isMetric), "isMetric");

        String[] lines = read(new File(fx, "scene.ndjson")).split("\n");
        SceneFrame f0 = SceneFrame.parse(lines[0]);
        ok(f0.seq == 101 && f0.hasModel && f0.laneLines.length == 4 && f0.laneLines[0].length == 2, "scene frame model");
        ok(f0.leadPresent && f0.leadTrackId == 5 && f0.hasLeadKey, "scene lead"); near(f0.yawRate, 0.012, "yaw rate");
        ok(SceneFrame.parse(lines[1]) == SceneFrame.KEEPALIVE, "keep-alive");
        SceneFrame f2 = SceneFrame.parse(lines[2]);
        ok(!f2.hasModel && f2.hasLeadKey && !f2.leadPresent && Double.isNaN(f2.yawRate), "scene nulls = unknown / no lead");
    }

    // ---------------------------------------------------------------- stale / sticky rules
    static void stale(File fx) throws Exception {
        sec("stale");
        ok(!HoldRule.grey(1000, -1, -1, 2000, 1000), "never seen: not grey");
        ok(!HoldRule.grey(1999, 0, -1, 2000, 1000) && HoldRule.grey(2000, 0, -1, 2000, 1000), "grey at 2 s without scene");
        ok(!HoldRule.grey(2500, 0, 1600, 2000, 1000) && HoldRule.grey(2600, 0, 1600, 2000, 1000), "scene frame defers grey by 1 s");
        ok(HoldRule.sceneUsable(2000, 0, -1, 2000) && !HoldRule.sceneUsable(2001, 0, -1, 2000), "scene usable while base <= 2 s");
        ok(HoldRule.sceneUsable(30000, 0, 29000, 2000) && !HoldRule.sceneUsable(30000, 0, 28000, 2000), "live stream extends base to 60 s");
        ok(!HoldRule.sceneUsable(61000, 0, 60900, 2000) && !HoldRule.sceneUsable(10, -1, 5, 2000), "base > 60 s or none: unusable");
        long[] at = {-1};
        ok(HoldRule.drivingStep(true, 0, at), "onroad true");
        ok(HoldRule.drivingStep(null, 9000, at) && HoldRule.drivingStep(null, 10000, at), "null holds the drive 10 s");
        ok(!HoldRule.drivingStep(null, 10001, at), "null beyond 10 s ends it");
        HoldRule.drivingStep(true, 20000, at);
        ok(!HoldRule.drivingStep(false, 20100, at) && !HoldRule.drivingStep(null, 20200, at), "explicit false ends at once");

        String cur = read(new File(fx, "hud-current.json")), st = read(new File(fx, "hud-stale.json"));
        HudModel m = new HudModel(); m.onLink(LinkState.LIVE, "");
        m.onHud(HudFrame.parse(cur), 0);
        ViewState v = m.snapshot(100);
        ok(!v.grey && !v.stale && v.driving && v.engaged && "Engaged".equals(v.chip()), "current: live engaged");
        HudFrame sf = HudFrame.parse(st);
        m.onHud(sf, 250);
        ok(sf.laneLines == null, "model does not edit the caller's frame");
        v = m.snapshot(1000);
        ok(v.stale && !v.grey, "stale blip: sticky, not grey yet");
        ok(v.laneLines != null && v.leadPresent && v.tracks != null, "stale keeps last geometry + lead (sticky merge)");
        near(v.speedMps, 24.1, "stale shows the comma's sticky vEgo");
        ok(v.dataAgeS >= 1.7, "data age >= onroadAgeSeconds (" + v.dataAgeS + ")");
        ok(m.snapshot(2100).grey, "grey after 2 s without current hud or scene");
        m.onScene(SceneFrame.parse("{\"s\":5,\"a\":0.02,\"v\":24.0,\"yr\":0,\"c\":0,\"m\":null,\"l\":null,\"lc\":\"off\",\"ld\":\"none\"}"), 1900);
        ok(!m.snapshot(2100).grey, "a scene frame within 1 s defers grey");
        ok(m.snapshot(3000).grey, "grey again when the stream stops too");
        m.onHud(HudFrame.parse(cur), 3100);
        ok(!m.snapshot(3200).grey && !m.snapshot(3200).stale, "current again clears grey/stale");

        // scene merge
        HudModel s = new HudModel(); s.onLink(LinkState.LIVE, "");
        s.onHud(HudFrame.parse(cur), 0);
        s.onScene(SceneFrame.parse(read(new File(fx, "scene.ndjson")).split("\n")[0]), 100);
        v = s.snapshot(150);
        near(v.speedMps, 25.0, "scene vEgo wins"); near(v.leadDRel, 30.0, "scene lead wins");
        ok(v.laneLines[0].length == 2, "scene lines replace hud lines");
        s.onScene(SceneFrame.parse("{\"s\":6,\"v\":25.2,\"m\":null,\"l\":null}"), 200);
        v = s.snapshot(250);
        ok(v.laneLines[0].length == 3 && !v.leadPresent, "scene m=null keeps hud lines; l=null = no lead");

        // driving hold through null onroad; explicit false parks
        HudModel d = new HudModel(); d.onLink(LinkState.LIVE, "");
        d.onHud(HudFrame.parse(cur), 0);
        d.onHud(HudFrame.parse(read(new File(fx, "hud-unavailable.json"))), 500);
        ok(d.snapshot(9000).driving && !d.snapshot(10500).driving, "unavailable (onroad null) holds driving 10 s");
        d.onHud(HudFrame.parse(cur.replace("\"onroad\":true", "\"onroad\":false")), 11000);
        ok("Parked".equals(d.snapshot(11100).chip()), "onroad false -> Parked");
        HudModel nu = new HudModel(); nu.onLink(LinkState.LIVE, "");
        nu.onHud(HudFrame.parse(read(new File(fx, "hud-unavailable.json"))), 0);
        ok(nu.snapshot(10).grey, "only unavailable frames ever: grey");
        HudModel none = new HudModel();
        ok("Connecting".equals(none.snapshot(0).chip()) && !none.snapshot(0).grey, "no data yet: Connecting, not grey");
    }

    // ---------------------------------------------------------------- replay source
    static ReplaySource load(String replay, boolean loop, DataSource.Clock c) throws Exception {
        return ReplaySource.read(new InputStreamReader(new FileInputStream(replay), "UTF-8"), loop, c);
    }
    static void replay(String replay) throws Exception {
        sec("replay");
        ReplaySource r = load(replay, false, null);
        ok(r.size() > 500 && r.durationMs() >= 60000, "synthetic replay loaded (" + r.size() + " frames, " + r.durationMs() + " ms)");
        HudModel m = new HudModel(); m.setDemo(true);
        r.runTo(3000, 0, m);
        ViewState v = m.snapshot(3000);
        ok("Parked".equals(v.chip()) || !v.driving, "3 s: parked"); ok("P".equals(v.gear), "gear P");
        r.runTo(30000, 0, m); v = m.snapshot(30000);
        ok(v.driving && v.engaged && v.leadPresent && v.laneLines != null && v.laneLines.length == 4, "30 s: engaged, lead, lanes");
        ok(v.tracks != null && v.tracks.length >= 2, "30 s: other cars");
        ok(Math.abs(v.speedMps - 27.0) < 0.5, "30 s: speed ~27 m/s");
        r.runTo(47000, 0, m); v = m.snapshot(47000);
        ok("laneChangeStarting".equals(v.laneChangeState) && "right".equals(v.laneChangeDir), "47 s: lane change right");
        r.runTo(53900, 0, m); v = m.snapshot(53900);
        ok(v.grey && v.driving && v.engaged, "53.9 s: blip greys the scene, drive held, engaged sticky");
        r.runTo(54500, 0, m); ok(!m.snapshot(54500).grey, "54.5 s: recovered");
        r.runTo(57000, 0, m); v = m.snapshot(57000);
        ok(v.alertCritical() && !v.engaged, "57 s: takeover alert");
        ok(m.frames() > 200 && m.sceneFrames() > 300, "frames delivered (" + m.frames() + " hud, " + m.sceneFrames() + " scene)");

        // real-time player with a 300x clock: loops, delivers more than one pass
        final long t0 = System.nanoTime();
        ReplaySource fast = load(replay, true, new DataSource.Clock() { public long now() { return (System.nanoTime() - t0) / 1000000L * 300; } });
        final AtomicInteger hud = new AtomicInteger(); final LinkState[] link = {null};
        fast.start(new DataSource.Listener() {
            public void onHud(HudFrame f, long now) { hud.incrementAndGet(); }
            public void onScene(SceneFrame s, long now) { }
            public void onLink(LinkState st, String d) { link[0] = st; }
        });
        Thread.sleep(700); fast.stop();
        ok(link[0] == LinkState.REPLAY, "replay link state");
        ok(hud.get() > 248, "looping real-time replay (" + hud.get() + " hud frames in 0.7 s at 300x)");
    }

    // ---------------------------------------------------------------- layout engine
    static final Object[][] SIZES = {
            {800, 480, 1.0f, Layout.Mode.FLOATING}, {1024, 600, 1.0f, Layout.Mode.SPLIT}, {1280, 720, 1.0f, Layout.Mode.SPLIT},
            {1280, 720, 2.0f, Layout.Mode.FLOATING}, {1920, 1080, 1.5f, Layout.Mode.SPLIT}, {1080, 1920, 2.625f, Layout.Mode.SPLIT},
            {2400, 1080, 2.0f, Layout.Mode.SPLIT}, {1920, 720, 1.0f, Layout.Mode.SPLIT}, {768, 1024, 1.0f, Layout.Mode.SPLIT},
            {480, 800, 1.5f, Layout.Mode.SPLIT}};

    static void layout() {
        sec("layout");
        for (Object[] s : SIZES) {
            int w = (Integer) s[0], h = (Integer) s[1]; float d = (Float) s[2];
            Layout auto = Layout.compute(w, h, d);
            ok(auto.mode == s[3], w + "x" + h + "@" + d + " AUTO -> " + s[3] + " (got " + auto.mode + ")");
            for (Layout.Mode mode : new Layout.Mode[]{Layout.Mode.FLOATING, Layout.Mode.SPLIT})
                for (boolean left : new boolean[]{true, false}) checkLayout(Layout.compute(w, h, d, mode, left));
        }
        Layout uw = Layout.compute(2400, 1080, 2.0f);
        ok(Math.abs(uw.scene.w - 0.42 * 2400) < 2, "ultrawide: scene pane 42 %");
        Layout p = Layout.compute(1080, 1920, 2.625f);
        ok(p.portrait && p.scene.y == 0 && p.map.y == p.scene.b() && p.scene.w == 1080, "portrait: scene on top, map below");
        ok(p.bar.h == p.px(56) && p.bar.b() == 1920 && p.map.b() == p.bar.y, "portrait (L3): 56 dp bottom bar under the map");
        ok(Layout.compute(1280, 720, 1f).bar.h == 0 && Layout.compute(800, 480, 1f).bar.h == 0, "landscape: no bottom bar");
        Layout rhd = Layout.compute(1280, 720, 1f, Layout.Mode.SPLIT, false);
        ok(rhd.scene.x > rhd.map.x, "right-hand drive: scene on the right");
        Layout small = Layout.compute(800, 480, 1f);
        near(small.u, 1.0, "800x480@1: u = 1");
        ok(Layout.compute(1920, 1080, 1.0f).u > Layout.compute(800, 480, 1.0f).u, "bigger short side -> bigger cards");
        boolean threw = false; try { Layout.compute(0, 480, 1f); } catch (IllegalArgumentException e) { threw = true; }
        ok(threw, "zero size rejected");
    }

    static void checkLayout(Layout L) {
        String n = L.toString();
        Layout.R[] cards = L.cards();
        boolean inside = true, overlap = false;
        for (Layout.R c : cards) inside &= c.inside(L.scene) && c.inside(L.screen) && c.w > 0 && c.h > 0;
        for (int i = 0; i < cards.length; i++) for (int j = i + 1; j < cards.length; j++) overlap |= cards[i].intersects(cards[j]);
        ok(inside, "cards inside scene: " + n);
        ok(!overlap, "cards do not overlap: " + n);
        if (L.mapInset) {
            boolean mo = false; for (Layout.R c : cards) mo |= c.intersects(L.map);
            ok(!mo && L.map.inside(L.screen), "corner map clear of cards: " + n);
        } else {
            ok(!L.scene.intersects(L.map) && !L.map.intersects(L.bar) && (long) L.scene.w * L.scene.h + (long) L.map.w * L.map.h + (long) L.bar.w * L.bar.h == (long) L.w * L.h, "panes tile the screen: " + n);
        }
        ok(L.speed.w / L.density >= 120 && L.alert.h / L.density >= 56, "card sizes >= 120 dp / touch 56 dp: " + n);
        ok(L.speed.w <= L.scene.w / 2 + 1, "speed card <= half the scene width: " + n);
    }

    // ---------------------------------------------------------------- render smoke (every profile, both themes)
    static void render(String replay) throws Exception {
        sec("render");
        ReplaySource r = load(replay, false, null);
        HudModel m = new HudModel(); m.setDemo(true);
        long[] ts = {1000, 30000, 47000, 53900, 57000};
        int ok = 0;
        for (long t : ts) {
            r.runTo(t, 0, m);
            ViewState v = m.snapshot(t);
            for (Object[] s : SIZES) {
                int w = (Integer) s[0], h = (Integer) s[1];
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                new DriveRenderer().draw(new AwtGfx(img.createGraphics()), Layout.compute(w, h, (Float) s[2]), v, t % 2 == 0 ? Theme.DAY : Theme.NIGHT);
                int center = img.getRGB(w / 2, (int) (h * 0.9));
                if ((center >>> 24) == 0xFF) ok++;
            }
        }
        ok(ok == ts.length * SIZES.length, "rendered " + ok + "/" + ts.length * SIZES.length + " frames, opaque");
        ViewState empty = new HudModel().snapshot(0);
        BufferedImage img = new BufferedImage(800, 480, BufferedImage.TYPE_INT_ARGB);
        new DriveRenderer().draw(new AwtGfx(img.createGraphics()), Layout.compute(800, 480, 1f), empty, Theme.DAY);
        ok(true, "renders with no data at all");
    }

    // ---------------------------------------------------------------- HTTP source vs a fake comma (loopback only)
    static void http(File fx) throws Exception {
        sec("http");
        final String hud = read(new File(fx, "hud-current.json")).replace("\n", "");
        final String scene = read(new File(fx, "scene.ndjson"));
        final AtomicInteger sessions = new AtomicInteger(), sceneReq = new AtomicInteger();
        final boolean[] sceneOn = {true};
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/api/auth/session", new HttpHandler() { public void handle(HttpExchange x) throws java.io.IOException {
            sessions.incrementAndGet(); x.getResponseHeaders().add("Set-Cookie", "galaxy_session=tok123; Path=/; HttpOnly"); send(x, 200, "{}");
        }});
        srv.createContext("/api/hud/scene", new HttpHandler() { public void handle(HttpExchange x) throws java.io.IOException {
            sceneReq.incrementAndGet();
            if (!authed(x)) { send(x, 401, "{}"); return; }
            if (!sceneOn[0]) { send(x, 404, "{}"); return; }
            ok(x.getRequestURI().getQuery().equals("hz=10"), "scene asks hz=10");
            send(x, 200, scene);
        }});
        srv.createContext("/api/hud", new HttpHandler() { public void handle(HttpExchange x) throws java.io.IOException {
            if (!authed(x)) { send(x, 401, "{\"error\":\"auth\"}"); return; }
            send(x, 200, hud);
        }});
        srv.start();
        int port = srv.getAddress().getPort();
        final HudModel model = new HudModel();
        final AtomicInteger scenes = new AtomicInteger(); final List<LinkState> links = new ArrayList<LinkState>();
        CommaHttpSource src = new CommaHttpSource("127.0.0.1:" + port, null);
        src.start(new DataSource.Listener() {
            public void onHud(HudFrame f, long now) { model.onHud(f, now); }
            public void onScene(SceneFrame s, long now) { if (s != SceneFrame.KEEPALIVE) scenes.incrementAndGet(); model.onScene(s, now); }
            public void onLink(LinkState st, String d) { synchronized (links) { links.add(st); } model.onLink(st, d); }
        });
        long until = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < until && (src.hudOk < 6 || scenes.get() < 2)) Thread.sleep(50);
        ok(src.http401 >= 1 && sessions.get() >= 1, "401 -> /api/auth/session login (" + src.http401 + " x 401, " + sessions.get() + " logins)");
        ok(src.hudOk >= 6, "polls /api/hud ~4 Hz (" + src.hudOk + " ok)");
        ok(scenes.get() >= 2, "scene stream frames (" + scenes.get() + ")");
        ViewState v = model.snapshot(DataSource.SYSTEM.now());
        ok(v.link == LinkState.LIVE && v.engaged && !v.grey, "live + engaged via HTTP");
        sceneOn[0] = false; int before = sceneReq.get(); Thread.sleep(600);
        ok(src.hudOk > 6, "404 on scene: polling continues");
        int after404 = sceneReq.get(); Thread.sleep(800);
        ok(sceneReq.get() - after404 <= 1, "404: no tight scene retry loop (" + (sceneReq.get() - after404) + " in 0.8 s)");
        srv.stop(0);
        until = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < until && model.snapshot(DataSource.SYSTEM.now()).link == LinkState.LIVE) Thread.sleep(100);
        v = model.snapshot(DataSource.SYSTEM.now());
        ok(v.link == LinkState.NO_COMMA && "No comma".equals(v.chip()), "server gone -> No comma (" + v.link + ")");
        src.stop();
        ok(before >= 1, "scene endpoint was used");
        boolean threw = false; try { new CommaHttpSource("  ", null); } catch (IllegalArgumentException e) { threw = true; }
        ok(threw, "empty address rejected");
        Map<String, List<String>> hdr = new HashMap<String, List<String>>();
        hdr.put("set-cookie", Arrays.asList("other=1", "galaxy_session=zz; Path=/"));
        ok("zz".equals(cookieOf(hdr)), "Set-Cookie parse (case-insensitive)");
    }
    static String cookieOf(Map<String, List<String>> h) throws Exception {
        java.lang.reflect.Method mm = CommaHttpSource.class.getDeclaredMethod("sessionCookie", Map.class);
        mm.setAccessible(true); return (String) mm.invoke(null, h);
    }
    static boolean authed(HttpExchange x) {
        String c = x.getRequestHeaders().getFirst("Cookie"); return c != null && c.contains("galaxy_session=tok123");
    }
    static void send(HttpExchange x, int code, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        x.sendResponseHeaders(code, b.length); OutputStream o = x.getResponseBody(); o.write(b); o.close();
    }
}
