package com.astratv.app;

/** Einthusan has no published third-party catalog or playback API. This adapter does not scrape or authenticate. */
public final class EinthusanAdapter {
    private EinthusanAdapter(){}
    public static final class Status {
        public final boolean available;public final String reason;
        public Status(boolean available,String reason){this.available=available;this.reason=reason;}
    }
    public static Status status(){
        return new Status(false,"Not connected. Einthusan publishes no third-party catalog or playback API, and this app has no written agreement with them. Site scraping and Kodi add-ons are not used. "
            +"To test the integration path, enable \""+EinthusanSimulatedProvider.NAME+"\" in Add-ons; it serves Creative Commons sample films through the same provider contract.");
    }
}
