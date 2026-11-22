package org.tuiapp.core;

import java.util.List;
import java.util.Map;

/**
 * One typed `GET /api/hud` snapshot (StarPilot Galaxy, schemaVersion 3; older 1/2 parse with fewer fields).
 * Contract (docs/HUD-CONTRACT.md): SI units on the wire, absent or null = unknown, never invent a value.
 * Unknown numbers are NaN, unknown booleans/strings null, unknown ints -1.
 * Read-only: nothing here writes to the comma.
 */
public final class HudFrame implements Cloneable {
    public static final String CURRENT = "current", STALE = "stale", UNAVAILABLE = "unavailable";

    public int schemaVersion = -1;
    public String state = UNAVAILABLE;          // current | stale | unavailable
    public long seq = -1;
    public double ageSeconds = Double.NaN;      // sample age when the frame left the comma
    public double onroadAgeSeconds = Double.NaN;// schema 3: age of the last real onroad/engaged/vEgo sample (sticky while stale)
    public Boolean onroad, engaged, active, available, isMetric, experimental, latActive;
    public String status, personality, gearLetter, blinker;
    public double vEgo = Double.NaN, vCruise = Double.NaN, vCruiseCluster = Double.NaN;
    public int distanceBars = -1;

    // lead (radarState.leadOne): present = dRel known
    public boolean leadPresent;
    public double leadDRel = Double.NaN, leadYRel = Double.NaN, leadVRel = Double.NaN, leadVLead = Double.NaN;
    public int leadTrackId = -1;

    // alert
    public String alertText1 = "", alertText2 = "", alertStatus = "normal", alertSize = "";

    // speed-limit control
    public double slcLimit = Double.NaN, slcOffset = Double.NaN;

    // lane change
    public String laneChangeState, laneChangeDir;

    // model (x forward m, y right m)
    public float[][][] laneLines, roadEdges;
    public float[][] path;
    public float[] laneLineProbs, roadEdgeStds;

    // gps (comma fused fix; null block = no fix): degrees, bearing true north, m/s, m, s
    public double gpsLat = Double.NaN, gpsLon = Double.NaN, gpsBearing = Double.NaN, gpsSpeed = Double.NaN, gpsAcc = Double.NaN, gpsAge = Double.NaN;
    // nav (StarPilot navigation): active, route generation (route geometry via /api/hud/route?gen=), next maneuver
    public boolean navActive; public int navGen = -1;
    public String navType, navModifier, navStreet, navText, navDest;
    public double navDistM = Double.NaN, navRemainM = Double.NaN, navRemainS = Double.NaN;

    public boolean hasFix() { return !Double.isNaN(gpsLat) && !Double.isNaN(gpsLon) && Math.abs(gpsLat) <= 90 && Math.abs(gpsLon) <= 180; }

    /** radar tracks: [i] = {dRel, yRel, vRel, id, vLead, stopped(0/1)} */
    public float[][] tracks;

    /** the whole parsed object, for add-ons that read fields the core does not know (never edited) */
    public java.util.Map<String, Object> raw = java.util.Collections.emptyMap();

    public static HudFrame parse(String text) { return from(Json.parseObject(text)); }

    public static HudFrame from(Map<String, Object> j) {
        HudFrame h = new HudFrame();
        h.raw = j;
        h.schemaVersion = Json.integer(j, "schemaVersion", -1);
        String st = Json.str(j, "state");
        h.state = CURRENT.equals(st) || STALE.equals(st) ? st : UNAVAILABLE;
        h.seq = (long) Json.integer(j, "seq", -1);
        h.ageSeconds = Json.num(j, "ageSeconds");
        h.onroadAgeSeconds = Json.num(j, "onroadAgeSeconds");
        h.onroad = Json.bool(j, "onroad");
        h.engaged = Json.bool(j, "engaged");
        h.active = Json.bool(j, "active");
        h.available = Json.bool(j, "available");
        h.isMetric = Json.bool(j, "isMetric");
        h.experimental = Json.bool(j, "experimental");
        h.status = Json.str(j, "status");
        h.personality = Json.str(j, "personality");
        h.gearLetter = Json.str(j, "gearLetter");
        h.blinker = Json.str(j, "blinker");
        h.vEgo = Json.num(j, "vEgo");
        h.vCruise = Json.num(j, "vCruise");
        h.vCruiseCluster = Json.num(j, "vCruiseCluster");
        h.distanceBars = Json.integer(j, "distanceBars", -1);

        Map<String, Object> steering = Json.obj(j, "steering");
        h.latActive = Json.bool(steering, "latActive");

        Map<String, Object> lead = Json.obj(j, "lead");
        h.leadDRel = Json.num(lead, "dRel");
        h.leadPresent = !Double.isNaN(h.leadDRel);
        h.leadYRel = Json.num(lead, "yRel");
        h.leadVRel = Json.num(lead, "vRel");
        h.leadVLead = Json.num(lead, "vLead");
        h.leadTrackId = Json.integer(lead, "trackId", -1);

        Map<String, Object> al = Json.obj(j, "alert");
        if (al != null) {
            h.alertText1 = trim(Json.str(al, "text1")); h.alertText2 = trim(Json.str(al, "text2"));
            String s = Json.str(al, "status"); h.alertStatus = s == null ? "normal" : s;
            String z = Json.str(al, "size"); h.alertSize = z == null ? "" : z;
        }

        Map<String, Object> slc = Json.obj(j, "slc");
        h.slcLimit = Json.num(slc, "limit"); h.slcOffset = Json.num(slc, "offset");

        Map<String, Object> lc = Json.obj(j, "laneChange");
        h.laneChangeState = Json.str(lc, "state"); h.laneChangeDir = Json.str(lc, "direction");

        Map<String, Object> m = Json.obj(j, "model");
        if (m != null) {
            h.laneLines = Json.lines(m.get("laneLines"));
            h.roadEdges = Json.lines(m.get("roadEdges"));
            h.path = Json.points(m.get("path"));
            h.laneLineProbs = Json.floats(m.get("laneLineProbs"));
            h.roadEdgeStds = Json.floats(m.get("roadEdgeStds"));
        }

        Map<String, Object> gps = Json.obj(j, "gps");
        if (gps != null) {
            h.gpsLat = Json.num(gps, "lat"); h.gpsLon = Json.num(gps, "lon"); h.gpsBearing = Json.num(gps, "bearingDeg");
            h.gpsSpeed = Json.num(gps, "speedMs"); h.gpsAcc = Json.num(gps, "accM"); h.gpsAge = Json.num(gps, "ageS");
        }
        Map<String, Object> nav = Json.obj(j, "nav");
        if (nav != null) {
            h.navActive = Boolean.TRUE.equals(Json.bool(nav, "active"));
            h.navGen = Json.integer(nav, "gen", -1);
            Map<String, Object> mv = Json.obj(nav, "maneuver");
            h.navType = Json.str(mv, "type"); h.navModifier = Json.str(mv, "modifier"); h.navStreet = Json.str(mv, "street");
            h.navText = Json.str(mv, "text"); h.navDistM = Json.num(mv, "distM");
            h.navRemainM = Json.num(nav, "remainM"); h.navRemainS = Json.num(nav, "remainS"); h.navDest = Json.str(nav, "dest");
        }

        List<Object> tr = Json.arr(j, "tracks");
        if (tr != null) {
            float[][] t = new float[tr.size()][]; int n = 0;
            for (Object o : tr) {
                if (!(o instanceof Map)) continue;
                @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) o;
                double d = Json.num(p, "dRel"), y = Json.num(p, "yRel");
                if (Double.isNaN(d) || Double.isNaN(y)) continue;
                Boolean stopped = Json.bool(p, "stopped");
                t[n++] = new float[]{(float) d, (float) y, (float) Json.num(p, "vRel"), Json.integer(p, "id", -1),
                        (float) Json.num(p, "vLead"), Boolean.TRUE.equals(stopped) ? 1 : 0};
            }
            h.tracks = new float[n][]; System.arraycopy(t, 0, h.tracks, 0, n);
        }
        return h;
    }

    /** shallow copy (arrays are replaced wholesale, never edited in place) */
    public HudFrame copy() { try { return (HudFrame) super.clone(); } catch (CloneNotSupportedException e) { throw new AssertionError(e); } }

    private static String trim(String s) { return s == null ? "" : s.trim(); }

    public boolean isCurrent() { return CURRENT.equals(state); }
    public boolean isStale() { return STALE.equals(state); }
    public boolean hasAlert() { return !alertText1.isEmpty() || !alertText2.isEmpty(); }
}
