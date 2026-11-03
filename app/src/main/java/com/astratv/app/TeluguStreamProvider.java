package com.astratv.app;

/**
 * Catch-Up TV: Telugu serial episodes listed by TeluguStream.
 *
 * telugustream.com is a WordPress site that re-posts same-day episodes of Star Maa, Gemini TV,
 * Zee Telugu, ETV and other channels' serials. It has no licence for those videos, so Saandio
 * reads only its public RSS feed for episode metadata and sends viewers to the broadcaster's own
 * app (JioHotstar, Sun NXT, ZEE5, ETV Win). The legacy add-on that scraped its pages and played
 * its links stays blocked ({@link ProviderRepository#unauthorized}).
 */
final class TeluguStreamProvider extends SerialCatchupProvider {
    static final String ID="catchup.telugustream",PROVIDER="telugustream",LEGACY_ADDON="telugu";
    static final String FEED="https://telugustream.com/feed/";
    static final String REASON="TeluguStream videos are not played in Saandio: the site re-posts Star Maa, Gemini TV, Zee Telugu and ETV serial episodes "
        +"that those broadcasters license only to their own apps. Catch-Up TV lists its episodes and opens them in the broadcaster's official app.";
    TeluguStreamProvider(Feed feed){super(feed);}
    static TeluguStreamProvider live(){return new TeluguStreamProvider(new WordPressSerialFeed(FEED,PROVIDER,"TeluguStream","Telugu",12));}

    @Override public String id(){return ID;}
    @Override public String label(){return "TeluguStream · Catch-Up TV";}
    @Override String provider(){return PROVIDER;}
    @Override String providerName(){return "TeluguStream";}
    @Override String language(){return "Telugu";}
    @Override String reason(){return REASON;}
}
