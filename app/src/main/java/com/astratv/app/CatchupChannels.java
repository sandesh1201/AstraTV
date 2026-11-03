package com.astratv.app;

import java.util.*;

/**
 * Official broadcaster archives for Catch-Up TV. Each is the broadcaster's own verified YouTube
 * channel, so episodes play in the YouTube app (an official, free route) and nothing is scraped.
 * Keep in sync with ALLOWED_CHANNELS in backend/lib/channel-uploads.js.
 */
final class CatchupChannels {
    static final String PRASAR_BHARATI="Prasar Bharati",SUN_TV_NETWORK="Sun TV Network";
    static final String CLASSICS="classics";
    static final class Channel{
        final String youtubeId,name,language,broadcaster,role;
        Channel(String youtubeId,String name,String language,String broadcaster,String role){this.youtubeId=youtubeId;this.name=name;this.language=language;this.broadcaster=broadcaster;this.role=role;}
        String providerId(){return "yt."+youtubeId;}
    }
    static final List<Channel> CHANNELS=Collections.unmodifiableList(Arrays.asList(
        new Channel("UC5-_zkjKabYd7tsLucKsriw","DD Tamil","Tamil",PRASAR_BHARATI,""),
        new Channel("UCcsvAreQ1IxIjWlBSpNEOWg","DD Saptagiri","Telugu",PRASAR_BHARATI,""),
        new Channel("UC6V4nVa0CQkoLZUlg9drmZA","DD Yadagiri","Telugu",PRASAR_BHARATI,""),
        new Channel("UCbdMik2cV8pea1jcWdX_CYA","DD Chandana","Kannada",PRASAR_BHARATI,""),
        new Channel("UC4--pPs1CVZvs60uuhfjdwQ","DD Malayalam","Malayalam",PRASAR_BHARATI,""),
        new Channel("UCSjPe5kinQtwcyHcFJyyMfw","Doordarshan National","Hindi",PRASAR_BHARATI,""),
        new Channel("UCWxqSvqiH8PGpSNPSVtFwGw","Prasar Bharati Archives","Hindi",PRASAR_BHARATI,CLASSICS)
    ));
    /** Subscription catch-up that Saandio cannot stream: offered only as an official app handoff. */
    static final String[][] SUBSCRIPTION_APPS={
        {OfficialAppHandoff.SUN_NXT,"Sun NXT · Tamil serials","Tamil"},
        {OfficialAppHandoff.SUN_NXT,"Sun NXT · Telugu serials","Telugu"},
        {OfficialAppHandoff.SUN_NXT,"Sun NXT · Kannada serials","Kannada"},
        {OfficialAppHandoff.SUN_NXT,"Sun NXT · Malayalam serials","Malayalam"},
    };
    private CatchupChannels(){}
    static Channel byYoutubeId(String id){for(Channel c:CHANNELS)if(c.youtubeId.equals(id))return c;return null;}
}
