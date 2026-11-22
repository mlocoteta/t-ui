package org.tuiapp.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Add-on registry. Add-ons are listed by class name in an optional asset "addons.txt" that only an add-on build
 * contains (tools/build.sh ADDONS=...). The default APK has no such file, so the list is empty.
 */
public final class Addons {
    private final List<Addon> list;
    private final List<SourceProvider> sources;
    private final List<Object> others = new ArrayList<Object>();
    /** loaded classes that are neither Addon nor SourceProvider: platform hooks the app layer understands
     *  (e.g. the Android app's HeadUnitAdapter); the core never inspects them */
    public List<Object> others() { return Collections.unmodifiableList(others); }
    public static final Addons NONE = new Addons(Collections.<Addon>emptyList());

    public Addons(List<Addon> l) { this(l, Collections.<SourceProvider>emptyList()); }
    public Addons(List<Addon> l, List<SourceProvider> s) {
        list = Collections.unmodifiableList(new ArrayList<Addon>(l)); sources = Collections.unmodifiableList(new ArrayList<SourceProvider>(s));
    }
    public List<SourceProvider> sources() { return sources; }
    /** the provider for "scheme:rest", or null (plain host:port = the comma over HTTP) */
    public SourceProvider sourceFor(String address) {
        if (address == null) return null;
        int i = address.indexOf(':');
        if (i <= 0) return null;
        String sc = address.substring(0, i).trim().toLowerCase(java.util.Locale.US);
        for (SourceProvider p : sources) if (p.scheme().equals(sc)) return p;
        return null;
    }
    public List<Addon> all() { return list; }
    public boolean isEmpty() { return list.isEmpty(); }
    public Addon byId(String id) { for (Addon a : list) if (a.id().equals(id)) return a; return null; }

    /** one class name per line, '#' comments; a class that fails to load is reported in errors and skipped */
    public static Addons load(Reader r, List<String> errors) throws IOException {
        List<Addon> l = new ArrayList<Addon>(); List<SourceProvider> sp = new ArrayList<SourceProvider>(); List<Object> ot = new ArrayList<Object>();
        BufferedReader br = new BufferedReader(r); String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            try {
                Object o = Class.forName(line).getDeclaredConstructor().newInstance();
                if (o instanceof Addon) l.add((Addon) o);
                if (o instanceof SourceProvider) sp.add((SourceProvider) o);
                if (!(o instanceof Addon) && !(o instanceof SourceProvider)) ot.add(o);
            } catch (Exception | LinkageError e) { if (errors != null) errors.add(line + ": " + e); }
        }
        Addons a = new Addons(l, sp); a.others.addAll(ot); return a;
    }

    public void onHud(java.util.Map<String, Object> raw, long now) { for (Addon a : list) a.onHud(raw, now); }

    public List<AddonCard> cards(ViewState v, Settings s, long now) {
        if (list.isEmpty()) return Collections.emptyList();
        List<AddonCard> c = new ArrayList<AddonCard>();
        for (Addon a : list) { List<AddonCard> x = a.cards(v, s, now); if (x != null) c.addAll(x); }
        return c;
    }
}
