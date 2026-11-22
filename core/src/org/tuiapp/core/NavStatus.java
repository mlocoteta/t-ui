package org.tuiapp.core;

import java.util.List;
import java.util.Map;

/**
 * StarPilot's own navigation status (`GET /api/navigation/status`, upstream Galaxy, no patch needed):
 * location {latitude, longitude, bearing?, source?, validForMs?} (present when navigation is set up),
 * status (disabled | needsKey | noDestination | waitingForLocation | routing | guiding | arrived | ...),
 * instruction {text, maneuverType, maneuverModifier, distanceMeters, remainingDistanceMeters, remainingDurationSeconds},
 * route [{latitude, longitude}, ...] (omitted with routeUnchanged when the client sent the current routeKey), routeKey.
 */
public final class NavStatus {
    public double lat = Double.NaN, lon = Double.NaN, bearing = Double.NaN;
    /** the position is a live fix (validForMs > 0), not the last saved position */
    public boolean liveFix;
    public String status = "", routeKey = "";
    public boolean routeUnchanged;
    public String text, maneuverType, maneuverModifier;
    public double distM = Double.NaN, remainM = Double.NaN, remainS = Double.NaN;
    /** null = no route in this response (unchanged or none) */
    public double[] routeLon, routeLat;

    public boolean guiding() { return "guiding".equals(status); }
    public boolean hasFix() { return !Double.isNaN(lat) && !Double.isNaN(lon); }

    public static NavStatus parse(String text) {
        Map<String, Object> j = Json.parseObject(text);
        NavStatus n = new NavStatus();
        String st = Json.str(j, "status"); n.status = st == null ? "" : st;
        String rk = Json.str(j, "routeKey"); n.routeKey = rk == null ? "" : rk;
        n.routeUnchanged = Boolean.TRUE.equals(Json.bool(j, "routeUnchanged"));
        Map<String, Object> loc = Json.obj(j, "location");
        if (loc != null) {
            double la = Json.num(loc, "latitude"), lo = Json.num(loc, "longitude");
            if (!Double.isNaN(la) && !Double.isNaN(lo) && Math.abs(la) <= 90 && Math.abs(lo) <= 180) {
                n.lat = la; n.lon = lo; n.bearing = Json.num(loc, "bearing");
                double v = Json.num(loc, "validForMs");
                n.liveFix = !Double.isNaN(v) && v > 0;
            }
        }
        Map<String, Object> in = Json.obj(j, "instruction");
        if (in != null) {
            n.text = Json.str(in, "text"); n.maneuverType = Json.str(in, "maneuverType"); n.maneuverModifier = Json.str(in, "maneuverModifier");
            n.distM = Json.num(in, "distanceMeters"); n.remainM = Json.num(in, "remainingDistanceMeters"); n.remainS = Json.num(in, "remainingDurationSeconds");
        }
        List<Object> r = Json.arr(j, "route");
        if (r != null && !n.routeUnchanged) {
            double[] lo = new double[r.size()], la = new double[r.size()]; int k = 0;
            for (Object o : r) {
                if (!(o instanceof Map)) continue;
                @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) o;
                double a = Json.num(p, "latitude"), b = Json.num(p, "longitude");
                if (Double.isNaN(a) || Double.isNaN(b) || Math.abs(a) > 90 || Math.abs(b) > 180) continue;
                la[k] = a; lo[k] = b; k++;
            }
            n.routeLat = java.util.Arrays.copyOf(la, k); n.routeLon = java.util.Arrays.copyOf(lo, k);
        }
        return n;
    }
}
