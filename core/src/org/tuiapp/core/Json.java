package org.tuiapp.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader (no dependencies, so the core runs on the host JVM and on Android alike).
 * Objects become Map<String,Object>, arrays List<Object>, numbers Double, plus String / Boolean / null.
 * Malformed input throws IllegalArgumentException. Frames are 1-4 KB, so a tree parse is fine.
 * Typed getters treat a missing key, JSON null and a wrong type the same way: unknown.
 */
public final class Json {
    private final String s; private int i;
    private Json(String s) { this.s = s; }

    public static Object parse(String text) {
        Json j = new Json(text);
        Object v = j.value();
        j.ws();
        if (j.i != j.s.length()) throw j.err("trailing data");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object o = parse(text);
        if (!(o instanceof Map)) throw new IllegalArgumentException("not an object");
        return (Map<String, Object>) o;
    }

    // ---------------------------------------------------------------- typed getters (null map allowed)
    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Map<String, Object> m, String k) {
        Object o = m == null ? null : m.get(k); return o instanceof Map ? (Map<String, Object>) o : null;
    }
    @SuppressWarnings("unchecked")
    public static List<Object> arr(Map<String, Object> m, String k) {
        Object o = m == null ? null : m.get(k); return o instanceof List ? (List<Object>) o : null;
    }
    public static double num(Map<String, Object> m, String k) {
        Object o = m == null ? null : m.get(k);
        if (!(o instanceof Double)) return Double.NaN;
        double d = (Double) o; return Double.isInfinite(d) ? Double.NaN : d;
    }
    public static int integer(Map<String, Object> m, String k, int dflt) {
        double d = num(m, k); return Double.isNaN(d) ? dflt : (int) Math.round(d);
    }
    public static String str(Map<String, Object> m, String k) {
        Object o = m == null ? null : m.get(k); return o instanceof String ? (String) o : null;
    }
    public static Boolean bool(Map<String, Object> m, String k) {
        Object o = m == null ? null : m.get(k); return o instanceof Boolean ? (Boolean) o : null;
    }
    public static boolean has(Map<String, Object> m, String k) { return m != null && m.containsKey(k); }

    /** [x0,y0,x1,y1,...] (scene stream) or [[x0,y0],[x1,y1],...] (/api/hud) -> float[n][2]; null if malformed. */
    public static float[][] points(Object o) {
        if (!(o instanceof List)) return null;
        List<?> l = (List<?>) o;
        if (l.isEmpty()) return new float[0][2];
        if (l.get(0) instanceof List) {
            float[][] r = new float[l.size()][2];
            for (int k = 0; k < l.size(); k++) {
                if (!(l.get(k) instanceof List)) return null;
                List<?> p = (List<?>) l.get(k);
                if (p.size() < 2 || !(p.get(0) instanceof Double) || !(p.get(1) instanceof Double)) return null;
                r[k][0] = ((Double) p.get(0)).floatValue(); r[k][1] = ((Double) p.get(1)).floatValue();
            }
            return r;
        }
        if (l.size() % 2 != 0) return null;
        float[][] r = new float[l.size() / 2][2];
        for (int k = 0; k < l.size(); k++) {
            if (!(l.get(k) instanceof Double)) return null;
            r[k / 2][k % 2] = ((Double) l.get(k)).floatValue();
        }
        return r;
    }

    /** A list of point lists (lane lines, road edges); null if any entry is malformed. */
    public static float[][][] lines(Object o) {
        if (!(o instanceof List)) return null;
        List<?> l = (List<?>) o;
        float[][][] r = new float[l.size()][][];
        for (int k = 0; k < l.size(); k++) { r[k] = points(l.get(k)); if (r[k] == null) return null; }
        return r;
    }

    public static float[] floats(Object o) {
        if (!(o instanceof List)) return null;
        List<?> l = (List<?>) o; float[] r = new float[l.size()];
        for (int k = 0; k < r.length; k++) { Object v = l.get(k); r[k] = v instanceof Double ? ((Double) v).floatValue() : Float.NaN; }
        return r;
    }

    // ---------------------------------------------------------------- parser
    private IllegalArgumentException err(String m) { return new IllegalArgumentException(m + " at " + i); }
    private void ws() { while (i < s.length()) { char c = s.charAt(i); if (c == ' ' || c == '\n' || c == '\r' || c == '\t') i++; else break; } }

    private Object value() {
        ws();
        if (i >= s.length()) throw err("eof");
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': lit("true"); return Boolean.TRUE;
            case 'f': lit("false"); return Boolean.FALSE;
            case 'n': lit("null"); return null;
            default: return number();
        }
    }
    private void lit(String w) { if (!s.startsWith(w, i)) throw err("bad literal"); i += w.length(); }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<String, Object>(); i++; ws();
        if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws(); if (i >= s.length() || s.charAt(i) != '"') throw err("want key");
            String k = string(); ws();
            if (i >= s.length() || s.charAt(i) != ':') throw err("want :"); i++;
            m.put(k, value()); ws();
            if (i >= s.length()) throw err("eof in object");
            char c = s.charAt(i++);
            if (c == '}') return m;
            if (c != ',') throw err("want , or }");
        }
    }
    private List<Object> array() {
        List<Object> l = new ArrayList<Object>(); i++; ws();
        if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
        while (true) {
            l.add(value()); ws();
            if (i >= s.length()) throw err("eof in array");
            char c = s.charAt(i++);
            if (c == ']') return l;
            if (c != ',') throw err("want , or ]");
        }
    }
    private String string() {
        i++; StringBuilder b = null; int start = i;
        while (true) {
            if (i >= s.length()) throw err("eof in string");
            char c = s.charAt(i);
            if (c == '"') { String r = b == null ? s.substring(start, i) : b.append(s, start, i).toString(); i++; return r; }
            if (c == '\\') {
                if (b == null) b = new StringBuilder();
                b.append(s, start, i); i++;
                if (i >= s.length()) throw err("eof in escape");
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break; case 't': b.append('\t'); break; case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break; case 'f': b.append('\f'); break;
                    case 'u': if (i + 4 > s.length()) throw err("bad \\u"); b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: b.append(e);
                }
                start = i;
            } else i++;
        }
    }
    private Double number() {
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (st == i) throw err("unexpected char");
        try { return Double.valueOf(s.substring(st, i)); } catch (NumberFormatException e) { throw err("bad number"); }
    }
}
