package org.tuiapp.core;

/**
 * Where HUD data comes from. v0 implementations: CommaHttpSource (StarPilot Galaxy /api/hud + /api/hud/scene)
 * and ReplaySource (recorded or synthetic JSONL). Later: a standalone comma daemon, Bluetooth, OBD.
 * Sources are read-only: there is no write path in this interface on purpose.
 * Listener callbacks arrive on a source thread; `now` is the source clock in ms (monotonic).
 */
public interface DataSource {
    interface Listener {
        void onHud(HudFrame f, long now);
        void onScene(SceneFrame s, long now);
        void onLink(LinkState state, String detail);
        /** route geometry for nav generation r.gen (sources that support it) */
        default void onRoute(RouteGeo r, long now) { }
    }

    /** Monotonic ms clock; replaceable in tests. */
    interface Clock { long now(); }
    Clock SYSTEM = new Clock() { public long now() { return System.nanoTime() / 1000000L; } };

    void start(Listener l);
    void stop();
    String describe();
}
