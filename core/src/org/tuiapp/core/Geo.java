package org.tuiapp.core;

/**
 * Geo math (pure): Web Mercator tiles and screen viewports (512 px world tiles, as MapLibre), tile counts and size
 * estimates for offline areas, radius boxes, and a local metre projection for the built-in renderer.
 * Bounds are {north, east, south, west} in degrees.
 */
public final class Geo {
    public static final double R = 6378137.0, MAX_LAT = 85.05112878;

    public static double tileX(double lon, int z) { return (lon + 180.0) / 360.0 * (1 << z); }
    public static double tileY(double lat, int z) {
        double la = Math.toRadians(Math.max(-MAX_LAT, Math.min(MAX_LAT, lat)));
        return (1 - Math.log(Math.tan(la) + 1 / Math.cos(la)) / Math.PI) / 2 * (1 << z);
    }

    /** tiles covering bounds at zoom z */
    public static long tilesAt(double[] b, int z) {
        int n = 1 << z;
        long x0 = (long) Math.floor(tileX(b[3], z)), x1 = (long) Math.floor(tileX(b[1], z) - 1e-9);
        long y0 = (long) Math.floor(tileY(b[0], z)), y1 = (long) Math.floor(tileY(b[2], z) - 1e-9);
        x0 = clamp(x0, 0, n - 1); x1 = clamp(x1, 0, n - 1); y0 = clamp(y0, 0, n - 1); y1 = clamp(y1, 0, n - 1);
        return Math.max(0, x1 - x0 + 1) * Math.max(0, y1 - y0 + 1);
    }
    public static long tiles(double[] b, int minZ, int maxZ) { long t = 0; for (int z = minZ; z <= maxZ; z++) t += tilesAt(b, z); return t; }

    /** rough average bytes per vector tile by zoom (OpenMapTiles schema, mixed urban/rural); an ESTIMATE only */
    static final int[] BYTES_PER_TILE = {80000, 70000, 60000, 55000, 50000, 45000, 40000, 38000, 36000, 34000, 32000, 30000, 28000, 26000, 24000, 22000, 20000};
    /** style, fonts and icons fetched once per offline area */
    public static final long FIXED_BYTES = 1500000;
    public static long estimateBytes(double[] b, int minZ, int maxZ) {
        long s = FIXED_BYTES;
        for (int z = minZ; z <= maxZ; z++) s += tilesAt(b, z) * BYTES_PER_TILE[Math.min(z, BYTES_PER_TILE.length - 1)];
        return s;
    }

    /** square-ish box of radius r metres around a point */
    public static double[] around(double lat, double lon, double r) {
        double dLat = r / 111320.0, dLon = r / (111320.0 * Math.max(0.05, Math.cos(Math.toRadians(lat))));
        return new double[]{Math.min(MAX_LAT, lat + dLat), Math.min(180, lon + dLon), Math.max(-MAX_LAT, lat - dLat), Math.max(-180, lon - dLon)};
    }

    public static boolean contains(double[] b, double lat, double lon) { return lat <= b[0] && lat >= b[2] && lon <= b[1] && lon >= b[3]; }

    /** local east/north metres of (lat, lon) relative to (lat0, lon0) (equirectangular; fine for a few km) */
    public static double eastM(double lon, double lat0, double lon0) { return Math.toRadians(lon - lon0) * R * Math.cos(Math.toRadians(lat0)); }
    public static double northM(double lat, double lat0) { return Math.toRadians(lat - lat0) * R; }

    public static String mb(long bytes) { return bytes >= 10e6 ? Math.round(bytes / 1e6) + " MB" : String.format(java.util.Locale.US, "%.1f MB", bytes / 1e6); }

    private static long clamp(long v, long lo, long hi) { return v < lo ? lo : v > hi ? hi : v; }

    /** north-up Web Mercator viewport: screen px <-> lon/lat (512 px world tile at zoom 0, as MapLibre) */
    public static final class Viewport {
        public final double lat, lon, zoom; public final float x, y, w, h;
        public Viewport(double lat, double lon, double zoom, float x, float y, float w, float h) { this.lat = lat; this.lon = lon; this.zoom = zoom; this.x = x; this.y = y; this.w = w; this.h = h; }
        double scale() { return 512.0 * Math.pow(2, zoom); }
        double wx(double lon) { return (lon + 180) / 360 * scale(); }
        double wy(double lat) { return tileY(lat, 0) * scale(); }
        public float sx(double lo) { return (float) (x + w / 2 + wx(lo) - wx(lon)); }
        public float sy(double la) { return (float) (y + h / 2 + wy(la) - wy(lat)); }
        public double lonAt(float px) { return (wx(lon) + px - x - w / 2) / scale() * 360 - 180; }
        public double latAt(float py) {
            double my = (wy(lat) + py - y - h / 2) / scale();
            return Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * my))));
        }
        /** bounds of a screen rectangle */
        public double[] bounds(float x0, float y0, float x1, float y1) {
            return new double[]{latAt(Math.min(y0, y1)), lonAt(Math.max(x0, x1)), latAt(Math.max(y0, y1)), lonAt(Math.min(x0, x1))};
        }
        public Viewport zoomBy(double dz) { return new Viewport(lat, lon, Math.max(1, Math.min(18, zoom + dz)), x, y, w, h); }
        public Viewport panBy(float dx, float dy) { return new Viewport(latAt(y + h / 2 + dy), lonAt(x + w / 2 + dx), zoom, x, y, w, h); }
    }
}
