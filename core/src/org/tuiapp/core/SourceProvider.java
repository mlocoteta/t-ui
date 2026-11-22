package org.tuiapp.core;

/**
 * Extra data sources supplied by add-ons, chosen by an address scheme in Settings ("<scheme>:<rest>"), e.g. a local feed
 * from another app on the same head unit. Listed in assets/addons.txt like add-ons; the default build has none, so a plain
 * "host:port" always means the comma over HTTP.
 */
public interface SourceProvider {
    /** lowercase scheme without the colon, e.g. "local-feed" */
    String scheme();
    /** short human label for the connection screen / settings note */
    String label();
    /** open a source for the part after "scheme:" (never null; throw IllegalArgumentException for a bad address) */
    DataSource open(String rest, DataSource.Clock clock);
}
