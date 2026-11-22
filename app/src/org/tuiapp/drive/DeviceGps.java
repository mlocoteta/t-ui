package org.tuiapp.drive;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import org.tuiapp.core.DataSource;
import org.tuiapp.core.HudModel;

/**
 * Optional fallback: this device's own GPS for the map, used only when the comma gives no position. Off by default;
 * the location permission is requested only when the user turns the setting on. Fixes stay on the device.
 * Only builds that declare ACCESS_FINE_LOCATION (the MapLibre build) offer it.
 */
final class DeviceGps implements LocationListener {
    static final String PERM = "android.permission.ACCESS_FINE_LOCATION";
    private final Context ctx; private final HudModel model;
    private LocationManager lm; private boolean running;

    DeviceGps(Context c, HudModel m) { ctx = c.getApplicationContext(); model = m; }

    /** the build declares the permission (MapLibre build) */
    static boolean supported(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_PERMISSIONS);
            if (pi.requestedPermissions != null) for (String p : pi.requestedPermissions) if (PERM.equals(p)) return true;
        } catch (Exception ignored) { }
        return false;
    }

    static boolean granted(Context c) {
        if (android.os.Build.VERSION.SDK_INT < 23) return true;   // install-time permission before Android 6
        try {   // Context.checkSelfPermission (API 23) by reflection: the same sources also build for Android 4.2
            Object r = Context.class.getMethod("checkSelfPermission", String.class).invoke(c, PERM);
            return r instanceof Integer && (Integer) r == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) { return false; }
    }

    /** Activity.requestPermissions (API 23) by reflection */
    static void request(android.app.Activity a, int code) {
        try { android.app.Activity.class.getMethod("requestPermissions", String[].class, int.class).invoke(a, new String[]{PERM}, code); }
        catch (Exception ignored) { }
    }

    void start() {
        if (running || !granted(ctx)) return;
        try {
            lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null || !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return;
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0f, this);
            running = true;
        } catch (SecurityException | IllegalArgumentException e) { running = false; }
    }

    void stop() {
        if (!running) return;
        try { lm.removeUpdates(this); } catch (Exception ignored) { }
        running = false;
    }

    @Override public void onLocationChanged(Location l) {
        model.onDeviceFix(l.getLatitude(), l.getLongitude(), l.hasBearing() ? l.getBearing() : Double.NaN, DataSource.SYSTEM.now());
    }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }
}
