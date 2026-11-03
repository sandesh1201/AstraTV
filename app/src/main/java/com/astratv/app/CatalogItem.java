package com.astratv.app;

import org.json.*;
import java.util.*;

/**
 * A catalog entry plus the facts Saandio tracks separately: official release date, the time
 * Saandio first saw the title, source popularity, rating and verified availability are never
 * derived from one another.
 */
public final class CatalogItem {
    public static final String MOVIE="movie",SERIES="series",CHANNEL="channel",EVENT="event",CATCHUP="catchup";
    /** Access and playback routes for official-provider entries (Sun Network, catch-up). Empty for everything else. */
    public static final String FREE="FREE",SUBSCRIPTION="SUBSCRIPTION",UNAVAILABLE="UNAVAILABLE";
    public static final String APP_HANDOFF="APP_HANDOFF",YOUTUBE_HANDOFF="YOUTUBE_HANDOFF";
    public final Library.Title title;
    public String kind=MOVIE;
    public int tmdbId;
    public String languageCode="";
    /** Official release or first-air date, yyyy-MM-dd. Empty when the source does not say. */
    public String releaseDate="";
    /** Source-reported update time (latest episode air date, event start), epoch ms. 0 = unknown. */
    public long sourceUpdatedAt;
    /** When Saandio first saw this key. 0 = part of the first catalog baseline, -1 = not stored yet. */
    public long firstSeen=-1;
    public double popularity;
    public double rating;public int votes;public String ratingSource="";
    /** Highest rendition height reported by an authorized provider's playback API. 0 = not verified. */
    public int verifiedHeight;
    public boolean playable;
    /** Source movie tabs: a {@link MovieAvailability} name decided by the provider's playback API, plus its reason. */
    public String availability="",availabilityReason="";
    /** Source-reported time the provider added the title, epoch ms. 0 = unknown. */
    public long addedAt;
    /** YouTube video id of a trailer that passed {@link TrailerMatcher}; empty until one is validated. */
    public String trailerKey="";
    public final List<String> audio=new ArrayList<>(),subtitles=new ArrayList<>(),sources=new ArrayList<>();
    /** Channels: "ok" or "failed" from a real playback probe; empty when never checked. */
    public String health="";
    public long healthCheckedAt,healthOkAt;
    /** Consecutive failed playback checks since the last success. */
    public int healthFails;
    /** Events: live, upcoming, finished, postponed, cancelled or unconfirmed, as reported by the schedule source. */
    public String status="";
    public long startsAt;
    public String sport="",league="",episodeLabel="";
    /** Official-provider entries: {@link #FREE}/{@link #SUBSCRIPTION}/{@link #UNAVAILABLE}, how playback is routed, and the route target (package, channel or video id). */
    public String accessType="",playbackType="",playbackReference="";
    /** A {@link ChannelState} name fixed by the provider entry (for example SUBSCRIPTION_REQUIRED); empty means derive it from health. */
    public String channelState="";
    /** Catch-up episodes: the full episode record. */
    public CatchupEpisode episode;
    /** Not persisted: when the player last validated this channel (see {@link ChannelValidation}). */
    public long playbackVerifiedAt;

    public CatalogItem(Library.Title title){this.title=title;}
    public String key(){return title.key();}
    public int year(){
        if(releaseDate.length()>=4)try{return Integer.parseInt(releaseDate.substring(0,4));}catch(NumberFormatException ignored){/* fall through */}
        return CatalogSort.year(title);
    }
    /** Release date as yyyyMMdd for ordering; unknown dates sort last. */
    public int dateKey(){
        if(releaseDate.length()==10)try{return Integer.parseInt(releaseDate.replace("-",""));}catch(NumberFormatException ignored){/* fall through */}
        int y=year();return y>0?y*10000:0;
    }
    public boolean hd(){return verifiedHeight>=720;}
    public boolean live(){return EVENT.equals(kind)&&"live".equals(status);}
    /** Wraps a legacy provider or library title. Playability is only claimed for licensed or user-owned sources. */
    public static CatalogItem of(Library.Title t){
        CatalogItem c=new CatalogItem(t);
        c.kind="tv".equals(t.type)?CHANNEL:"series".equals(t.type)||"episode".equals(t.type)?SERIES:MOVIE;
        c.releaseDate=t.released==null?"":t.released;
        c.playable="library".equals(t.provider)||"samples".equals(t.provider);
        c.rating=CatalogSort.popularity(t);
        if(!t.language.isEmpty())c.audio.add(t.language);
        return c;
    }
    /** Changes whenever anything a card displays changes; drives row diffing without resetting focus. */
    public String signature(){
        return title.name+"|"+title.poster+"|"+title.year+"|"+title.language+"|"+rating+"|"+verifiedHeight+"|"+playable+"|"+health+"|"+status+"|"+startsAt+"|"+episodeLabel+"|"+audio+"|"+availability+"|"+title.background+"|"+title.genre
            +(channelState.isEmpty()&&playbackVerifiedAt==0?"":"|"+channelState+"|"+playbackVerifiedAt);
    }
    /** Opens in an official app (or YouTube) instead of Saandio's player. */
    public boolean handoff(){return APP_HANDOFF.equals(playbackType)||YOUTUBE_HANDOFF.equals(playbackType);}
    public MovieAvailability availability(){return MovieAvailability.parse(availability);}
    public JSONObject json()throws JSONException{
        return new JSONObject().put("t",title.json()).put("kind",kind).put("tmdb",tmdbId).put("lang",languageCode).put("release",releaseDate)
            .put("updated",sourceUpdatedAt).put("pop",popularity).put("rating",rating).put("votes",votes).put("ratingSource",ratingSource)
            .put("height",verifiedHeight).put("playable",playable).put("audio",new JSONArray(audio)).put("subs",new JSONArray(subtitles)).put("sources",new JSONArray(sources))
            .put("status",status).put("starts",startsAt).put("sport",sport).put("league",league).put("episode",episodeLabel)
            .put("avail",availability).put("availWhy",availabilityReason).put("added",addedAt).put("trailer",trailerKey)
            .put("access",accessType).put("playback",playbackType).put("playbackRef",playbackReference).put("chState",channelState)
            .put("catchup",episode==null?null:episode.json());
    }
    public static CatalogItem from(JSONObject o)throws JSONException{
        CatalogItem c=new CatalogItem(Library.Title.from(o.getJSONObject("t")));
        c.kind=o.optString("kind",MOVIE);c.tmdbId=o.optInt("tmdb");c.languageCode=o.optString("lang");c.releaseDate=o.optString("release");
        c.sourceUpdatedAt=o.optLong("updated");c.popularity=o.optDouble("pop",0);c.rating=o.optDouble("rating",0);c.votes=o.optInt("votes");c.ratingSource=o.optString("ratingSource");
        c.verifiedHeight=o.optInt("height");c.playable=o.optBoolean("playable");
        strings(o.optJSONArray("audio"),c.audio);strings(o.optJSONArray("subs"),c.subtitles);strings(o.optJSONArray("sources"),c.sources);
        c.status=o.optString("status");c.startsAt=o.optLong("starts");c.sport=o.optString("sport");c.league=o.optString("league");c.episodeLabel=o.optString("episode");
        c.availability=o.optString("avail");c.availabilityReason=o.optString("availWhy");c.addedAt=o.optLong("added");c.trailerKey=o.optString("trailer");
        c.accessType=o.optString("access");c.playbackType=o.optString("playback");c.playbackReference=o.optString("playbackRef");c.channelState=o.optString("chState");
        JSONObject ep=o.optJSONObject("catchup");if(ep!=null)c.episode=CatchupEpisode.from(ep);
        return c;
    }
    private static void strings(JSONArray a,List<String> into){if(a!=null)for(int i=0;i<a.length();i++){String s=a.optString(i);if(!s.isEmpty()&&!into.contains(s))into.add(s);}}
    /** 64-bit key hash for RecyclerView stable ids; String.hashCode collides too easily across thousands of titles. */
    public static long stableId(String key){long h=1125899906842597L;for(int i=0;i<key.length();i++)h=31*h+key.charAt(i);return h;}
}
