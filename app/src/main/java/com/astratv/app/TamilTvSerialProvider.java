package com.astratv.app;

/**
 * Catch-Up TV: Tamil serial episodes listed by TamilTVSerial.
 *
 * tamiltvserial.com is a WordPress site that re-posts same-day episodes of Sun TV, Star Vijay,
 * Zee Tamil and other channels' serials. It has no licence for those videos, so Saandio reads
 * only its public RSS feed for episode metadata and sends viewers to the broadcaster's own app.
 * The legacy add-on that scraped its pages and played its links stays blocked
 * ({@link ProviderRepository#unauthorized}).
 */
final class TamilTvSerialProvider extends SerialCatchupProvider {
    static final String ID="catchup.tamiltvserial",PROVIDER="tamiltvserial",LEGACY_ADDON="tamil";
    static final String FEED="https://www.tamiltvserial.com/feed/";
    static final String REASON="TamilTVSerial videos are not played in Saandio: the site re-posts Sun TV, Star Vijay and Zee Tamil serial episodes "
        +"that those broadcasters license only to their own apps. Catch-Up TV lists its episodes and opens them in the broadcaster's official app.";
    TamilTvSerialProvider(Feed feed){super(feed);}
    static TamilTvSerialProvider live(){return new TamilTvSerialProvider(new WordPressSerialFeed(FEED,PROVIDER,"TamilTVSerial","Tamil",12));}

    @Override public String id(){return ID;}
    @Override public String label(){return "TamilTVSerial · Catch-Up TV";}
    @Override String provider(){return PROVIDER;}
    @Override String providerName(){return "TamilTVSerial";}
    @Override String language(){return "Tamil";}
    @Override String reason(){return REASON;}
}
