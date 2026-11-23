package org.tuiapp.drive;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import org.tuiapp.core.Addons;
import org.tuiapp.core.DataSource;
import org.tuiapp.core.DriveRenderer;
import org.tuiapp.core.Geo;
import org.tuiapp.core.HudModel;
import org.tuiapp.core.Layout;
import org.tuiapp.core.MapChooser;
import org.tuiapp.core.MapData;
import org.tuiapp.core.MapSources;
import org.tuiapp.core.OfflineModel;
import org.tuiapp.core.OfflinePanel;
import org.tuiapp.core.Settings;
import org.tuiapp.core.SettingsPanel;
import org.tuiapp.core.Smoother;
import org.tuiapp.core.Theme;
import org.tuiapp.core.ViewState;

/**
 * The single full-screen view: core layout + renderer + easing; the core-drawn settings panel (long-press) and offline
 * maps screen. With a map engine (MapLibre flavour) the engine's view sits BEHIND this one and the map pane / offline
 * viewport are drawn transparent (hardware layer + CLEAR), so the cards stay on top of the live map.
 */
final class DriveView extends View {
    interface Host {
        void onPanelHit(SettingsPanel.Hit h, ViewState v);
        void onOfflineHit(OfflinePanel.Hit h, ViewState v);
        boolean networkUp();
        void onInfoHit(String id);
        String mapBuildLine();
    }

    static final long FRAME_MS = 33;
    private final HudModel model;
    private final DriveRenderer renderer = new DriveRenderer();
    private final SettingsPanel panel = new SettingsPanel();
    final OfflinePanel offline = new OfflinePanel();
    private final org.tuiapp.core.InfoPanel info = new org.tuiapp.core.InfoPanel();
    private boolean aboutOpen;
    private final OfflineModel noEngineModel = new OfflineModel();
    private final Smoother smoother = new Smoother();
    private final AndroidGfx gfx = new AndroidGfx();
    private final Host host;
    private Layout layout;
    private Settings settings = new Settings();
    private Addons addons = Addons.NONE;
    private MapData map;
    private MapEngine engine; private View engineView;
    private boolean panelOpen, offlineOpen, connected = true;
    private ViewState last;
    private float downX, downY, lastY; private boolean dragged, mapTouch;
    private int[] engineRect = new int[4];
    // own camera for the offline screen when there is no engine (preview only)
    private double camLat, camLon, camZoom = 11;

    DriveView(Context c, HudModel model, Host host) {
        super(c);
        this.model = model; this.host = host;
        setLayerType(LAYER_TYPE_HARDWARE, null);   // CLEAR leaves real holes for the map view behind
        setOnLongClickListener(new OnLongClickListener() { public boolean onLongClick(View v) { if (!panelOpen && !offlineOpen && !infoShown()) { panelOpen = true; invalidate(); } return true; } });
    }

    void configure(Settings s, Addons a, MapData m) {
        settings = s; addons = a; map = m; layout = null; smoother.reset(); setKeepScreenOn(s.keepScreenOn);
        offline.sourceId = s.mapSource; offline.customCapMb = s.offlineCapMb;
        invalidate();
    }
    void setEngine(MapEngine e, View v) { engine = e; engineView = v; }
    void closePanel() { panelOpen = false; invalidate(); }
    boolean panelOpen() { return panelOpen; }
    void openOffline() {
        panelOpen = false; offlineOpen = true; offline.drawMode = false;
        ViewState v = last;
        double lat = v != null && v.hasFix() ? v.lat : 0, lon = v != null && v.hasFix() ? v.lon : 0;
        camLat = lat; camLon = lon; camZoom = v != null && v.hasFix() ? 11 : 2;
        if (engine != null) engine.setBrowsing(true, lat, lon, camZoom);
        invalidate();
    }
    void openAbout() { panelOpen = false; aboutOpen = true; invalidate(); }
    void closeAbout() { aboutOpen = false; invalidate(); }
    boolean aboutOpen() { return aboutOpen; }
    private boolean infoShown() { return aboutOpen || !settings.safetyAccepted; }

    void closeOffline() { offlineOpen = false; if (engine != null) engine.setBrowsing(false, 0, 0, 0); invalidate(); }
    boolean offlineOpen() { return offlineOpen; }
    OfflineModel offlineModel() { return engine != null ? engine.offline() : noEngineModel; }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { layout = null; }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (infoShown()) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) { String id = info.hit(e.getX(), e.getY()); if (id != null) host.onInfoHit(id); invalidate(); }
            return true;
        }
        if (offlineOpen) return offlineTouch(e);
        if (panelOpen) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: downY = lastY = e.getY(); dragged = false; break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getY() - downY) > 12 * getResources().getDisplayMetrics().density) dragged = true;
                    if (dragged) { panel.scrollBy(lastY - e.getY()); lastY = e.getY(); invalidate(); }
                    break;
                case MotionEvent.ACTION_UP:
                    if (!dragged) { SettingsPanel.Hit h = panel.hit(e.getX(), e.getY()); if (h != null) host.onPanelHit(h, last); }
                    invalidate();
                    break;
                default: break;
            }
            return true;
        }
        return super.onTouchEvent(e);
    }

    private boolean offlineTouch(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mapTouch = offline.inViewport(x, y);
            // move mode over the map: let the engine's view behind us take the gesture (pan / pinch)
            if (mapTouch && !offline.drawMode && engine != null) return false;
            downX = x; downY = y; dragged = false;
            return true;
        }
        if (mapTouch && offline.drawMode) {
            if (e.getActionMasked() == MotionEvent.ACTION_MOVE || e.getActionMasked() == MotionEvent.ACTION_UP) { offline.selectRect(downX, downY, x, y); invalidate(); }
            return true;
        }
        if (mapTouch && engine == null && e.getActionMasked() == MotionEvent.ACTION_MOVE && offline.viewport != null) {   // preview pan
            Geo.Viewport vp = offline.viewport.panBy(downX - x, downY - y); camLat = vp.lat; camLon = vp.lon; downX = x; downY = y; invalidate();
            return true;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_UP && !mapTouch) { OfflinePanel.Hit h = offline.hit(x, y); if (h != null) host.onOfflineHit(h, last); invalidate(); }
        return true;
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float density = getResources().getDisplayMetrics().density;
        if (layout == null || layout.w != w || layout.h != h)
            layout = Layout.compute(w, h, density, settings.layout, !settings.rightHandDrive);
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        Theme t = settings.theme(night);
        long now = DataSource.SYSTEM.now();
        ViewState v = model.snapshot(now);
        settings.applyTo(v);
        v.addonCards = addons.cards(v, settings, now);
        last = v;
        boolean net = host.networkUp();
        if (engine != null) {
            boolean c = MapChooser.engineConnected(settings.mapOnline, net);
            if (c != connected) { connected = c; engine.setConnected(c); }
        }
        if (infoShown()) {
            placeEngine(false, 0, 0, 0, 0);
            if (!settings.safetyAccepted) info.draw(gfx.on(canvas), layout, t, org.tuiapp.core.InfoPanel.Kind.SAFETY, org.tuiapp.core.InfoPanel.SAFETY);
            else info.draw(gfx.on(canvas), layout, t, org.tuiapp.core.InfoPanel.Kind.ABOUT, org.tuiapp.core.InfoPanel.about(host.mapBuildLine()));
            postInvalidateDelayed(FRAME_MS * 3);
            return;
        }
        if (offlineOpen) {
            float[] r = OfflinePanel.viewportRect(layout);
            double[] cam = engine != null ? engine.camera() : null;
            if (cam != null) { camLat = cam[0]; camLon = cam[1]; camZoom = cam[2]; }
            // MapLibre zoom counts 512 dp world tiles; the core viewport counts 512 px
            double pxZoom = camZoom + (engine != null ? Math.log(density) / Math.log(2) : 0);
            Geo.Viewport vp = new Geo.Viewport(camLat, camLon, pxZoom, r[0], r[1], r[2], r[3]);
            placeEngine(true, (int) r[0], (int) r[1], (int) r[2], (int) r[3]);
            offline.draw(gfx.on(canvas), layout, t, vp, engine != null, null, null, offlineModel(), v.hasFix(), v.lat, v.lon, v.metric());
            postInvalidateDelayed(FRAME_MS * 3);
            return;
        }
        MapChooser.Mode mode = MapChooser.choose(v.demo && map != null, engine != null, v.hasFix(), engine == null ? MapChooser.STYLE_LOADING : engine.styleState(),
                settings.mapOnline, net, engine != null && v.hasFix() && engine.offline().covers(v.lat, v.lon));
        ViewState shown = smoother.apply(v, now);
        if (engine != null) {
            placeEngine(mode.engine(), layout.map.x, layout.map.y, layout.map.w, layout.map.h);
            if (mode.engine()) engine.follow(shown);
        }
        renderer.draw(gfx.on(canvas), layout, shown, t, map, mode, MapSources.attribution(settings.mapSource));
        if (panelOpen) panel.draw(gfx, layout, t, settings, addons, v, now);
        postInvalidateDelayed(FRAME_MS);   // ~30 fps is plenty for 4-10 Hz data and spares cheap head units
    }

    /** size / show the engine view behind us (only touches layout when something changed) */
    private void placeEngine(boolean show, final int x, final int y, final int w, final int h) {
        if (engineView == null) return;
        final int vis = show ? VISIBLE : INVISIBLE;
        if (engineView.getVisibility() != vis) post(new Runnable() { public void run() { engineView.setVisibility(vis); } });
        if (!show || (engineRect[0] == x && engineRect[1] == y && engineRect[2] == w && engineRect[3] == h)) return;
        engineRect = new int[]{x, y, w, h};
        post(new Runnable() { public void run() {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h); lp.leftMargin = x; lp.topMargin = y;
            engineView.setLayoutParams(lp);
        }});
    }
}
