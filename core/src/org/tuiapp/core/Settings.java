package org.tuiapp.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User settings, persisted as flat string key/values (Android SharedPreferences in the app, a Map in tests).
 * Add-on settings use keys "addon.<id>.<key>" and live in the same store (get/put). Unknown values fall back to
 * the default, so an old or hand-edited store never crashes the app.
 */
public final class Settings {
    public enum ThemePref { AUTO, LIGHT, DARK }
    public enum Units { AUTO, MPH, KMH }

    public static final String K_ADDR = "commaAddress", K_THEME = "theme", K_UNITS = "units", K_LAYOUT = "layoutMode",
            K_SCREEN_ON = "keepScreenOn", K_RHD = "rightHandDrive", K_MAP_ONLINE = "mapOnline", K_MAP_SOURCE = "mapSource",
            K_EGO_COLOR = "egoColor", K_DEVICE_GPS = "useDeviceGps", K_SAFETY = "safetyAccepted", K_ABOUT = "about", K_MAP_URL = "mapCustomStyleUrl", K_OFFLINE_CAP = "offlineCapMb", K_OFFLINE = "offlineMaps";

    public String commaAddress = "";
    public ThemePref theme = ThemePref.AUTO;
    public Units units = Units.AUTO;
    public Layout.Mode layout = Layout.Mode.AUTO;
    public boolean keepScreenOn = true, rightHandDrive;
    /** live map tiles over the network (the comma hotspot or the unit's own data); off = offline areas + built-in only */
    public boolean mapOnline = true;
    public String mapSource = MapSources.OFM_LIBERTY, mapCustomUrl = "";
    public long offlineCapMb = 500;
    /** my car colour (opaque ARGB), 0 = theme default */
    public int egoColor;
    /** use this device's own GPS for the map when the comma gives no position (opt-in; asks for location permission) */
    public boolean useDeviceGps;
    /** the first-run safety notice was accepted (shown until then) */
    public boolean safetyAccepted;
    /** set by the app: this build can use the device's GPS (MapLibre build); not persisted */
    public boolean deviceGpsSupported;
    /** preset swatches for "My car colour" (neutral paint names, no maker colours); 0 = theme default */
    public static final int[] EGO_PRESETS = {0, 0xFF1B1D21, 0xFFF2F3F5, 0xFFB8BDC4, 0xFF5E646C, 0xFF1F3F7A, 0xFF9C1C1C, 0xFF2E4A3A};
    public static final String[] EGO_PRESET_NAMES = {"Auto", "Black", "White", "Silver", "Grey", "Blue", "Red", "Green"};
    private final Map<String, String> extra = new LinkedHashMap<String, String>();

    public static Settings from(Map<String, String> m) {
        Settings s = new Settings();
        if (m == null) return s;
        String a = m.get(K_ADDR); s.commaAddress = a == null ? "" : a.trim();
        s.theme = en(ThemePref.class, m.get(K_THEME), ThemePref.AUTO);
        s.units = en(Units.class, m.get(K_UNITS), Units.AUTO);
        s.layout = en(Layout.Mode.class, m.get(K_LAYOUT), Layout.Mode.AUTO);
        s.keepScreenOn = !"false".equals(m.get(K_SCREEN_ON));
        s.rightHandDrive = "true".equals(m.get(K_RHD));
        s.mapOnline = !"false".equals(m.get(K_MAP_ONLINE));
        String src = m.get(K_MAP_SOURCE); s.mapSource = src != null && java.util.Arrays.asList(MapSources.IDS).contains(src) ? src : MapSources.OFM_LIBERTY;
        String u = m.get(K_MAP_URL); s.mapCustomUrl = u == null ? "" : u.trim();
        s.egoColor = parseColor(m.get(K_EGO_COLOR));
        s.useDeviceGps = "true".equals(m.get(K_DEVICE_GPS));
        s.safetyAccepted = "true".equals(m.get(K_SAFETY));
        try { s.offlineCapMb = Math.max(10, Math.min(20000, Long.parseLong(String.valueOf(m.get(K_OFFLINE_CAP))))); } catch (NumberFormatException e) { s.offlineCapMb = 500; }
        for (Map.Entry<String, String> e : m.entrySet()) if (e.getKey().startsWith("addon.")) s.extra.put(e.getKey(), e.getValue());
        return s;
    }

    public Map<String, String> toMap() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put(K_ADDR, commaAddress); m.put(K_THEME, theme.name()); m.put(K_UNITS, units.name()); m.put(K_LAYOUT, layout.name());
        m.put(K_SCREEN_ON, String.valueOf(keepScreenOn)); m.put(K_RHD, String.valueOf(rightHandDrive));
        m.put(K_MAP_ONLINE, String.valueOf(mapOnline)); m.put(K_MAP_SOURCE, mapSource); m.put(K_MAP_URL, mapCustomUrl);
        m.put(K_OFFLINE_CAP, String.valueOf(offlineCapMb));
        m.put(K_EGO_COLOR, egoColor == 0 ? "" : hex(egoColor));
        m.put(K_DEVICE_GPS, String.valueOf(useDeviceGps));
        m.put(K_SAFETY, String.valueOf(safetyAccepted));
        m.putAll(extra);
        return m;
    }

    /** add-on value ("addon.<id>.<key>"), or dflt */
    public String get(String addonId, String key, String dflt) { String v = extra.get("addon." + addonId + "." + key); return v == null ? dflt : v; }
    public void put(String addonId, String key, String v) { extra.put("addon." + addonId + "." + key, v); }

    /** units shown: the user's choice, else the comma's isMetric, else mph */
    public boolean metric(Boolean commaMetric) { return units == Units.KMH || (units == Units.AUTO && Boolean.TRUE.equals(commaMetric)); }
    public Theme theme(boolean systemNight) { return theme == ThemePref.DARK || (theme == ThemePref.AUTO && systemNight) ? Theme.NIGHT : Theme.DAY; }
    public void applyTo(ViewState v) { v.isMetric = metric(v.isMetric); v.egoColor = egoColor; }

    /** "#RRGGBB", "RRGGBB" or "r,g,b" (0-255) -> opaque ARGB; anything else -> 0 (theme default) */
    public static int parseColor(String s) {
        if (s == null) return 0;
        String t = s.trim();
        try {
            if (t.contains(",")) {
                String[] p = t.split(",");
                if (p.length != 3) return 0;
                int r = Integer.parseInt(p[0].trim()), g = Integer.parseInt(p[1].trim()), b = Integer.parseInt(p[2].trim());
                if ((r | g | b) < 0 || r > 255 || g > 255 || b > 255) return 0;
                return 0xFF000000 | (r << 16) | (g << 8) | b;
            }
            if (t.startsWith("#")) t = t.substring(1);
            if (t.length() != 6) return 0;
            return 0xFF000000 | Integer.parseInt(t, 16);
        } catch (NumberFormatException e) { return 0; }
    }
    public static String hex(int argb) { return String.format(java.util.Locale.US, "#%06X", argb & 0xFFFFFF); }
    /** index of the current colour in EGO_PRESETS, or -1 for a custom colour */
    public int egoPresetIndex() { for (int i = 0; i < EGO_PRESETS.length; i++) if (EGO_PRESETS[i] == egoColor) return i; return -1; }

    public String styleUrl() { return MapSources.styleUrl(mapSource, mapCustomUrl); }

    private static <E extends Enum<E>> E en(Class<E> c, String v, E d) {
        if (v == null) return d;
        try { return Enum.valueOf(c, v.trim().toUpperCase(java.util.Locale.US)); } catch (IllegalArgumentException e) { return d; }
    }
}
