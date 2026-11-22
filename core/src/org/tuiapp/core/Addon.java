package org.tuiapp.core;

import java.util.List;
import java.util.Map;

/**
 * Extension point for optional, car- or owner-specific features (docs/ADDONS.md). The default build ships with NO
 * add-ons: the registry is empty, nothing extra is drawn, nothing extra is ever sent to the comma.
 * An add-on can: read extra /api/hud fields (onHud), offer settings rows (settingRows / select), and show small
 * cards on the drive screen (cards). It cannot draw over the alert banner or change openpilot alerts.
 * Writes: select() may return a WriteRequest; the app sends it only if the user has enabled writes for that add-on
 * AND the data source supports writes. Core itself never builds a write request.
 * All calls arrive on the UI/render thread except onHud (source thread): implementations synchronize themselves.
 */
public interface Addon {
    /** stable id, lowercase-hyphen (settings keys are "addon.&lt;id&gt;.&lt;key&gt;") */
    String id();
    /** section title shown in settings and on screenshots (should say what the add-on is and that it is optional) */
    String title();
    /** every /api/hud frame, raw parsed JSON (read-only) */
    void onHud(Map<String, Object> raw, long now);
    /** settings rows for this add-on (may be empty); called each time the settings panel draws */
    List<SettingRow> settingRows(ViewState v, Settings s, long now);
    /** the user tapped option `option` of row `key`; return a write request, or null (local change / refused) */
    WriteRequest select(String key, int option, ViewState v, Settings s, long now);
    /** result of a write sent for this add-on (status = HTTP code, -1 = transport error) */
    void onWriteResult(WriteRequest r, int status, String body, long now);
    /** small cards for the drive screen (may be empty) */
    List<AddonCard> cards(ViewState v, Settings s, long now);
}
