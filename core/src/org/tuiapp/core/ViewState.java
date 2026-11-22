package org.tuiapp.core;

/** Everything the renderer draws, as one immutable-by-convention snapshot (built by HudModel.snapshot). */
public final class ViewState implements Cloneable {
    public LinkState link = LinkState.SEARCHING;
    public String linkDetail = "";
    public boolean demo;
    public boolean driving;
    /** comma data is old: grey the scene and say how old */
    public boolean grey;
    public double dataAgeS = Double.NaN;
    /** /api/hud said "stale" (sticky values shown, not yet greyed) */
    public boolean stale;
    /** at least one /api/hud frame arrived from this source */
    public boolean hasData;
    /** snapshot time (ms, source clock): drives the alert pulse */
    public long nowMs;
    /** distance driven since the source started (m, integrated vEgo): positions the demo map */
    public double distanceM;
    /** cards supplied by add-ons for this frame (empty in the default build) */
    /** user's own car colour (Settings "My car colour"); 0 = the theme's default */
    public int egoColor;
    public java.util.List<AddonCard> addonCards = java.util.Collections.emptyList();

    public double speedMps = Double.NaN, setSpeedMps = Double.NaN, limitMps = Double.NaN;
    public Boolean isMetric;
    public boolean engaged, latActive, available;
    public String gear;
    public String personality;
    public String laneChangeState, laneChangeDir, blinker;

    public boolean leadPresent;
    public double leadDRel = Double.NaN, leadYRel = Double.NaN, leadVRel = Double.NaN;
    public int leadTrackId = -1;
    public float[][] tracks;

    public float[][][] laneLines, roadEdges;
    public float[][] path;
    public float[] laneLineProbs;
    public double yawRate = Double.NaN;

    // map: comma gps fix, navigation, route geometry (null / NaN = unknown)
    public double lat = Double.NaN, lon = Double.NaN, bearingDeg = Double.NaN;
    public boolean navActive;
    public String navModifier, navType, navStreet;
    public double navDistM = Double.NaN, navRemainM = Double.NaN, navRemainS = Double.NaN;
    public RouteGeo route;
    /** where the position came from: comma (HUD gps), comma-nav (StarPilot navigation), device (this device's GPS) */
    public String fixSource;
    public boolean hasFix() { return !Double.isNaN(lat) && !Double.isNaN(lon); }

    public String alert1 = "", alert2 = "", alertStatus = "normal";

    /** shallow copy: arrays are replaced wholesale, never edited in place */
    public ViewState copy() { try { return (ViewState) super.clone(); } catch (CloneNotSupportedException e) { throw new AssertionError(e); } }

    /** time gap to the lead in s (NaN when unknown or nearly stopped) */
    public double leadGapS() { return leadPresent && speedMps > 2 && !Double.isNaN(leadDRel) ? leadDRel / speedMps : Double.NaN; }
    /** lead closer than 1.0 s or 8 m: drawn with the caution tint */
    public boolean leadClose() { double g = leadGapS(); return leadPresent && ((!Double.isNaN(g) && g < 1.0) || (!Double.isNaN(leadDRel) && leadDRel < 8 && driving)); }

    /** the state chip text: "Ready" / "Engaged" / "Not ready" / "Parked" / link text */
    public String chip() {
        if (link != LinkState.LIVE && !demo) return link.label;
        if (!driving) return "Parked";
        if (engaged) return "Engaged";
        return available ? "Ready" : "Not ready";
    }

    public boolean metric() { return Boolean.TRUE.equals(isMetric); }
    /** m/s -> display units (km/h or mph) */
    public double toDisplay(double mps) { return mps * (metric() ? 3.6 : 2.2369363); }
    public String unit() { return metric() ? "km/h" : "mph"; }
    public boolean alertCritical() { return "critical".equals(alertStatus) || "userPrompt".equals(alertStatus); }
}
