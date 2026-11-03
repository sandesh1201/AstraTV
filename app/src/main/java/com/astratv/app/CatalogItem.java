package com.astratv.app;

import org.json.*;
import java.util.*;

/**
 * A catalog entry plus the facts Saandio tracks separately: official release date, the time
 * Saandio first saw the title, source popularity, rating and verified availability are never
 * derived from one another.
 */
public final class CatalogItem {
    public static final String MOVIE="movie",SERIES="series",CHANNEL="channel",EVENT="event";
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
    public final List<String> audio=new ArrayList<>(),subtitles=new ArrayList<>(),sources=new ArrayList<>();
    /** Channels: "ok" or "failed" from a real playback probe; empty when never checked. */
    public String health="";
    public long healthCheckedAt;
    /** Events: live, upcoming, finished, postponed, cancelled or unconfirmed, as reported by the schedule source. */
    public String status="";
    public long startsAt;
    public String sport="",league="",episodeLabel="";

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
        c.playable="library".equals(t.provider)||"samples".equals(t.provider)||EinthusanSimulatedProvider.ID.equals(t.provider);
        c.rating=CatalogSort.popularity(t);
        if(!t.language.isEmpty())c.audio.add(t.language);
        return c;
    }
    /** Changes whenever anything a card displays changes; drives row diffing without resetting focus. */
    public String signature(){
        return title.name+"|"+title.poster+"|"+title.year+"|"+title.language+"|"+rating+"|"+verifiedHeight+"|"+playable+"|"+health+"|"+status+"|"+startsAt+"|"+episodeLabel+"|"+audio;
    }
    public JSONObject json()throws JSONException{
        return new JSONObject().put("t",title.json()).put("kind",kind).put("tmdb",tmdbId).put("lang",languageCode).put("release",releaseDate)
            .put("updated",sourceUpdatedAt).put("pop",popularity).put("rating",rating).put("votes",votes).put("ratingSource",ratingSource)
            .put("height",verifiedHeight).put("playable",playable).put("audio",new JSONArray(audio)).put("subs",new JSONArray(subtitles)).put("sources",new JSONArray(sources))
            .put("status",status).put("starts",startsAt).put("sport",sport).put("league",league).put("episode",episodeLabel);
    }
    public static CatalogItem from(JSONObject o)throws JSONException{
        CatalogItem c=new CatalogItem(Library.Title.from(o.getJSONObject("t")));
        c.kind=o.optString("kind",MOVIE);c.tmdbId=o.optInt("tmdb");c.languageCode=o.optString("lang");c.releaseDate=o.optString("release");
        c.sourceUpdatedAt=o.optLong("updated");c.popularity=o.optDouble("pop",0);c.rating=o.optDouble("rating",0);c.votes=o.optInt("votes");c.ratingSource=o.optString("ratingSource");
        c.verifiedHeight=o.optInt("height");c.playable=o.optBoolean("playable");
        strings(o.optJSONArray("audio"),c.audio);strings(o.optJSONArray("subs"),c.subtitles);strings(o.optJSONArray("sources"),c.sources);
        c.status=o.optString("status");c.startsAt=o.optLong("starts");c.sport=o.optString("sport");c.league=o.optString("league");c.episodeLabel=o.optString("episode");
        return c;
    }
    private static void strings(JSONArray a,List<String> into){if(a!=null)for(int i=0;i<a.length();i++){String s=a.optString(i);if(!s.isEmpty()&&!into.contains(s))into.add(s);}}
    /** 64-bit key hash for RecyclerView stable ids; String.hashCode collides too easily across thousands of titles. */
    public static long stableId(String key){long h=1125899906842597L;for(int i=0;i<key.length();i++)h=31*h+key.charAt(i);return h;}
}
