package org.tuiapp.core;

/** Connection state of a DataSource, as shown to the driver. */
public enum LinkState {
    SEARCHING("Connecting"), LIVE("Live"), NO_COMMA("No comma"), NO_NETWORK("No network"), NOT_SET("Set comma address"), REPLAY("Replay");

    public final String label;
    LinkState(String label) { this.label = label; }
}
