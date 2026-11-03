package com.astratv.app;

import java.util.Locale;

/**
 * Viewer-facing channel state. It is derived from facts Saandio already records and never rewrites
 * them: the stored probe health ("ok"/"failed") keeps driving the existing Online / Offline /
 * Unchecked labels. LIVE is stricter than "Online": it needs a recent playback validation, meaning
 * the player reached READY and kept advancing (see {@link ChannelValidation}). A reachable URL or a
 * valid manifest alone is never LIVE.
 */
enum ChannelState {
    LIVE("Live"),
    CHECKING("Checking"),
    TEMPORARILY_UNAVAILABLE("Temporarily unavailable"),
    SUBSCRIPTION_REQUIRED("Subscription required"),
    GEO_RESTRICTED("Region restricted"),
    UNSUPPORTED("Unsupported"),
    UNKNOWN("Unknown");

    /** A playback validation older than this no longer proves the channel is live. */
    static final long PLAYBACK_VALID_MS=6L*60*60_000L;
    final String label;
    ChannelState(String label){this.label=label;}

    static ChannelState parse(String name){
        if(name==null||name.isEmpty())return UNKNOWN;
        try{return valueOf(name.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException e){return UNKNOWN;}
    }
    /**
     * @param probing a health probe for this channel is in flight right now
     */
    static ChannelState of(CatalogItem c,boolean probing,long now){
        if(!c.channelState.isEmpty())return parse(c.channelState);
        if(probing)return CHECKING;
        long verified=c.playbackVerifiedAt;
        boolean failedSince=("failed".equals(c.health))&&c.healthCheckedAt>verified;
        if(verified>0&&now-verified<=PLAYBACK_VALID_MS&&!failedSince)return LIVE;
        if("failed".equals(c.health))return TEMPORARILY_UNAVAILABLE;
        return UNKNOWN;
    }
}
