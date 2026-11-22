package org.tuiapp.core;

/**
 * The HUD model: folds /api/hud snapshots and scene-stream frames into one ViewState, applying the schema-3
 * staleness contract. Implements DataSource.Listener so a source can feed it directly. Thread-safe (synchronized);
 * time is passed in (ms, monotonic) so host tests drive it with a fake clock.
 *
 * Rules (docs/HUD-CONTRACT.md):
 *  - state "current": the frame becomes the new base; goodAt = now.
 *  - state "stale" (sticky): onroad / engaged / vEgo in the frame are the comma's last real values (sticky), and
 *    onroadAgeSeconds says how old. Blocks the stale frame leaves null (model, lead, tracks, alert) keep the last
 *    current values: a short sampler blip must not blank the road. goodAt is NOT refreshed.
 *  - "unavailable": like stale, but the comma has no data at all; after the grey delay the scene greys.
 *  - grey only after GREY_MS without a current /api/hud AND SCENE_GREY_MS without a scene frame (HoldRule.grey).
 *  - driving ends only on an explicit onroad=false, or ONROAD_HOLD_MS after the last onroad=true (HoldRule.drivingStep).
 *  - a scene frame merges into the base while HoldRule.sceneUsable says so.
 */
public final class HudModel implements DataSource.Listener {
    public static final long GREY_MS = 2000, SCENE_GREY_MS = 1000, SCENE_BASE_MS = 2000;

    private HudFrame base;            // last frame with useful content (current, or sticky-merged stale)
    private long baseAt = -1, goodAt = -1, sceneAt = -1, prevSceneAt = -1;
    private final long[] onroadAt = {-1};
    private boolean stale;
    private double staleAgeS = Double.NaN;
    private LinkState link = LinkState.SEARCHING;
    private String linkDetail = "";
    private boolean demo;
    // scene overlay (newer than base)
    private SceneFrame scene;
    private RouteGeo route;
    // position fallbacks: StarPilot navigation status (upstream), then this device's own GPS (opt-in setting)
    private NavStatus nav; private long navAt = -1; private RouteGeo navRoute; private String navRouteKey = "";
    private double devLat = Double.NaN, devLon = Double.NaN, devBearing = Double.NaN; private long devAt = -1;
    public static final long FIX_MAX_AGE_MS = 3000;
    private long frames, sceneFrames;

    private Addons addons = Addons.NONE;
    // odometer from vEgo (positions the demo map)
    private double distM, lastV = Double.NaN; private long lastVAt = -1;

    public synchronized void setDemo(boolean d) { demo = d; }
    public synchronized void setAddons(Addons a) { addons = a == null ? Addons.NONE : a; }

    private void odo(double v, long now) {
        if (!Double.isNaN(lastV) && lastVAt >= 0 && now > lastVAt) distM += Math.max(0, lastV) * Math.min(1000, now - lastVAt) / 1000.0;
        if (!Double.isNaN(v)) { lastV = v; lastVAt = now; }
    }

    /** forget everything (the data source changed) */
    public synchronized void reset() {
        base = null; scene = null; route = null; nav = null; navAt = -1; navRoute = null; navRouteKey = ""; devAt = -1; baseAt = goodAt = sceneAt = prevSceneAt = -1; onroadAt[0] = -1;
        distM = 0; lastV = Double.NaN; lastVAt = -1;
        stale = false; staleAgeS = Double.NaN; link = LinkState.SEARCHING; linkDetail = ""; frames = sceneFrames = 0;
    }

    @Override public synchronized void onHud(HudFrame f, long now) {
        frames++;
        addons.onHud(f.raw, now);
        odo(f.vEgo, now);
        HoldRule.drivingStep(f.onroad, now, onroadAt);
        if (f.isCurrent()) {
            base = f; baseAt = now; goodAt = now; stale = false; staleAgeS = Double.NaN;
            return;
        }
        stale = true;
        staleAgeS = f.onroadAgeSeconds;
        if (base == null) { base = f; return; }
        // sticky merge: take the comma's sticky scalars, keep the last real geometry
        HudFrame m = f.copy();   // never edit the caller's frame
        if (m.laneLines == null) { m.laneLines = base.laneLines; m.roadEdges = base.roadEdges; m.path = base.path; m.laneLineProbs = base.laneLineProbs; m.roadEdgeStds = base.roadEdgeStds; }
        if (!m.leadPresent && base.leadPresent && Double.isNaN(m.leadDRel)) {
            m.leadPresent = true; m.leadDRel = base.leadDRel; m.leadYRel = base.leadYRel; m.leadVRel = base.leadVRel; m.leadTrackId = base.leadTrackId;
        }
        if (m.tracks == null) m.tracks = base.tracks;
        if (m.onroad == null) m.onroad = base.onroad;
        if (m.engaged == null) m.engaged = base.engaged;
        if (m.available == null) m.available = base.available;
        if (Double.isNaN(m.vEgo)) m.vEgo = base.vEgo;
        if (Double.isNaN(m.vCruise)) m.vCruise = base.vCruise;
        if (Double.isNaN(m.slcLimit)) m.slcLimit = base.slcLimit;
        if (m.isMetric == null) m.isMetric = base.isMetric;
        if (m.gearLetter == null) m.gearLetter = base.gearLetter;
        if (m.latActive == null) m.latActive = base.latActive;
        if (!m.hasFix()) { m.gpsLat = base.gpsLat; m.gpsLon = base.gpsLon; m.gpsBearing = base.gpsBearing; }
        if (m.navGen < 0 && base.navActive) { m.navActive = true; m.navGen = base.navGen; m.navType = base.navType; m.navModifier = base.navModifier;
            m.navStreet = base.navStreet; m.navDistM = base.navDistM; m.navRemainM = base.navRemainM; m.navRemainS = base.navRemainS; }
        if (!m.hasAlert()) { m.alertText1 = base.alertText1; m.alertText2 = base.alertText2; m.alertStatus = base.alertStatus; }
        base = m;   // baseAt / goodAt unchanged: the data did not get newer
    }

    @Override public synchronized void onScene(SceneFrame s, long now) {
        if (s == SceneFrame.KEEPALIVE) return;
        sceneFrames++;
        odo(s.vEgo, now);
        prevSceneAt = sceneAt; sceneAt = now;
        if (HoldRule.sceneUsable(now, baseAt, prevSceneAt, SCENE_BASE_MS)) scene = s;
    }

    @Override public synchronized void onRoute(RouteGeo r, long now) { route = r; }

    @Override public synchronized void onNav(NavStatus n, long now) {
        nav = n; navAt = now;
        if (n.routeLat != null && !n.routeKey.equals(navRouteKey)) {
            navRouteKey = n.routeKey;
            navRoute = n.routeLat.length >= 2 ? new RouteGeo(n.routeKey.hashCode(), n.routeLon, n.routeLat, null) : null;
        }
    }

    /** this device's own GPS fix (only fed when the user enabled it in settings) */
    public synchronized void onDeviceFix(double lat, double lon, double bearingDeg, long now) {
        if (Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) return;
        devLat = lat; devLon = lon; devBearing = bearingDeg; devAt = now;
    }

    @Override public synchronized void onLink(LinkState st, String detail) { link = st; linkDetail = detail == null ? "" : detail; }

    public synchronized long frames() { return frames; }
    public synchronized long sceneFrames() { return sceneFrames; }

    public synchronized ViewState snapshot(long now) {
        ViewState v = new ViewState();
        v.link = link; v.linkDetail = linkDetail; v.demo = demo || link == LinkState.REPLAY;
        v.driving = HoldRule.drivingStep(null, now, onroadAt.clone());   // explicit false cleared onroadAt; true holds ONROAD_HOLD_MS
        v.stale = stale;
        v.nowMs = now;
        v.hasData = base != null;
        v.distanceM = distM + (Double.isNaN(lastV) || lastVAt < 0 ? 0 : Math.max(0, lastV) * Math.max(0, Math.min(1000, now - lastVAt)) / 1000.0);
        v.grey = HoldRule.grey(now, goodAt, sceneAt, GREY_MS, SCENE_GREY_MS) || (goodAt < 0 && base != null);
        double held = goodAt < 0 ? Double.NaN : (now - goodAt) / 1000.0;
        v.dataAgeS = !Double.isNaN(staleAgeS) && stale ? Math.max(staleAgeS, Double.isNaN(held) ? 0 : held) : held;
        HudFrame b = base;
        if (b == null) return v;
        v.speedMps = b.vEgo; v.setSpeedMps = !Double.isNaN(b.vCruiseCluster) ? b.vCruiseCluster : b.vCruise;
        if (!Double.isNaN(v.setSpeedMps) && (v.setSpeedMps <= 0 || v.setSpeedMps > 90)) v.setSpeedMps = Double.NaN; // 255 km/h = "unset"
        v.limitMps = b.slcLimit > 0 ? b.slcLimit : Double.NaN;
        v.isMetric = b.isMetric;
        v.engaged = Boolean.TRUE.equals(b.engaged);
        v.available = Boolean.TRUE.equals(b.available) || v.engaged;
        v.latActive = Boolean.TRUE.equals(b.latActive);
        v.gear = b.gearLetter; v.personality = b.personality; v.blinker = b.blinker;
        v.laneChangeState = b.laneChangeState; v.laneChangeDir = b.laneChangeDir;
        v.leadPresent = b.leadPresent; v.leadDRel = b.leadDRel; v.leadYRel = b.leadYRel; v.leadVRel = b.leadVRel; v.leadTrackId = b.leadTrackId;
        v.tracks = b.tracks;
        v.laneLines = b.laneLines; v.roadEdges = b.roadEdges; v.path = b.path; v.laneLineProbs = b.laneLineProbs;
        if (b.hasFix()) { v.lat = b.gpsLat; v.lon = b.gpsLon; v.bearingDeg = b.gpsBearing; v.fixSource = "comma"; }
        v.navActive = b.navActive; v.navModifier = b.navModifier; v.navType = b.navType; v.navStreet = b.navStreet;
        v.navDistM = b.navDistM; v.navRemainM = b.navRemainM; v.navRemainS = b.navRemainS;
        v.route = b.navActive && route != null && route.gen == b.navGen ? route : null;
        // StarPilot's own navigation (upstream /api/navigation/status) fills position / guidance the HUD frame lacks
        NavStatus ns = nav != null && navAt >= 0 && now - navAt <= FIX_MAX_AGE_MS ? nav : null;
        if (!v.hasFix() && ns != null && ns.hasFix() && ns.liveFix) { v.lat = ns.lat; v.lon = ns.lon; v.bearingDeg = ns.bearing; v.fixSource = "comma-nav"; }
        if (!v.navActive && ns != null && ns.guiding()) {
            v.navActive = true; v.navType = ns.maneuverType; v.navModifier = ns.maneuverModifier; v.navStreet = ns.text;
            v.navDistM = ns.distM; v.navRemainM = ns.remainM; v.navRemainS = ns.remainS;
            if (v.route == null) v.route = navRoute;
        }
        if (!v.hasFix() && devAt >= 0 && now - devAt <= FIX_MAX_AGE_MS) { v.lat = devLat; v.lon = devLon; v.bearingDeg = devBearing; v.fixSource = "device"; }
        v.alert1 = b.alertText1; v.alert2 = b.alertText2; v.alertStatus = b.alertStatus;
        SceneFrame s = scene;
        if (s != null && HoldRule.sceneUsable(now, baseAt, sceneAt, SCENE_BASE_MS) && now - sceneAt <= SCENE_BASE_MS) {
            if (!Double.isNaN(s.vEgo)) v.speedMps = s.vEgo;
            v.yawRate = s.yawRate;
            if (s.hasModel) { v.laneLines = s.laneLines; v.roadEdges = s.roadEdges; if (s.path != null) v.path = s.path; if (s.laneLineProbs != null) v.laneLineProbs = s.laneLineProbs; }
            if (s.hasLeadKey) { v.leadPresent = s.leadPresent; v.leadDRel = s.leadDRel; v.leadYRel = s.leadYRel; v.leadVRel = s.leadVRel; v.leadTrackId = s.leadTrackId; }
            if (s.laneChangeState != null) { v.laneChangeState = s.laneChangeState; v.laneChangeDir = s.laneChangeDir; }
        }
        return v;
    }
}
