package org.tuiapp.map.maplibre;

import android.app.Activity;
import android.view.View;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapLibreMapOptions;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;
import org.maplibre.android.module.http.HttpRequestUtil;
import org.maplibre.android.offline.OfflineManager;
import org.maplibre.android.offline.OfflineRegion;
import org.maplibre.android.offline.OfflineRegionError;
import org.maplibre.android.offline.OfflineRegionStatus;
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;

import org.tuiapp.core.MapChooser;
import org.tuiapp.core.OfflineModel;
import org.tuiapp.core.RouteGeo;
import org.tuiapp.core.ViewState;
import org.tuiapp.drive.MapEngine;

/**
 * MapLibre Native (OpenGL ES build) behind the TUI drive view. Compiled only in the Gradle flavour.
 * Requests: one OkHttp client for everything MapLibre fetches, with a generic app User-Agent (no user, car or device
 * data) and at most maxParallel requests (the free tile source allows 4). Offline areas use MapLibre's OfflineManager.
 * NOT verified on a device yet: compile-checked against MapLibre 13.7.1 only.
 */
public final class MapLibreEngine implements MapEngine {
    static final String SRC_ROUTE = "tui-route", SRC_PUCK = "tui-puck";
    private Activity act;
    private MapView view;
    private MapLibreMap map;
    private String styleUrl;
    private volatile int styleState = MapChooser.STYLE_LOADING;
    private boolean browsing;
    private int routeGen = -2;
    private long puckAt;
    private final OfflineModel model = new OfflineModel();
    private final Map<Long, OfflineRegion> regions = new HashMap<Long, OfflineRegion>();
    private final Map<Long, long[]> limits = new HashMap<Long, long[]>();
    private float density = 1;

    @Override public View create(Activity a, String url, final String userAgent, int maxParallel) {
        act = a; styleUrl = url; density = a.getResources().getDisplayMetrics().density;
        Dispatcher d = new Dispatcher(); d.setMaxRequests(maxParallel); d.setMaxRequestsPerHost(maxParallel);
        OkHttpClient http = new OkHttpClient.Builder().dispatcher(d).connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(new Interceptor() {
                    @Override public Response intercept(Chain c) throws IOException {
                        return c.proceed(c.request().newBuilder().header("User-Agent", userAgent).build());
                    }
                }).build();
        MapLibre.getInstance(a.getApplicationContext());
        HttpRequestUtil.setOkHttpClient(http);
        model.available = true;
        MapLibreMapOptions o = MapLibreMapOptions.createFromAttributes(a).textureMode(true).translucentTextureSurface(true)
                .logoEnabled(false).attributionEnabled(false).compassEnabled(false);   // attribution is drawn by the TUI overlay
        view = new MapView(a, o);
        view.onCreate(null);
        view.addOnDidFailLoadingMapListener(new MapView.OnDidFailLoadingMapListener() {
            @Override public void onDidFailLoadingMap(String msg) { styleState = MapChooser.STYLE_FAILED; }
        });
        view.getMapAsync(new OnMapReadyCallback() {
            @Override public void onMapReady(MapLibreMap m) { map = m; map.getUiSettings().setAllGesturesEnabled(browsing); loadStyle(); }
        });
        refreshOffline();
        return view;
    }

    private void loadStyle() {
        if (map == null || styleUrl == null || styleUrl.isEmpty()) { styleState = MapChooser.STYLE_FAILED; return; }
        styleState = MapChooser.STYLE_LOADING; routeGen = -2;
        map.setStyle(new Style.Builder().fromUri(styleUrl), new Style.OnStyleLoaded() {
            @Override public void onStyleLoaded(Style s) {
                styleState = MapChooser.STYLE_LOADED;
                s.addSource(new GeoJsonSource(SRC_ROUTE));
                s.addSource(new GeoJsonSource(SRC_PUCK));
                s.addLayer(new LineLayer("tui-route-casing", SRC_ROUTE).withProperties(PropertyFactory.lineColor("#FFFFFF"),
                        PropertyFactory.lineWidth(9f), PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round")));
                s.addLayer(new LineLayer("tui-route-line", SRC_ROUTE).withProperties(PropertyFactory.lineColor("#2F74D0"),
                        PropertyFactory.lineWidth(6f), PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round")));
                s.addLayer(new CircleLayer("tui-puck", SRC_PUCK).withProperties(PropertyFactory.circleColor("#2F74D0"),
                        PropertyFactory.circleRadius(9f), PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(3f)));
            }
        });
    }

    @Override public void setStyleUrl(String url) { if (url != null && !url.equals(styleUrl)) { styleUrl = url; loadStyle(); } }
    @Override public void setConnected(boolean c) { MapLibre.setConnected(c); }
    @Override public int styleState() { return styleState; }

    @Override public void follow(ViewState v) {
        if (map == null || browsing || !v.hasFix()) return;
        long now = System.currentTimeMillis();
        if (now - puckAt < 100) return;   // <= 10 camera updates per second
        puckAt = now;
        double spd = Double.isNaN(v.speedMps) ? 0 : v.speedMps;
        double zoom = spd > 25 ? 14.5 : spd > 15 ? 15.2 : 16.0;
        CameraPosition.Builder cb = new CameraPosition.Builder().target(new LatLng(v.lat, v.lon)).zoom(zoom).tilt(0);
        if (!Double.isNaN(v.bearingDeg)) cb.bearing(v.bearingDeg);
        map.easeCamera(CameraUpdateFactory.newCameraPosition(cb.build()), 250);
        Style s = map.getStyle();
        if (s == null || styleState != MapChooser.STYLE_LOADED) return;
        GeoJsonSource puck = (GeoJsonSource) s.getSource(SRC_PUCK);
        if (puck != null) puck.setGeoJson(Feature.fromGeometry(Point.fromLngLat(v.lon, v.lat)));
        RouteGeo r = v.route;
        int gen = r == null ? -1 : r.gen;
        if (gen != routeGen) {
            routeGen = gen;
            GeoJsonSource rs = (GeoJsonSource) s.getSource(SRC_ROUTE);
            if (rs != null) {
                List<Point> pts = new ArrayList<Point>();
                if (r != null) for (int i = 0; i < r.size(); i++) pts.add(Point.fromLngLat(r.lon[i], r.lat[i]));
                rs.setGeoJson(FeatureCollection.fromFeatures(pts.size() >= 2 ? new Feature[]{Feature.fromGeometry(LineString.fromLngLats(pts))} : new Feature[0]));
            }
        }
    }

    @Override public void setBrowsing(boolean b, double lat, double lon, double zoom) {
        browsing = b;
        if (map == null) return;
        map.getUiSettings().setAllGesturesEnabled(b);
        map.getUiSettings().setRotateGesturesEnabled(false);
        map.getUiSettings().setTiltGesturesEnabled(false);
        if (b) map.moveCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder()
                .target(new LatLng(lat, lon)).zoom(zoom).bearing(0).tilt(0).build()));
    }

    @Override public double[] camera() {
        if (map == null) return null;
        CameraPosition c = map.getCameraPosition();
        return c == null || c.target == null ? null : new double[]{c.target.getLatitude(), c.target.getLongitude(), c.zoom};
    }

    // ------------------------------------------------------------------ offline areas
    @Override public OfflineModel offline() { return model; }

    @Override public void refreshOffline() {
        OfflineManager.Companion.getInstance(act).listOfflineRegions(new OfflineManager.ListOfflineRegionsCallback() {
            @Override public void onList(OfflineRegion[] list) { if (list != null) for (OfflineRegion r : list) track(r, null, false); }
            @Override public void onError(String e) { model.message = "Offline list failed: " + e; }
        });
    }

    @Override public void download(final String name, double[] b, int minZ, int maxZ, final long maxTiles, final long maxBytes) {
        OfflineTilePyramidRegionDefinition def = new OfflineTilePyramidRegionDefinition(styleUrl,
                LatLngBounds.from(b[0], b[1], b[2], b[3]), minZ, maxZ, density);
        byte[] meta;
        try { meta = ("{\"name\":\"" + name.replace("\"", "'") + "\"}").getBytes("UTF-8"); } catch (java.io.UnsupportedEncodingException e) { meta = new byte[0]; }
        OfflineManager.Companion.getInstance(act).createOfflineRegion(def, meta, new OfflineManager.CreateOfflineRegionCallback() {
            @Override public void onCreate(OfflineRegion r) { limits.put(r.getId(), new long[]{maxTiles, maxBytes}); track(r, name, true); }
            @Override public void onError(String e) { model.message = "Could not start the download: " + e; }
        });
    }

    private void track(final OfflineRegion r, String name, boolean start) {
        final long id = r.getId();
        regions.put(id, r);
        final OfflineModel.Region m = new OfflineModel.Region();
        m.id = id; m.name = name != null ? name : nameOf(r.getMetadata());
        if (r.getDefinition() instanceof OfflineTilePyramidRegionDefinition) {
            OfflineTilePyramidRegionDefinition d = (OfflineTilePyramidRegionDefinition) r.getDefinition();
            LatLngBounds bb = d.getBounds();
            m.bounds = new double[]{bb.getLatNorth(), bb.getLonEast(), bb.getLatSouth(), bb.getLonWest()};
        }
        m.state = start ? OfflineModel.State.DOWNLOADING : OfflineModel.State.PAUSED;
        model.put(m);
        r.setObserver(new OfflineRegion.OfflineRegionObserver() {
            @Override public void onStatusChanged(OfflineRegionStatus s) {
                m.doneRes = s.getCompletedResourceCount(); m.needRes = s.getRequiredResourceCount();
                m.doneTiles = s.getCompletedTileCount(); m.bytes = s.getCompletedResourceSize();
                if (s.isComplete()) { m.state = OfflineModel.State.READY; r.setDownloadState(OfflineRegion.STATE_INACTIVE); }
                else m.state = s.getDownloadState() == OfflineRegion.STATE_ACTIVE ? OfflineModel.State.DOWNLOADING : OfflineModel.State.PAUSED;
                long[] lim = limits.get(id);
                if (lim != null && !s.isComplete() && (m.doneTiles > lim[0] || m.bytes > lim[1] * 12 / 10)) {
                    r.setDownloadState(OfflineRegion.STATE_INACTIVE); m.state = OfflineModel.State.ERROR; m.error = "stopped: over the source's size limit";
                }
                model.put(m);
            }
            @Override public void onError(OfflineRegionError e) {
                if (OfflineRegionError.REASON_CONNECTION.equals(e.getReason())) { m.error = "no connection, retrying"; model.put(m); return; }
                m.state = OfflineModel.State.ERROR; m.error = e.getReason(); model.put(m);
            }
            @Override public void mapboxTileCountLimitExceeded(long limit) {
                r.setDownloadState(OfflineRegion.STATE_INACTIVE); m.state = OfflineModel.State.ERROR; m.error = "tile limit"; model.put(m);
            }
        });
        if (start) r.setDownloadState(OfflineRegion.STATE_ACTIVE);
        else r.getStatus(new OfflineRegion.OfflineRegionStatusCallback() {
            @Override public void onStatus(OfflineRegionStatus s) {
                m.doneRes = s.getCompletedResourceCount(); m.needRes = s.getRequiredResourceCount(); m.bytes = s.getCompletedResourceSize();
                m.state = s.isComplete() ? OfflineModel.State.READY : OfflineModel.State.PAUSED; model.put(m);
            }
            @Override public void onError(String e) { }
        });
    }

    private static String nameOf(byte[] meta) {
        try { String s = new String(meta, "UTF-8"); int i = s.indexOf("\"name\":\""); return i < 0 ? "Offline area" : s.substring(i + 8, s.indexOf('"', i + 8)); }
        catch (Exception e) { return "Offline area"; }
    }

    @Override public void pause(long id) { OfflineRegion r = regions.get(id); if (r != null) r.setDownloadState(OfflineRegion.STATE_INACTIVE); }
    @Override public void resume(long id) { OfflineRegion r = regions.get(id); if (r != null) r.setDownloadState(OfflineRegion.STATE_ACTIVE); }
    @Override public void delete(final long id) {
        OfflineRegion r = regions.get(id);
        if (r == null) return;
        r.setDownloadState(OfflineRegion.STATE_INACTIVE);
        r.delete(new OfflineRegion.OfflineRegionDeleteCallback() {
            @Override public void onDelete() { regions.remove(id); model.remove(id); }
            @Override public void onError(String e) { model.message = "Delete failed: " + e; }
        });
    }

    @Override public void onStart() { if (view != null) view.onStart(); }
    @Override public void onResume() { if (view != null) view.onResume(); }
    @Override public void onPause() { if (view != null) view.onPause(); }
    @Override public void onStop() { if (view != null) view.onStop(); }
    @Override public void onDestroy() { if (view != null) view.onDestroy(); }
    @Override public void onLowMemory() { if (view != null) view.onLowMemory(); }
}
