package org.tuiapp.core;

/**
 * The ONE place for the product name and ids. tools/build.sh reads APP_ID, APP_NAME, VERSION_CODE and VERSION_NAME
 * from this file (sed on the `= "..."` / `= n;` lines), so a rename is a one-line change here.
 * Keep each constant on its own line in the exact form below.
 * Public name "T-UI" (chosen 2026-11-22); repository slug suggestion "t-ui"; distributed as APKs on GitHub Releases only. Java packages stay org.tuiapp.* (internal).
 */
public final class Brand {
    public static final String APP_NAME = "T-UI";
    public static final String APP_ID = "org.tuidrive.app";
    public static final String VERSION_NAME = "0.1.1-alpha";
    public static final int VERSION_CODE = 2;
    public static final String TAGLINE = "A driving display for openpilot/StarPilot";
    /** HTTP User-Agent sent to the comma (no device or user identifiers). */
    public static final String USER_AGENT = APP_NAME + "/" + VERSION_NAME;

    private Brand() {}
}
