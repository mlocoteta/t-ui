package org.tuiapp.core;

/**
 * One settings row: a title, a set of options shown as chips (selected = highlighted), or a free-text value
 * (options == null: tapping asks the app for text input). note = small grey line under the row; warning = shown in
 * the caution colour. enabled = false draws the row greyed and taps do nothing.
 */
public final class SettingRow {
    public final String key, title;
    public final String[] options;
    public int selected = -1;
    public String value = "";
    public String note = "";
    public boolean warning, enabled = true;
    /** optional colour per option (draws the chip as a swatch; 0 = plain chip) */
    public int[] swatches;
    /** optional car preview in this colour (0 = none) drawn next to the title */
    public int previewColor;
    /** option index that is waiting for a second tap to confirm (-1 = none) */
    public int confirming = -1;

    public SettingRow(String key, String title, String[] options, int selected) {
        this.key = key; this.title = title; this.options = options; this.selected = selected;
    }
    public static SettingRow text(String key, String title, String value) {
        SettingRow r = new SettingRow(key, title, null, -1); r.value = value == null ? "" : value; return r;
    }
    public SettingRow note(String n, boolean warn) { note = n == null ? "" : n; warning = warn; return this; }
    public SettingRow enabled(boolean e) { enabled = e; return this; }
}
