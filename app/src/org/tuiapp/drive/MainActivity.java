package org.tuiapp.drive;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tuiapp.core.Addon;
import org.tuiapp.core.Addons;
import org.tuiapp.core.CommaHttpSource;
import org.tuiapp.core.DataSource;
import org.tuiapp.core.HudModel;
import org.tuiapp.core.LinkState;
import org.tuiapp.core.MapData;
import org.tuiapp.core.MapSources;
import org.tuiapp.core.OfflinePanel;
import org.tuiapp.core.ReplaySource;
import org.tuiapp.core.Settings;
import org.tuiapp.core.SettingsPanel;
import org.tuiapp.core.ViewState;
import org.tuiapp.core.WriteRequest;

/**
 * Thin shell: one DriveView fed by the comma (address set) or the bundled synthetic replay + demo map (no address).
 * Settings: long-press -> core-drawn panel; persisted in SharedPreferences as flat strings (core Settings).
 * Read-only unless an add-on build is installed AND the user allowed changes in that add-on (docs/ADDONS.md).
 */
public final class MainActivity extends Activity implements DriveView.Host {
    static final String PREFS = "tui";
    /** suggestion shown in the address dialog only (StarPilot Galaxy on the comma's hotspot); never used automatically */
    static final String SUGGESTED_ADDR = "172.31.254.1:8082";

    private final HudModel model = new HudModel();
    private Settings settings;
    private Addons addons = Addons.NONE;
    private MapData demoMap;
    private DataSource source;
    private DriveView view;
    private MapEngine engine;
    private DeviceGps deviceGps;
    private final java.util.List<HeadUnitAdapter> adapters = new java.util.ArrayList<HeadUnitAdapter>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        settings = load();
        settings.deviceGpsSupported = DeviceGps.supported(this);
        deviceGps = new DeviceGps(this, model);
        // test hook: am start ... --es tui.address <address | demo> (sets and saves the data source; "demo" = demo drive)
        if (getIntent() != null && getIntent().hasExtra("tui.address")) {
            String a = getIntent().getStringExtra("tui.address"); a = a == null ? "" : a.trim();
            settings.commaAddress = "demo".equals(a) ? "" : a;
        }
        addons = loadAddons();
        model.setAddons(addons);
        save();
        demoMap = loadDemoMap();
        view = new DriveView(this, model, this);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        engine = loadEngine();
        if (engine != null) {
            try {
                View mv = engine.create(this, settings.styleUrl(), MapSources.USER_AGENT, MapSources.MAX_PARALLEL);
                root.addView(mv, new android.widget.FrameLayout.LayoutParams(1, 1));
                mv.setVisibility(View.INVISIBLE);
                view.setEngine(engine, mv);
            } catch (Throwable t) { android.util.Log.w("T-UI", "map engine failed, using the built-in map", t); engine = null; }
        }
        root.addView(view, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        applySettings();
        for (Object o : addons.others()) if (o instanceof HeadUnitAdapter) {
            HeadUnitAdapter h = (HeadUnitAdapter) o;
            try { h.attach(this, settings, adapterActions); adapters.add(h); }
            catch (Throwable t) { android.util.Log.w("T-UI", "head-unit adapter " + h.id() + " failed", t); }
        }
    }

    @Override protected void onStart() { super.onStart(); if (engine != null) engine.onStart(); for (HeadUnitAdapter h : adapters) h.onStart(); }
    @Override protected void onResume() { super.onResume(); if (engine != null) engine.onResume(); immersive(); startSource(); updateDeviceGps(false); }
    @Override protected void onPause() { stopSource(); deviceGps.stop(); if (engine != null) engine.onPause(); super.onPause(); }
    @Override protected void onStop() { for (HeadUnitAdapter h : adapters) h.onStop(); if (engine != null) engine.onStop(); super.onStop(); }
    @Override protected void onDestroy() { for (HeadUnitAdapter h : adapters) h.detach(); if (engine != null) engine.onDestroy(); super.onDestroy(); }
    @Override public boolean onKeyDown(int keyCode, android.view.KeyEvent e) {
        for (HeadUnitAdapter h : adapters) if (h.onKey(keyCode, e)) return true;
        return super.onKeyDown(keyCode, e);
    }

    private final HeadUnitAdapter.Actions adapterActions = new HeadUnitAdapter.Actions() {
        public void openSettings() { if (!view.panelOpen()) view.performLongClick(); }
        public void closeOverlays() { if (view.offlineOpen()) view.closeOffline(); if (view.panelOpen()) view.closePanel(); }
        public void nextLayout() {
            org.tuiapp.core.Layout.Mode[] m = org.tuiapp.core.Layout.Mode.values();
            settings.layout = m[(settings.layout.ordinal() + 1) % m.length]; save(); applySettings();
        }
        public void yieldScreen(boolean y) { view.setVisibility(y ? View.INVISIBLE : View.VISIBLE); }
        public void setNight(Boolean night) {
            settings.theme = night == null ? Settings.ThemePref.AUTO : night ? Settings.ThemePref.DARK : Settings.ThemePref.LIGHT; applySettings();
        }
    };
    @Override public void onLowMemory() { super.onLowMemory(); if (engine != null) engine.onLowMemory(); }
    @Override public void onBackPressed() {
        if (view.offlineOpen()) view.closeOffline(); else if (view.panelOpen()) view.closePanel(); else super.onBackPressed();
    }

    // ------------------------------------------------------------------ optional device GPS (opt-in)
    static final int REQ_LOCATION = 41;
    /** start / stop the device GPS per the setting; ask for the permission only right after the user enabled it */
    private void updateDeviceGps(boolean userAction) {
        if (!settings.deviceGpsSupported || !settings.useDeviceGps) { deviceGps.stop(); return; }
        if (DeviceGps.granted(this)) { deviceGps.start(); return; }
        if (userAction && android.os.Build.VERSION.SDK_INT >= 23) DeviceGps.request(this, REQ_LOCATION);
    }
    /** Activity callback (API 23+; no @Override so the sources also compile for Android 4.2) */
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code != REQ_LOCATION) return;
        if (results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) deviceGps.start();
        else { settings.useDeviceGps = false; save(); applySettings(); }   // declined: turn the setting back off
    }

    // ------------------------------------------------------------------ map engine + offline screen
    private MapEngine loadEngine() {
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new InputStreamReader(getAssets().open("map-engines.txt"), "UTF-8"));
            String c = br.readLine(); br.close();
            return c == null ? null : (MapEngine) Class.forName(c.trim()).getDeclaredConstructor().newInstance();
        } catch (Throwable none) { return null; }   // lean build: no engine, built-in renderer
    }

    @Override public boolean networkUp() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            android.net.NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) { return false; }
    }

    @Override public void onOfflineHit(OfflinePanel.Hit h, ViewState v) {
        final OfflinePanel p = view.offline;
        switch (h.act) {
            case DONE: view.closeOffline(); immersive(); break;
            case MODE_MOVE: p.drawMode = false; break;
            case MODE_DRAW: p.drawMode = true; break;
            case PRESET_VISIBLE: p.selectVisible(); break;
            case PRESET_10: if (v != null && v.hasFix()) p.selectAround(v.lat, v.lon, 10000); break;
            case PRESET_50: if (v != null && v.hasFix()) p.selectAround(v.lat, v.lon, 50000); break;
            case DETAIL: p.detail = (int) h.arg; break;
            case DOWNLOAD: {
                String why = p.refusal();
                if (why != null || engine == null) { p.note = why == null ? "No map engine in this build" : why; break; }
                long[] lim = MapSources.limits(settings.mapSource, settings.offlineCapMb);
                engine.download(OfflinePanel.areaName(p.selection, p.maxZoom()), p.selection, OfflinePanel.MIN_Z, p.maxZoom(), lim[0], lim[1]);
                p.note = ""; p.selection = null;
                break;
            }
            case PAUSE: if (engine != null) engine.pause(h.arg); break;
            case RESUME: if (engine != null) engine.resume(h.arg); break;
            case DELETE: {
                final long id = h.arg;
                new AlertDialog.Builder(this).setMessage("Delete this offline area?")
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { if (engine != null) engine.delete(id); } })
                        .setNegativeButton("Cancel", null).show();
                break;
            }
            default: break;
        }
    }

    // ------------------------------------------------------------------ settings
    private Settings load() {
        Map<String, String> m = new HashMap<String, String>();
        // build defaults (assets/defaults.properties, e.g. the preview build's data source); saved settings win
        try {
            java.util.Properties d = new java.util.Properties();
            InputStream in = getAssets().open("defaults.properties"); d.load(in); in.close();
            for (String k : d.stringPropertyNames()) m.put(k, d.getProperty(k));
        } catch (Exception none) { }
        for (Map.Entry<String, ?> e : getSharedPreferences(PREFS, MODE_PRIVATE).getAll().entrySet()) m.put(e.getKey(), String.valueOf(e.getValue()));
        return Settings.from(m);
    }
    private void save() {
        SharedPreferences.Editor ed = getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear();
        for (Map.Entry<String, String> e : settings.toMap().entrySet()) ed.putString(e.getKey(), e.getValue());
        ed.apply();
    }
    private void applySettings() {
        if (settings.keepScreenOn) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view.configure(settings, addons, settings.commaAddress.isEmpty() ? demoMap : null);
    }

    @Override public void onPanelHit(SettingsPanel.Hit h, final ViewState v) {
        long now = DataSource.SYSTEM.now();
        if (SettingsPanel.DONE.equals(h.section)) { view.closePanel(); immersive(); return; }
        if (SettingsPanel.CORE.equals(h.section)) {
            if (Settings.K_OFFLINE.equals(h.key)) { view.openOffline(); return; }
            if (Settings.K_EGO_COLOR.equals(h.key) && h.option == SettingsPanel.EGO_CUSTOM) { editEgoColor(); return; }
            if (h.text && Settings.K_MAP_URL.equals(h.key)) { editStyleUrl(); return; }
            if (h.text) { editAddress(); return; }
            if (SettingsPanel.apply(settings, h.key, h.option)) { save(); applySettings(); if (engine != null) engine.setStyleUrl(settings.styleUrl()); }
            if (Settings.K_DEVICE_GPS.equals(h.key)) updateDeviceGps(true);
            return;
        }
        final Addon a = addons.byId(h.section);
        if (a == null || v == null) return;
        final WriteRequest w = a.select(h.key, h.option, v, settings, now);
        save();
        if (w != null) send(a, w);
    }

    private void send(final Addon a, final WriteRequest w) {
        final DataSource s = source;
        new Thread(new Runnable() { public void run() {
            int code = -1; String body = "";
            try {
                if (s instanceof CommaHttpSource) {
                    CommaHttpSource c = (CommaHttpSource) s;
                    c.setWritesEnabled(true);   // only reached through an add-on that passed its own guards
                    try { String[] r = c.post(w.path, w.jsonBody); code = Integer.parseInt(r[0]); body = r[1]; }
                    finally { c.setWritesEnabled(false); }
                }
            } catch (Exception e) { body = e.toString(); }
            a.onWriteResult(w, code, body, DataSource.SYSTEM.now());
        }}, "tui-write").start();
    }

    private void editEgoColor() {
        final EditText c = new EditText(this);
        c.setSingleLine(true); c.setInputType(InputType.TYPE_CLASS_TEXT);
        c.setText(settings.egoColor == 0 ? "" : Settings.hex(settings.egoColor)); c.setHint("#1F3F7A or 31,63,122");
        new AlertDialog.Builder(this).setTitle("My car colour")
                .setMessage("Hex (#RRGGBB) or R,G,B (0-255). Empty = theme colour. Only your car changes; other cars stay neutral.")
                .setView(c)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int which) {
                    String v = c.getText().toString().trim();
                    int col = Settings.parseColor(v);
                    if (!v.isEmpty() && col == 0) { android.widget.Toast.makeText(MainActivity.this, "Not a colour: " + v, android.widget.Toast.LENGTH_LONG).show(); return; }
                    settings.egoColor = col; save(); applySettings(); immersive();
                }})
                .setNegativeButton("Cancel", null).show();
    }

    private void editStyleUrl() {
        final EditText u = new EditText(this);
        u.setSingleLine(true); u.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        u.setText(settings.mapCustomUrl); u.setHint("https://.../style.json");
        new AlertDialog.Builder(this).setTitle("Custom map style URL")
                .setMessage("A MapLibre style JSON. Check the tile server's usage policy; offline areas are capped at " + settings.offlineCapMb + " MB each.")
                .setView(u)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int which) {
                    settings.mapCustomUrl = u.getText().toString().trim(); save(); applySettings(); if (engine != null) engine.setStyleUrl(settings.styleUrl()); immersive();
                }})
                .setNegativeButton("Cancel", null).show();
    }

    private void editAddress() {
        final EditText addr = new EditText(this);
        addr.setSingleLine(true); addr.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addr.setText(settings.commaAddress); addr.setHint(SUGGESTED_ADDR);
        new AlertDialog.Builder(this)
                .setTitle("Comma address (host:port)")
                .setMessage("On the comma's hotspot this is usually " + SUGGESTED_ADDR + ". Leave empty for the demo drive.")
                .setView(addr)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int which) {
                    settings.commaAddress = addr.getText().toString().trim(); save(); applySettings(); startSource(); immersive();
                }})
                .setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------------ sources, add-ons, assets
    private void startSource() {
        stopSource();
        model.reset();
        try {
            org.tuiapp.core.SourceProvider sp = addons.sourceFor(settings.commaAddress);
            if (sp != null) {
                source = sp.open(settings.commaAddress.substring(settings.commaAddress.indexOf(':') + 1), null);
                model.setDemo(false);
            } else if (settings.commaAddress.isEmpty()) {
                source = ReplaySource.read(new InputStreamReader(getAssets().open("demo.jsonl"), "UTF-8"), true, null);
                model.setDemo(true);
            } else {
                source = new CommaHttpSource(settings.commaAddress, null);
                model.setDemo(false);
            }
            source.start(model);
        } catch (Exception e) {
            model.onLink(LinkState.NOT_SET, e.toString());
        }
    }
    private void stopSource() { if (source != null) { source.stop(); source = null; } }

    private Addons loadAddons() {
        try {
            List<String> err = new ArrayList<String>();
            Addons a = Addons.load(new InputStreamReader(getAssets().open("addons.txt"), "UTF-8"), err);
            for (String e : err) android.util.Log.w("T-UI", "add-on not loaded: " + e);
            return a;
        } catch (Exception none) { return Addons.NONE; }   // default build: no addons.txt
    }

    private MapData loadDemoMap() {
        try {
            InputStream in = getAssets().open("demo-map.json");
            ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return MapData.parse(bo.toString("UTF-8"));
        } catch (Exception e) { return null; }
    }

    private void immersive() {
        int f = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (android.os.Build.VERSION.SDK_INT >= 19) f |= 0x00001000;   // SYSTEM_UI_FLAG_IMMERSIVE_STICKY (API 19); API 17 head units skip it
        view.setSystemUiVisibility(f);
    }
}
