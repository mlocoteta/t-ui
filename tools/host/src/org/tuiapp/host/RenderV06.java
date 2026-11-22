package org.tuiapp.host;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.tuiapp.core.*;

/**
 * v0.6 host screenshots (v0.5 + one consistent tail-light colour + "My car colour"): one generic, rounded car silhouette for every car (no invented types or colours); only
 * data-driven states (brake lights, ego signal, grey when old, lean in a lane change). 800x480 and 1920x1080, light + night.
 * Usage: RenderV06 OUTDIR REPLAY.jsonl demo-map.json
 */
public final class RenderV06 {
    /** a synthetic multi-lane moment: lead closing (brake lights from data), cars left and right, one far car the comma
     *  marks as stopped, ego signalling left */
    static ViewState traffic(ViewState base) {
        ViewState v = base.copy();
        v.leadPresent = true; v.leadDRel = 17; v.leadYRel = 0.1; v.leadVRel = -2.2; v.leadTrackId = 21;
        v.tracks = new float[][]{{17, 0.1f, -2.2f, 21, 22, 0}, {24, 3.7f, 0.3f, 42, 27, 0}, {11, -3.6f, -0.4f, 63, 26, 0}, {45, -3.5f, -1.8f, 84, 0, 1}};
        v.blinker = null;   // no signal in this still (v0.5 had a made-up left blinker: the amber lamp looked like a mismatch)
        return v;
    }

    public static void main(String[] a) throws Exception {
        File dir = new File(a[0]); dir.mkdirs();
        String replay = a[1]; MapData map = RenderPngs.map(a.length > 2 ? a[2] : null);
        Map<String, ViewState> st = new LinkedHashMap<String, ViewState>();
        st.put("traffic", traffic(RenderPngs.at(replay, 30000)));
        st.put("engaged", RenderPngs.at(replay, 30000));
        st.put("lead-close-braking", RenderPngs.at(replay, 41000));
        ViewState lc = RenderPngs.at(replay, 47000).copy(); lc.nowMs = 47120;   // blink phase: signal on in the still
        st.put("lane-change", lc);
        st.put("takeover", RenderPngs.at(replay, 57200));
        st.put("stale", RenderPngs.at(replay, 53900));
        ViewState stopped = RenderPngs.at(replay, 30000).copy(); stopped.speedMps = 0; stopped.leadDRel = 6; stopped.leadVRel = 0; stopped.gear = "D";
        st.put("stopped-in-traffic", stopped);
        Object[][] sizes = {{"800x480", 800, 480, 1f}, {"1920x1080", 1920, 1080, 1.5f}};
        StringBuilder idx = new StringBuilder("# T-UI v0.6 cars + my car colour (host-rendered stills, synthetic data)\n\n"
                + "Tail lights: the same two colours on every car in both themes: unlit dim dark red, lit (braking) bright red with a glow.\n"
                + "Amber only for a turn signal from data (the lane-change stills).\n"
                + "My car colour: Settings -> My car colour (swatches or Custom hex / r,g,b); only our car changes.\n\n"
                + "No invented details. ONE generic car silhouette for every other car, ONE neutral colour (the comma\n"
                + "says nothing about type, size or paint). Less boxy than v0.2: rounded rear corners, tapered cabin and roof, sloped rear\n"
                + "glass, soft shadow, subtle shading. Only data-driven states: brake lights when the lead is close (< 1 s) or closing\n"
                + "faster than 1.5 m/s, or when the comma marks a car as stopped; our car's signal from the blinker / lane-change data;\n"
                + "grey when comma data is old; our car leans slightly during a lane change; cars follow the road's curve.\n\n"
                + "Start with `sheet-cars.png` and the two close-ups. Full set below (800x480 and 1920x1080, light and night).\n\n");
        List<BufferedImage> si = new ArrayList<BufferedImage>(); List<String> sc = new ArrayList<String>();
        int n = 0;
        for (Map.Entry<String, ViewState> e : st.entrySet()) for (Object[] z : sizes) for (String th : new String[]{"light", "night"}) {
            int w = (Integer) z[1], h = (Integer) z[2]; float d = (Float) z[3];
            BufferedImage img = RenderPngs.render(e.getValue(), w, h, d, Layout.Mode.AUTO, true, th.equals("night") ? Theme.NIGHT : Theme.DAY, map);
            String f = e.getKey() + "-" + z[0] + "-" + th + ".png";
            ImageIO.write(img, "png", new File(dir, f)); n++;
            idx.append("- ").append(f).append('\n');
            if (z[0].equals("800x480")) { si.add(img); sc.add(e.getKey() + " " + th); }
        }
        ImageIO.write(RenderPngs.sheet("T-UI v0.6: one generic rounded car, consistent tail lights, data-driven lights only", si, sc, 4, 420), "png", new File(dir, "sheet-cars.png")); n++;
        // close-up crop of the traffic still for judging the art
        BufferedImage big = RenderPngs.render(st.get("traffic"), 1920, 1080, 1.5f, Layout.Mode.FLOATING, true, Theme.DAY, map);
        ImageIO.write(big.getSubimage(480, 260, 960, 640), "png", new File(dir, "closeup-traffic-light.png")); n++;
        BufferedImage bigN = RenderPngs.render(st.get("traffic"), 1920, 1080, 1.5f, Layout.Mode.FLOATING, true, Theme.NIGHT, map);
        ImageIO.write(bigN.getSubimage(480, 260, 960, 640), "png", new File(dir, "closeup-traffic-night.png")); n++;
        idx.append("- closeup-traffic-light.png, closeup-traffic-night.png (crops of the 1920x1080 full-scene layout)\n");
        // my car colour: three sample colours on the traffic still + the settings picker
        idx.append("\n## My car colour\n\n");
        List<BufferedImage> ci = new ArrayList<BufferedImage>(); List<String> cc = new ArrayList<String>();
        int[] samples = {Settings.EGO_PRESETS[5], Settings.EGO_PRESETS[6], Settings.EGO_PRESETS[2]};
        String[] sn = {"blue", "red", "white"};
        for (int k = 0; k < samples.length; k++) for (Object[] z : sizes) for (String th : new String[]{"light", "night"}) {
            ViewState v = st.get("traffic").copy(); v.egoColor = samples[k];
            BufferedImage img = RenderPngs.render(v, (Integer) z[1], (Integer) z[2], (Float) z[3], Layout.Mode.AUTO, true, th.equals("night") ? Theme.NIGHT : Theme.DAY, map);
            String f = "mycar-" + sn[k] + "-" + z[0] + "-" + th + ".png";
            ImageIO.write(img, "png", new File(dir, f)); n++;
            idx.append("- ").append(f).append('\n');
            if (z[0].equals("800x480")) { ci.add(img); cc.add("my car " + sn[k] + " " + th); }
        }
        ViewState park = RenderPngs.at(replay, 3000);
        for (int k = 0; k < 2; k++) for (Object[] z : sizes) for (String th : new String[]{"light", "night"}) {
            Settings s = new Settings(); s.egoColor = k == 0 ? Settings.EGO_PRESETS[5] : 0xFF7A4A2C;   // preset swatch / a custom colour
            ViewState v = park.copy(); s.applyTo(v);
            BufferedImage img = RenderPngs.settings((Integer) z[1], (Integer) z[2], (Float) z[3], th.equals("night") ? Theme.NIGHT : Theme.DAY, s, Addons.NONE, v, map);
            String f = "settings-mycar-" + (k == 0 ? "preset-blue" : "custom-7A4A2C") + "-" + z[0] + "-" + th + ".png";
            ImageIO.write(img, "png", new File(dir, f)); n++;
            idx.append("- ").append(f).append('\n');
            if (z[0].equals("800x480") || th.equals("night")) { ci.add(img); cc.add("settings " + (k == 0 ? "preset blue" : "custom #7A4A2C") + " " + z[0] + " " + th); }
        }
        ImageIO.write(RenderPngs.sheet("T-UI v0.6: my car colour (only our car changes) + settings picker", ci, cc, 4, 420), "png", new File(dir, "sheet-mycar.png")); n++;
        idx.append("- sheet-mycar.png\n");
        try (PrintWriter pw = new PrintWriter(new File(dir, "index.md"), "UTF-8")) { pw.print(idx); }
        System.out.println("rendered " + n + " PNGs -> " + dir);
    }
}
