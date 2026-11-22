package org.tuiapp.core;

import java.util.List;
import java.util.Map;

/** Route geometry from GET /api/hud/route?gen=g: {"gen":g,"coords":[[lon,lat],...],"cong":["low",...]}. */
public final class RouteGeo {
    public final int gen;
    public final double[] lon, lat;
    public final String[] cong;   // per segment (length n-1) or null

    public RouteGeo(int gen, double[] lon, double[] lat, String[] cong) { this.gen = gen; this.lon = lon; this.lat = lat; this.cong = cong; }

    public int size() { return lon.length; }

    public static RouteGeo parse(String text) {
        Map<String, Object> j = Json.parseObject(text);
        int gen = Json.integer(j, "gen", -1);
        List<Object> c = Json.arr(j, "coords");
        if (gen < 0 || c == null) throw new IllegalArgumentException("not a route");
        double[] lo = new double[c.size()], la = new double[c.size()]; int n = 0;
        for (Object o : c) {
            if (!(o instanceof List) || ((List<?>) o).size() < 2) continue;
            Object a = ((List<?>) o).get(0), b = ((List<?>) o).get(1);
            if (!(a instanceof Double) || !(b instanceof Double)) continue;
            double x = (Double) a, y = (Double) b;
            if (Math.abs(y) > 90 || Math.abs(x) > 180) continue;
            lo[n] = x; la[n] = y; n++;
        }
        if (n < 2) throw new IllegalArgumentException("route too short");
        String[] cg = null;
        List<Object> cl = Json.arr(j, "cong");
        if (cl != null && cl.size() == n - 1) { cg = new String[n - 1]; for (int i = 0; i < n - 1; i++) cg[i] = cl.get(i) instanceof String ? (String) cl.get(i) : "unknown"; }
        return new RouteGeo(gen, java.util.Arrays.copyOf(lo, n), java.util.Arrays.copyOf(la, n), cg);
    }
}
