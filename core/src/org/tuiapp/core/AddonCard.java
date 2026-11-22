package org.tuiapp.core;

/** A small pill an add-on shows on the drive screen (one line of text). */
public final class AddonCard {
    public static final int INFO = 0, WARN = 1, ACCENT = 2;
    public final String text; public final int kind;
    public AddonCard(String text, int kind) { this.text = text; this.kind = kind; }
}
