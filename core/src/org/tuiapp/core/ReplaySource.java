package org.tuiapp.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Plays a JSONL replay: one object per line, {"t": ms since start, "hud": {/api/hud frame}} or
 * {"t": ms, "scene": {scene-stream frame}}. Lines starting with '#' are comments. Use only synthetic or
 * consented, scrubbed data (docs/PRIVACY-CLEAN-EXPORT.md); tools/gen_synthetic_replay.py makes one.
 * start() plays in real time on a thread (optionally looping); runTo() feeds deterministically for tests.
 */
public final class ReplaySource implements DataSource {
    static final class Ev { final long t; final HudFrame hud; final SceneFrame scene; Ev(long t, HudFrame h, SceneFrame s) { this.t = t; hud = h; scene = s; } }

    private final List<Ev> evs;
    private final boolean loop;
    private final Clock clock;
    private volatile boolean running;
    private Thread th;
    private int next;
    private long durationMs;

    public ReplaySource(List<String> lines, boolean loop, Clock clock) {
        this.loop = loop; this.clock = clock == null ? SYSTEM : clock;
        evs = new ArrayList<Ev>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            Map<String, Object> o = Json.parseObject(line);
            long t = (long) Json.num(o, "t");
            Map<String, Object> h = Json.obj(o, "hud"), s = Json.obj(o, "scene");
            if (h != null) evs.add(new Ev(t, HudFrame.from(h), null));
            else if (s != null) evs.add(new Ev(t, null, SceneFrame.from(s)));
        }
        for (Ev e : evs) durationMs = Math.max(durationMs, e.t);
    }

    public static ReplaySource read(Reader r, boolean loop, Clock clock) throws IOException {
        BufferedReader br = new BufferedReader(r); List<String> l = new ArrayList<String>(); String s;
        while ((s = br.readLine()) != null) l.add(s);
        return new ReplaySource(l, loop, clock);
    }

    public int size() { return evs.size(); }
    public long durationMs() { return durationMs; }

    /** Deterministic: deliver every event with t <= tMs (relative to replay start), stamped base + t. */
    public synchronized int runTo(long tMs, long base, Listener l) {
        int n = 0;
        while (next < evs.size() && evs.get(next).t <= tMs) { deliver(evs.get(next), base + evs.get(next).t, l); next++; n++; }
        return n;
    }

    private static void deliver(Ev e, long now, Listener l) {
        if (e.hud != null) l.onHud(e.hud, now); else l.onScene(e.scene, now);
    }
    @Override public String describe() { return "replay (" + evs.size() + " frames, " + durationMs / 1000 + " s)"; }

    @Override public synchronized void start(final Listener l) {
        if (running) return;
        running = true;
        l.onLink(LinkState.REPLAY, describe());
        th = new Thread(new Runnable() { public void run() { play(l); } }, "tui-replay");
        th.setDaemon(true); th.start();
    }

    private void play(Listener l) {
        do {
            synchronized (this) { next = 0; }
            long t0 = clock.now();
            while (running && next < evs.size()) {
                Ev e = evs.get(next);
                long wait = e.t - (clock.now() - t0);
                if (wait > 0) { try { Thread.sleep(Math.min(wait, 200)); } catch (InterruptedException ie) { return; } continue; }
                deliver(e, clock.now(), l);
                synchronized (this) { next++; }
            }
        } while (running && loop);
    }

    @Override public synchronized void stop() { running = false; if (th != null) th.interrupt(); }
}
