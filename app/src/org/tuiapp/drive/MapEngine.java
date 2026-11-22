package org.tuiapp.drive;

import android.app.Activity;
import android.view.View;

import org.tuiapp.core.OfflineModel;
import org.tuiapp.core.ViewState;

/**
 * A native map engine shown BEHIND the DriveView (which leaves the map pane transparent). Optional: the lean build
 * has none and uses the core built-in renderer. The MapLibre flavour lists its class in assets/map-engines.txt.
 * All calls on the UI thread.
 */
public interface MapEngine {
    /** create the map view; userAgent and maxParallel apply to every tile / style request */
    View create(Activity a, String styleUrl, String userAgent, int maxParallel);
    void setStyleUrl(String url);
    /** false = no network requests at all (offline areas and cache only) */
    void setConnected(boolean connected);
    /** MapChooser.STYLE_LOADING / LOADED / FAILED */
    int styleState();
    /** drive mode: follow the comma fix (heading-up), draw puck + route */
    void follow(ViewState v);
    /** offline screen: north-up, user pans / zooms; gestures on */
    void setBrowsing(boolean browsing, double lat, double lon, double zoom);
    /** {lat, lon, zoom} of the camera (MapLibre zoom: 512 dp world tiles) */
    double[] camera();
    OfflineModel offline();
    void refreshOffline();
    /** maxTiles / maxBytes: the source's per-area limits; the engine pauses a download that runs over them */
    void download(String name, double[] bounds, int minZ, int maxZ, long maxTiles, long maxBytes);
    void pause(long id);
    void resume(long id);
    void delete(long id);
    void onStart(); void onResume(); void onPause(); void onStop(); void onDestroy(); void onLowMemory();
}
