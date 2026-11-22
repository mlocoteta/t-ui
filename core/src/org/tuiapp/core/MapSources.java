package org.tuiapp.core;

/**
 * Map tile sources the user can pick, with each provider's usage limits for offline downloads.
 * No API keys. The default free source asks apps to keep each offline download to at most 3,000 tiles or 50 MB, at
 * most 4 parallel requests, with a User-Agent that identifies the app (its terms of service, checked 2026-11-22).
 * A custom style URL is the user's own responsibility: we still cap it (user setting) and keep 4 parallel requests.
 */
public final class MapSources {
    public static final String OFM_LIBERTY = "ofm-liberty", OFM_DARK = "ofm-dark", CUSTOM = "custom";
    public static final String[] IDS = {OFM_LIBERTY, OFM_DARK, CUSTOM};
    public static final String[] LABELS = {"OpenFreeMap", "OpenFreeMap dark", "Custom URL"};
    public static final int MAX_PARALLEL = 4;
    /** User-Agent for every map request: names the app, nothing about the user, car or device */
    public static final String USER_AGENT = Brand.APP_NAME + "-driving-display/" + Brand.VERSION_NAME + " (MapLibre Native)";

    public static String styleUrl(String id, String custom) {
        if (OFM_DARK.equals(id)) return "https://tiles.openfreemap.org/styles/dark";
        if (CUSTOM.equals(id)) return custom == null ? "" : custom.trim();
        return "https://tiles.openfreemap.org/styles/liberty";
    }

    public static String attribution(String id) {
        return CUSTOM.equals(id) ? "Map data: see your map source's terms" : "OpenFreeMap · © OpenMapTiles · Data © OpenStreetMap contributors";
    }

    /** offline limits for one download: {maxTiles, maxBytes} */
    public static long[] limits(String id, long customCapMb) {
        if (CUSTOM.equals(id)) return new long[]{Long.MAX_VALUE, Math.max(10, customCapMb) * 1000000L};
        return new long[]{3000, 50000000L};
    }

    /** why an offline area is refused, or null */
    public static String refuse(String id, long customCapMb, double[] bounds, int minZ, int maxZ) {
        if (bounds == null) return "Select an area first";
        if (maxZ > 14 && !CUSTOM.equals(id)) return "This source has detail up to zoom 14";
        long[] lim = limits(id, customCapMb);
        long t = Geo.tiles(bounds, minZ, maxZ), b = Geo.estimateBytes(bounds, minZ, maxZ);
        if (t > lim[0]) return "Too big for this source: " + String.format(java.util.Locale.US, "%,d", t) + " tiles (limit " + String.format(java.util.Locale.US, "%,d", lim[0]) + "). Pick a smaller area or lower detail.";
        if (b > lim[1]) return "Too big: about " + Geo.mb(b) + " (limit " + Geo.mb(lim[1]) + "). Pick a smaller area or lower detail.";
        return null;
    }

    public static int index(String id) { for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i; return 0; }
}
