package org.tuiapp.core;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Reads a StarPilot comma over its Galaxy web server (default port 8082), read-only:
 *  - polls GET /api/hud every POLL_MS (~4 Hz, the comma samples at 4 Hz);
 *  - while driving, keeps GET /api/hud/scene?hz=SCENE_HZ open (NDJSON, ~10 Hz; the server closes after ~60 s,
 *    we reconnect; 404 = older build without the stream: poll only; 429 = two streams already open: retry later).
 * Auth: on the comma's own hotspot Galaxy treats the client as local. If any request answers 401, we fetch
 * GET /api/auth/session once, keep its galaxy_session cookie and renew it before COOKIE_REFRESH_MS.
 * The address is a user setting; nothing is discovered automatically in v0.
 */
public final class CommaHttpSource implements DataSource {
    public static final long POLL_MS = 250, COOKIE_REFRESH_MS = 4 * 60 * 1000L, SCENE_RETRY_MS = 3000, SCENE_404_RETRY_MS = 60000;
    public static final int SCENE_HZ = 10, CONNECT_TIMEOUT_MS = 1500, READ_TIMEOUT_MS = 2500;
    public static final int DEFAULT_PORT = 8082;

    private final String base;     // http://host:port
    private final Clock clock;
    private volatile boolean running;
    private volatile boolean driving;
    private volatile String cookie;
    private volatile long cookieAt;
    private volatile boolean relogin;
    private Thread pollT, sceneT;
    private volatile HttpURLConnection sceneConn;
    public volatile int hudOk, hudFail, sceneLines, http401;

    /** @param hostPort "host" or "host:port" (no scheme); */
    public CommaHttpSource(String hostPort, Clock clock) {
        String hp = hostPort.trim();
        if (hp.startsWith("http://")) hp = hp.substring(7);
        while (hp.endsWith("/")) hp = hp.substring(0, hp.length() - 1);
        if (hp.isEmpty()) throw new IllegalArgumentException("empty comma address");
        boolean hasPort = hp.lastIndexOf(':') > 0 && !hp.endsWith("]");
        this.base = "http://" + (hasPort ? hp : hp + ":" + DEFAULT_PORT);
        this.clock = clock == null ? SYSTEM : clock;
    }

    @Override public String describe() { return "comma " + base; }

    @Override public synchronized void start(final Listener l) {
        if (running) return;
        running = true;
        l.onLink(LinkState.SEARCHING, base);
        pollT = new Thread(new Runnable() { public void run() { pollLoop(l); } }, "tui-hud");
        sceneT = new Thread(new Runnable() { public void run() { sceneLoop(l); } }, "tui-scene");
        pollT.setDaemon(true); sceneT.setDaemon(true);
        pollT.start(); sceneT.start();
    }

    @Override public synchronized void stop() {
        running = false;
        HttpURLConnection c = sceneConn; if (c != null) c.disconnect();
        if (pollT != null) pollT.interrupt();
        if (sceneT != null) sceneT.interrupt();
    }

    // ------------------------------------------------------------------ /api/hud polling
    private void pollLoop(Listener l) {
        int fails = 0; LinkState shown = LinkState.SEARCHING;
        while (running) {
            long t0 = clock.now();
            try {
                ensureCookie();
                Resp r = get("/api/hud", CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
                if (r.code == 401) { http401++; relogin = true; cookie = null; sleep(http401 <= 1 ? 50 : 1000); continue; }
                if (r.code != 200) throw new IOException("http " + r.code);
                HudFrame f = HudFrame.parse(r.body);
                hudOk++; fails = 0;
                driving = Boolean.TRUE.equals(f.onroad) || (f.onroad == null && driving);
                if (shown != LinkState.LIVE) { shown = LinkState.LIVE; l.onLink(shown, base); }
                l.onHud(f, clock.now());
                maybeFetchRoute(f, l);
                maybePollNav(l);
            } catch (Exception e) {
                hudFail++; fails++;
                LinkState st = e instanceof ConnectException || e instanceof SocketTimeoutException || e instanceof NoRouteToHostException
                        ? LinkState.NO_COMMA : (e instanceof IOException ? LinkState.NO_COMMA : LinkState.SEARCHING);
                if (fails >= 3 && shown != st) { shown = st; l.onLink(st, base + ": " + e.getClass().getSimpleName() + " " + e.getMessage()); }
            }
            long wait = fails == 0 ? POLL_MS - (clock.now() - t0) : Math.min(2000, 250L * fails);
            sleep(Math.max(20, wait));
        }
    }

    // ------------------------------------------------------------------ route geometry (once per nav generation)
    // ------------------------------------------------------------------ StarPilot navigation status (upstream Galaxy)
    public static final long NAV_POLL_MS = 1000;
    private long navAt = -1, navOffUntil = -1; private String navRouteKey = "";
    public volatile int navPolls;
    private void maybePollNav(Listener l) {
        long now = clock.now();
        if ((navAt >= 0 && now - navAt < NAV_POLL_MS) || now < navOffUntil) return;
        navAt = now;
        try {
            Resp r = get("/api/navigation/status" + (navRouteKey.isEmpty() ? "" : "?routeKey=" + navRouteKey), CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
            if (r.code == 404) { navOffUntil = now + 60000; return; }   // a build without StarPilot navigation
            if (r.code != 200) return;
            NavStatus n = NavStatus.parse(r.body);
            if (n.routeKey.matches("[0-9a-f]{16}")) navRouteKey = n.routeKey;
            navPolls++;
            l.onNav(n, clock.now());
        } catch (Exception ignored) { }
    }

    private int routeGen = -1; private long routeTryAt = -1;
    public volatile int routesFetched;
    private void maybeFetchRoute(HudFrame f, Listener l) {
        if (!f.navActive || f.navGen < 0 || f.navGen == routeGen) return;
        long now = clock.now();
        if (routeTryAt >= 0 && now - routeTryAt < 10000) return;   // one try per 10 s per generation
        routeTryAt = now;
        try {
            Resp r = get("/api/hud/route?gen=" + f.navGen, CONNECT_TIMEOUT_MS, 5000);
            if (r.code != 200) return;
            RouteGeo g = RouteGeo.parse(r.body);
            routeGen = g.gen; routeTryAt = -1; routesFetched++;
            l.onRoute(g, clock.now());
        } catch (Exception ignored) { }
    }

    private void ensureCookie() throws IOException {
        boolean need = relogin || (cookie != null && clock.now() - cookieAt > COOKIE_REFRESH_MS);
        if (!need) return;
        relogin = false; cookieAt = clock.now();   // one try per window even if it fails
        Resp a = get("/api/auth/session", CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
        if (a.code == 200 && a.cookie != null) cookie = a.cookie;
    }

    // ------------------------------------------------------------------ /api/hud/scene stream
    private void sceneLoop(Listener l) {
        while (running) {
            if (!driving) { sleep(500); continue; }
            long retry = SCENE_RETRY_MS;
            HttpURLConnection c = null;
            try {
                c = open("/api/hud/scene?hz=" + SCENE_HZ, CONNECT_TIMEOUT_MS, 5000);
                c.setRequestProperty("Accept", "application/x-ndjson");
                sceneConn = c;
                int code = c.getResponseCode();
                if (code == 401) { http401++; relogin = true; retry = 1000; }
                else if (code == 404) retry = SCENE_404_RETRY_MS;
                else if (code == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                    String line;
                    while (running && driving && (line = br.readLine()) != null) {
                        if (line.trim().isEmpty()) continue;
                        try { SceneFrame s = SceneFrame.parse(line); sceneLines++; l.onScene(s, clock.now()); }
                        catch (IllegalArgumentException bad) { /* skip a malformed line */ }
                    }
                    retry = 50;   // server closes every ~60 s: reconnect right away
                }
            } catch (IOException e) {
                retry = SCENE_RETRY_MS;
            } finally {
                if (c != null) c.disconnect();
                sceneConn = null;
            }
            sleep(retry);
        }
    }

    // ------------------------------------------------------------------ writes (off unless an add-on build enables them)
    private volatile boolean writesEnabled;
    /** Only an add-on build calls this, after the user turned writes on for that add-on. Default: false. */
    public void setWritesEnabled(boolean on) { writesEnabled = on; }
    public boolean writesEnabled() { return writesEnabled; }

    /** POST a JSON body (blocking; call off the UI thread). Returns {status, body}; throws when writes are disabled. */
    public String[] post(String path, String json) throws IOException {
        if (!writesEnabled) throw new IllegalStateException("writes are disabled (read-only display)");
        if (!path.startsWith("/api/")) throw new IllegalArgumentException("path");
        HttpURLConnection c = open(path, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
        try {
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            byte[] b = json.getBytes("UTF-8");
            c.getOutputStream().write(b); c.getOutputStream().close();
            int code = c.getResponseCode();
            if (code == 401) { relogin = true; http401++; }
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = "";
            if (in != null) { ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] buf = new byte[2048]; int n; while ((n = in.read(buf)) > 0 && bo.size() < 65536) bo.write(buf, 0, n); in.close(); body = bo.toString("UTF-8"); }
            return new String[]{String.valueOf(code), body};
        } finally { c.disconnect(); }
    }

    // ------------------------------------------------------------------ HTTP helpers
    private static final class Resp { int code; String body = ""; String cookie; }

    private HttpURLConnection open(String path, int cto, int rto) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setConnectTimeout(cto); c.setReadTimeout(rto); c.setUseCaches(false);
        c.setRequestProperty("User-Agent", Brand.USER_AGENT);
        String ck = cookie; if (ck != null) c.setRequestProperty("Cookie", "galaxy_session=" + ck);
        return c;
    }

    private Resp get(String path, int cto, int rto) throws IOException {
        HttpURLConnection c = open(path, cto, rto);
        try {
            Resp r = new Resp();
            r.code = c.getResponseCode();   // read the code first: Android throws on getInputStream() for a 401
            r.cookie = sessionCookie(c.getHeaderFields());
            InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in != null) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream(4096); byte[] buf = new byte[4096]; int n;
                while ((n = in.read(buf)) > 0) { bo.write(buf, 0, n); if (bo.size() > 512 * 1024) throw new IOException("response too large"); }
                in.close(); r.body = bo.toString("UTF-8");
            }
            return r;
        } finally { c.disconnect(); }
    }

    static String sessionCookie(Map<String, List<String>> h) {
        if (h == null) return null;
        for (Map.Entry<String, List<String>> e : h.entrySet()) {
            if (e.getKey() == null || !e.getKey().equalsIgnoreCase("Set-Cookie")) continue;
            for (String v : e.getValue()) {
                for (String part : v.split(";")) {
                    String p = part.trim();
                    if (p.startsWith("galaxy_session=")) return p.substring("galaxy_session=".length());
                }
            }
        }
        return null;
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
}
