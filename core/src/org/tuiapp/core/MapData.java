package org.tuiapp.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * v0.2 map data: a tiny self-contained vector format (docs/MAP.md), local metres (x east, y north), no geographic
 * coordinates. {"format":"tui-map-1","name":..,"roads":[{"c":"major|minor|street","p":[x0,y0,...]}],
 * "water":[[x0,y0,...]],"route":[x0,y0,...]}. Used for the synthetic demo town; real offline maps come later.
 */
public final class MapData {
    public static final String FORMAT = "tui-map-1";
    public static final int MAJOR = 0, MINOR = 1, STREET = 2;

    public String name = "";
    public final List<float[]> roads = new ArrayList<float[]>();     // flat x,y
    public final List<Integer> roadClass = new ArrayList<Integer>();
    public final List<float[]> water = new ArrayList<float[]>();
    public float[] route;          // flat x,y (may be null)
    public float[] routeCum;       // cumulative length at each route vertex (m)

    public static MapData parse(String text) {
        Map<String, Object> j = Json.parseObject(text);
        if (!FORMAT.equals(Json.str(j, "format"))) throw new IllegalArgumentException("not " + FORMAT);
        MapData d = new MapData();
        String n = Json.str(j, "name"); d.name = n == null ? "" : n;
        List<Object> roads = Json.arr(j, "roads");
        if (roads != null) for (Object o : roads) {
            if (!(o instanceof Map)) continue;
            @SuppressWarnings("unchecked") Map<String, Object> r = (Map<String, Object>) o;
            float[] p = flat(r.get("p")); if (p == null || p.length < 4) continue;
            String c = Json.str(r, "c");
            d.roads.add(p); d.roadClass.add("major".equals(c) ? MAJOR : "minor".equals(c) ? MINOR : STREET);
        }
        List<Object> water = Json.arr(j, "water");
        if (water != null) for (Object o : water) { float[] p = flat(o); if (p != null && p.length >= 6) d.water.add(p); }
        d.route = flat(j.get("route"));
        if (d.route != null && d.route.length < 4) d.route = null;
        if (d.route != null) {
            int n2 = d.route.length / 2; d.routeCum = new float[n2];
            for (int i = 1; i < n2; i++) d.routeCum[i] = d.routeCum[i - 1] + (float) Math.hypot(d.route[2 * i] - d.route[2 * i - 2], d.route[2 * i + 1] - d.route[2 * i - 1]);
        }
        return d;
    }

    static float[] flat(Object o) {
        float[][] p = Json.points(o);
        if (p == null) return null;
        float[] r = new float[p.length * 2];
        for (int i = 0; i < p.length; i++) { r[2 * i] = p[i][0]; r[2 * i + 1] = p[i][1]; }
        return r;
    }

    public float routeLength() { return routeCum == null ? 0 : routeCum[routeCum.length - 1]; }

    /** position + heading (rad, 0 = east, ccw) at distance s along the route (clamped). out = {x, y, heading} */
    public void along(double s, float[] out) {
        int n = routeCum.length;
        float ss = (float) Math.max(0, Math.min(routeLength(), s));
        int i = 1;
        while (i < n - 1 && routeCum[i] < ss) i++;
        float seg = Math.max(1e-3f, routeCum[i] - routeCum[i - 1]), a = (ss - routeCum[i - 1]) / seg;
        float x0 = route[2 * i - 2], y0 = route[2 * i - 1], x1 = route[2 * i], y1 = route[2 * i + 1];
        out[0] = x0 + (x1 - x0) * a; out[1] = y0 + (y1 - y0) * a; out[2] = (float) Math.atan2(y1 - y0, x1 - x0);
    }

    /** next turn after s: {distance to it (m), +1 left / -1 right}, or null. A turn = heading change > 35 deg. */
    public float[] nextTurn(double s) {
        if (route == null) return null;
        int n = routeCum.length;
        for (int i = 1; i < n - 1; i++) {
            if (routeCum[i] <= s) continue;
            double h0 = Math.atan2(route[2 * i + 1] - route[2 * i - 1], route[2 * i] - route[2 * i - 2]);
            double h1 = Math.atan2(route[2 * i + 3] - route[2 * i + 1], route[2 * i + 2] - route[2 * i]);
            double d = h1 - h0; while (d > Math.PI) d -= 2 * Math.PI; while (d < -Math.PI) d += 2 * Math.PI;
            if (Math.abs(d) > Math.toRadians(35)) return new float[]{(float) (routeCum[i] - s), d > 0 ? 1 : -1};
        }
        return null;
    }
}
