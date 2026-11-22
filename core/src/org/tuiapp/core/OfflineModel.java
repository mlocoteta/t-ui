package org.tuiapp.core;

import java.util.ArrayList;
import java.util.List;

/** Offline map areas and their download state, filled by the map engine (or by tests / screenshots). Thread-safe. */
public final class OfflineModel {
    public enum State { QUEUED, DOWNLOADING, PAUSED, READY, ERROR }

    public static final class Region {
        public long id; public String name = ""; public double[] bounds; public int minZ, maxZ;
        public State state = State.QUEUED; public long doneRes, needRes, doneTiles, bytes; public String error = "";
        public float progress() { return needRes <= 0 ? 0f : Math.min(1f, doneRes / (float) needRes); }
        public Region copy() { Region r = new Region(); r.id = id; r.name = name; r.bounds = bounds; r.minZ = minZ; r.maxZ = maxZ; r.state = state;
            r.doneRes = doneRes; r.needRes = needRes; r.doneTiles = doneTiles; r.bytes = bytes; r.error = error; return r; }
    }

    private final List<Region> regions = new ArrayList<Region>();
    public volatile boolean available;   // an offline-capable map engine exists in this build
    public volatile String message = "";

    public synchronized List<Region> regions() { List<Region> l = new ArrayList<Region>(); for (Region r : regions) l.add(r.copy()); return l; }
    public synchronized void put(Region r) { for (int i = 0; i < regions.size(); i++) if (regions.get(i).id == r.id) { regions.set(i, r.copy()); return; } regions.add(r.copy()); }
    public synchronized void remove(long id) { for (int i = regions.size() - 1; i >= 0; i--) if (regions.get(i).id == id) regions.remove(i); }
    public synchronized long bytesUsed() { long b = 0; for (Region r : regions) b += r.bytes; return b; }
    public synchronized boolean covers(double lat, double lon) { for (Region r : regions) if (r.state == State.READY && r.bounds != null && Geo.contains(r.bounds, lat, lon)) return true; return false; }
    public synchronized boolean busy() { for (Region r : regions) if (r.state == State.DOWNLOADING || r.state == State.QUEUED) return true; return false; }
}
