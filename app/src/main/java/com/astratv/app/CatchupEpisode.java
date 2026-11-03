package com.astratv.app;

import org.json.*;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One catch-up programme or serial episode from an official broadcaster archive. Playback is only
 * offered through {@link #playbackType} (a provider-supported route); artwork is the episode's own
 * thumbnail, never a movie poster.
 */
final class CatchupEpisode {
    String episodeId="",providerId="",seriesId="",seriesTitle="",episodeTitle="",language="",genre="";
    int seasonNumber,episodeNumber;
    /** Broadcast or publish time, epoch ms. */
    long airDate,availableFrom,availableUntil;
    String posterUrl="",backdropUrl="";
    int durationSeconds;
    String accessType=CatalogItem.FREE,playbackType=CatalogItem.YOUTUBE_HANDOFF,playbackReference="";
    long lastValidatedAt;
    String channelTitle="",description="";

    boolean serial(){return episodeNumber>0;}

    JSONObject json()throws JSONException{
        return new JSONObject().put("episodeId",episodeId).put("providerId",providerId).put("seriesId",seriesId).put("seriesTitle",seriesTitle)
            .put("episodeTitle",episodeTitle).put("language",language).put("genre",genre).put("seasonNumber",seasonNumber).put("episodeNumber",episodeNumber)
            .put("airDate",airDate).put("availableFrom",availableFrom).put("availableUntil",availableUntil).put("posterUrl",posterUrl).put("backdropUrl",backdropUrl)
            .put("duration",durationSeconds).put("accessType",accessType).put("playbackType",playbackType).put("playbackReference",playbackReference)
            .put("lastValidatedAt",lastValidatedAt).put("channelTitle",channelTitle).put("description",description);
    }
    static CatchupEpisode from(JSONObject o){
        CatchupEpisode e=new CatchupEpisode();
        e.episodeId=o.optString("episodeId");e.providerId=o.optString("providerId");e.seriesId=o.optString("seriesId");e.seriesTitle=o.optString("seriesTitle");
        e.episodeTitle=o.optString("episodeTitle");e.language=o.optString("language");e.genre=o.optString("genre");
        e.seasonNumber=o.optInt("seasonNumber");e.episodeNumber=o.optInt("episodeNumber");
        e.airDate=o.optLong("airDate");e.availableFrom=o.optLong("availableFrom");e.availableUntil=o.optLong("availableUntil");
        e.posterUrl=o.optString("posterUrl");e.backdropUrl=o.optString("backdropUrl");e.durationSeconds=o.optInt("duration");
        e.accessType=o.optString("accessType",CatalogItem.FREE);e.playbackType=o.optString("playbackType",CatalogItem.YOUTUBE_HANDOFF);e.playbackReference=o.optString("playbackReference");
        e.lastValidatedAt=o.optLong("lastValidatedAt");e.channelTitle=o.optString("channelTitle");e.description=o.optString("description");
        return e;
    }

    // ---- title parsing ------------------------------------------------------------------------
    private static final Pattern EPISODE=Pattern.compile("(?i)(?:\\b(?:episode|epi|ep)\\s*[.:#-]?\\s*|\\bs\\d{1,2}\\s*e)(\\d{1,4})\\b");
    private static final Pattern SEASON=Pattern.compile("(?i)\\b(?:season\\s*[.:#-]?\\s*(\\d{1,2})\\b|s(\\d{1,2})\\s*e\\d)");
    private static final Pattern SEPARATORS=Pattern.compile("\\s*(?:\\||\\s-\\s|–|—|::)\\s*");

    static int episodeNumber(String title){Matcher m=EPISODE.matcher(title==null?"":title);return m.find()?Integer.parseInt(m.group(1)):0;}
    static int seasonNumber(String title){Matcher m=SEASON.matcher(title==null?"":title);if(!m.find())return 0;return Integer.parseInt(m.group(1)!=null?m.group(1):m.group(2));}
    /**
     * Series name from a broadcaster video title such as "Serial Name | Episode 12 | DD Tamil": the
     * first title segment once episode/season markers and the channel name are removed. Empty when
     * nothing meaningful remains.
     */
    static String seriesTitle(String title,String channelTitle){
        if(title==null)return "";
        String channel=channelTitle==null?"":channelTitle.trim().toLowerCase(Locale.ROOT);
        for(String part:SEPARATORS.split(title)){
            String p=EPISODE.matcher(part).replaceAll(" ");p=SEASON.matcher(p).replaceAll(" ");
            p=p.replaceAll("(?i)#\\S+","").replaceAll("\\s+"," ").replaceAll("^[\\s,:-]+|[\\s,:-]+$","").trim();
            if(p.length()<2)continue;
            String lower=p.toLowerCase(Locale.ROOT);
            if(!channel.isEmpty()&&(lower.equals(channel)||lower.equals("dd")))continue;
            return p;
        }
        return "";
    }
    /** Shorts, trailers, promos and very short clips are not catch-up programmes. */
    static boolean programme(String title,int durationSeconds,boolean live){
        if(live||durationSeconds<240)return false;
        String t=title==null?"":title.toLowerCase(Locale.ROOT);
        return !(t.contains("#shorts")||t.contains("promo")||t.contains("teaser")||t.contains("trailer")||t.contains("coming soon"));
    }
    static String seriesId(String providerId,String seriesTitle){
        String n=seriesTitle.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+","-").replaceAll("^-|-$","");
        return n.isEmpty()?"":providerId+"/"+n;
    }
}
