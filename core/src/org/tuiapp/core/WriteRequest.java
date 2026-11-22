package org.tuiapp.core;

/** An HTTP write an add-on asks the app to send to the comma (POST, JSON body). Built only by add-ons. */
public final class WriteRequest {
    public final String addonId, key, path, jsonBody;
    public WriteRequest(String addonId, String key, String path, String jsonBody) {
        this.addonId = addonId; this.key = key; this.path = path; this.jsonBody = jsonBody;
    }
    @Override public String toString() { return "POST " + path + " " + jsonBody; }
}
