package org.tuiapp.drive;

import android.app.Activity;
import android.view.KeyEvent;

import org.tuiapp.core.Settings;

/**
 * Head-unit enhancement module (optional, one per device family, shipped as an add-on; docs/ADDONS.md). Listed by
 * class name in assets/addons.txt; the default app has none. Lets a device family add what its hardware offers without
 * touching the core: auto-start at ignition, steering-wheel / hard keys, vendor broadcasts (day / night, reverse camera),
 * a second display. All calls on the UI thread. Must never write to the comma or hide openpilot alerts.
 */
public interface HeadUnitAdapter {
    /** what the adapter may ask the app to do */
    interface Actions {
        void openSettings();
        void closeOverlays();
        /** cycle the layout mode (auto / full scene / split) */
        void nextLayout();
        /** the device's camera / reverse view took over: pause heavy drawing until resumed(true) */
        void yieldScreen(boolean yield);
        /** night mode from the vehicle (null = follow the system) */
        void setNight(Boolean night);
    }
    /** short id for logs and settings, e.g. "generic-android" */
    String id();
    void attach(Activity activity, Settings settings, Actions actions);
    /** a key the activity received (steering-wheel keys arrive as media / custom key codes on many units); true = handled */
    boolean onKey(int keyCode, KeyEvent event);
    void onStart();
    void onStop();
    void detach();
}
