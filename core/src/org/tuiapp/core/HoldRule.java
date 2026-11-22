package org.tuiapp.core;

/**
 * Staleness rules for held comma data (pure; tests/host StaleTest). Copied from the private head-unit project
 * (see CHANGES/ORIGIN.md) with the device-specific comments removed.
 */
public final class HoldRule {
    /** Grey the scene + show "comma data old" only when the HUD has not been current for greyMs AND no scene frame
     *  arrived for sceneMs. goodAt / sceneAt are -1 when never seen. */
    public static boolean grey(long now, long goodAt, long sceneAt, long greyMs, long sceneMs) {
        return goodAt >= 0 && now - goodAt >= greyMs && (sceneAt < 0 || now - sceneAt >= sceneMs);
    }

    /** A scene frame may be merged into the last full HUD frame while that frame is <= baseMs old, or, beyond that,
     *  while the scene stream itself is live (previous scene frame <= SCENE_LIVE_GAP_MS ago) and the base is
     *  <= SCENE_BASE_MAX_MS old. Without this a long /api/hud gap froze the road although scene frames kept coming. */
    public static final long SCENE_LIVE_GAP_MS = 1500, SCENE_BASE_MAX_MS = 60000;
    public static boolean sceneUsable(long now, long baseAt, long lastSceneAt, long baseMs) {
        if (baseAt < 0) return false;
        long age = now - baseAt;
        if (age <= baseMs) return true;
        return age <= SCENE_BASE_MAX_MS && lastSceneAt >= 0 && now - lastSceneAt <= SCENE_LIVE_GAP_MS;
    }

    /** Drive edge: only an explicit onroad=false ends a drive; onroad=null (comma sampler lag) keeps the last
     *  onroad=true for ONROAD_HOLD_MS. at[0] = time of the last onroad=true, -1 = none. */
    public static final long ONROAD_HOLD_MS = 10000;
    public static boolean drivingStep(Boolean onroad, long now, long[] at) {
        if (onroad != null) { at[0] = onroad ? now : -1; return onroad; }
        return at[0] >= 0 && now - at[0] <= ONROAD_HOLD_MS;
    }

    private HoldRule() {}
}
