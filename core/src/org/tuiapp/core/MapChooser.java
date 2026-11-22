package org.tuiapp.core;

/**
 * Which map to show (pure; host-tested): online tiles -> offline/cached tiles -> built-in renderer.
 *   demo drive                              -> BUILTIN_DEMO (synthetic town)
 *   no map engine in this build             -> BUILTIN_ROUTE when there is a fix, else NO_FIX
 *   engine, no gps fix                      -> NO_FIX ("waiting for GPS from the comma")
 *   style failed to load (no net, no cache) -> BUILTIN_ROUTE
 *   online allowed and network up           -> ENGINE_ONLINE
 *   otherwise, fix inside a downloaded area -> ENGINE_OFFLINE
 *   otherwise, style loaded from cache      -> ENGINE_CACHED (tiles may be missing)
 *   otherwise                               -> BUILTIN_ROUTE
 */
public final class MapChooser {
    public enum Mode {
        BUILTIN_DEMO("Demo map"), NO_FIX("Waiting for GPS from the comma"), BUILTIN_ROUTE("No map tiles: route only"),
        ENGINE_ONLINE("Live map"), ENGINE_OFFLINE("Offline map"), ENGINE_CACHED("Cached map");
        public final String label;
        Mode(String l) { label = l; }
        public boolean engine() { return this == ENGINE_ONLINE || this == ENGINE_OFFLINE || this == ENGINE_CACHED; }
    }
    public static final int STYLE_LOADING = 0, STYLE_LOADED = 1, STYLE_FAILED = 2;

    public static Mode choose(boolean demo, boolean engine, boolean hasFix, int style, boolean onlineAllowed, boolean networkUp, boolean inOfflineArea) {
        if (demo) return Mode.BUILTIN_DEMO;
        if (!engine) return hasFix ? Mode.BUILTIN_ROUTE : Mode.NO_FIX;
        if (!hasFix) return Mode.NO_FIX;
        if (style == STYLE_FAILED) return Mode.BUILTIN_ROUTE;
        if (onlineAllowed && networkUp) return Mode.ENGINE_ONLINE;
        if (inOfflineArea) return Mode.ENGINE_OFFLINE;
        if (style == STYLE_LOADED) return Mode.ENGINE_CACHED;
        return Mode.BUILTIN_ROUTE;
    }

    /** MapLibre should stay offline (no requests) when online tiles are off or there is no network */
    public static boolean engineConnected(boolean onlineAllowed, boolean networkUp) { return onlineAllowed && networkUp; }
}
