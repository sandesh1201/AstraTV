package com.astratv.app;

import org.json.*;
import java.net.URLEncoder;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Supplier;

/**
 * Catch-Up TV from official broadcaster archives ({@link CatchupChannels}): the newest public
 * uploads of each channel, via the Saandio Catalog API ({@code /api/channel-uploads}, YouTube key on
 * the server) or, in debug builds, a developer key. Episodes play in the YouTube app; Saandio never
 * extracts media URLs. Shorts, promos and live streams are left out.
 */
final class CatchupSource implements CatalogSource {
    static final String ID="catchup.official",PROVIDER="catchup";
    static final String ROW_RECENT="catchup.recent",ROW_LATEST="catchup.latest",ROW_CLASSICS="catchup.classics",ROW_FREE="catchup.free";
    static final String ROW_CONTINUE="catchup.continue",ROW_SUBSCRIPTION="catchup.subscription";
    static final String[][] LANGUAGES={{"te","Telugu"},{"ta","Tamil"},{"kn","Kannada"},{"ml","Malayalam"},{"hi","Hindi"}};
    static final long RECENT_MS=7L*24*60*60_000L;
    private static final String API="https://www.googleapis.com/youtube/v3";
    private final String catalogApiBase;private final Map<String,String> clientHeaders;private final Supplier<String> developerKey,country;
    private final java.util.function.LongSupplier clock;private final java.util.function.BooleanSupplier enabled;

    CatchupSource(java.util.function.BooleanSupplier enabled,String catalogApiBase,Map<String,String> clientHeaders,Supplier<String> developerKey,Supplier<String> country,java.util.function.LongSupplier clock){
        this.enabled=enabled;this.catalogApiBase=catalogApiBase==null?"":catalogApiBase.trim();this.clientHeaders=clientHeaders;this.developerKey=developerKey;this.country=country;this.clock=clock;
    }
    static String langRow(String code){return "catchup.lang."+code;}

    @Override public String id(){return ID;}
    @Override public String label(){return "Catch-Up TV · official broadcaster archives";}
    @Override public long defaultTtlMs(){return 2L*60*60_000L;}
    @Override public boolean checkOnForeground(){return false;}
    @Override public boolean ready(){return enabled.getAsBoolean()&&configured();}
    boolean configured(){return backendUrl(catalogApiBase,"UC")!=null||!developerKey.get().isEmpty();}
    @Override public String notReadyReason(){return !enabled.getAsBoolean()?"Catch-Up TV is turned off (ENABLE_CATCHUP_TV).":"Catch-Up TV needs the Saandio Catalog API with YOUTUBE_API_KEY set on the server.";}

    @Override public List<Batch> fetch(CatalogHttp http)throws Exception{
        List<CatchupEpisode> all=new ArrayList<>();Exception last=null;int ok=0;String cc=country.get();
        for(CatchupChannels.Channel ch:CatchupChannels.CHANNELS){
            try{all.addAll(episodes(ch,videos(http,ch.youtubeId),cc,clock.getAsLong()));ok++;}
            catch(Exception e){last=e;}
        }
        if(ok==0&&last!=null)throw last;
        return batches(all,clock.getAsLong());
    }
    private JSONObject videos(CatalogHttp http,String channelId)throws Exception{
        String backend=backendUrl(catalogApiBase,channelId);
        if(backend!=null)return new JSONObject(http.get(backend,clientHeaders).body);
        String key=developerKey.get();
        JSONObject list=new JSONObject(http.get(API+"/playlistItems?part=contentDetails&maxResults=25&playlistId="+enc("UU"+channelId.substring(2))+"&key="+enc(key)).body);
        List<String> ids=new ArrayList<>();JSONArray items=list.optJSONArray("items");
        if(items!=null)for(int i=0;i<items.length();i++){JSONObject cd=items.optJSONObject(i)==null?null:items.optJSONObject(i).optJSONObject("contentDetails");String v=cd==null?"":cd.optString("videoId");if(v.matches("[A-Za-z0-9_-]{11}"))ids.add(v);}
        if(ids.isEmpty())return new JSONObject().put("items",new JSONArray());
        return new JSONObject(http.get(YoutubeTrailerSearch.videosUrl(ids,key)).body);
    }
    static String backendUrl(String base,String channelId){
        String b=base==null?"":base.trim();while(b.endsWith("/"))b=b.substring(0,b.length()-1);
        return Library.https(b)?b+"/api/channel-uploads?channel="+enc(channelId):null;
    }

    /** Parses videos.list items (backend or direct shape) into programmes from this channel only. */
    static List<CatchupEpisode> episodes(CatchupChannels.Channel ch,JSONObject videos,String country,long now){
        List<CatchupEpisode> out=new ArrayList<>();JSONArray items=videos==null?null:videos.optJSONArray("items");if(items==null)return out;
        String cc=country==null?"":country;
        for(int i=0;i<items.length();i++){
            JSONObject o=items.optJSONObject(i);if(o==null)continue;
            JSONObject sn=o.optJSONObject("snippet"),cd=o.optJSONObject("contentDetails"),st=o.optJSONObject("status");
            String id=o.optString("id");if(sn==null||!id.matches("[A-Za-z0-9_-]{11}"))continue;
            if(!ch.youtubeId.equals(sn.optString("channelId",ch.youtubeId)))continue;
            if(st!=null&&!"public".equals(st.optString("privacyStatus","public")))continue;
            if(YoutubeTrailerSearch.blocked(cd,cc))continue;
            String title=sn.optString("title").trim();int seconds=YoutubeTrailerSearch.seconds(cd==null?"":cd.optString("duration"));
            if(!CatchupEpisode.programme(title,seconds,!"none".equals(sn.optString("liveBroadcastContent","none"))))continue;
            long aired=instant(sn.optString("publishedAt"));if(aired<=0||aired>now+60_000L)continue;
            CatchupEpisode e=new CatchupEpisode();
            e.episodeId="yt:"+id;e.providerId=ch.providerId();e.channelTitle=ch.name;e.language=ch.language;
            e.episodeTitle=title;e.description=sn.optString("description");
            e.episodeNumber=CatchupEpisode.episodeNumber(title);e.seasonNumber=CatchupEpisode.seasonNumber(title);
            e.seriesTitle=e.episodeNumber>0?CatchupEpisode.seriesTitle(title,ch.name):"";e.seriesId=CatchupEpisode.seriesId(e.providerId,e.seriesTitle);
            if(e.seriesTitle.isEmpty())e.episodeNumber=0;
            e.genre=CatchupChannels.CLASSICS.equals(ch.role)?"Classics":e.episodeNumber>0?"Serial":"Programme";
            e.airDate=aired;e.availableFrom=aired;e.availableUntil=0;
            String thumb=thumbnail(sn);e.posterUrl=thumb;e.backdropUrl=thumb;
            e.durationSeconds=seconds;e.accessType=CatalogItem.FREE;e.playbackType=CatalogItem.YOUTUBE_HANDOFF;e.playbackReference=id;e.lastValidatedAt=now;
            out.add(e);
        }
        return out;
    }
    /** Newest first in every row; a series appears once in Latest Episodes, with its newest episode. */
    static List<Batch> batches(List<CatchupEpisode> all,long now){
        List<CatchupEpisode> sorted=new ArrayList<>(all);sorted.sort((a,b)->Long.compare(b.airDate,a.airDate));
        Map<String,CatchupChannels.Channel> channels=new HashMap<>();for(CatchupChannels.Channel c:CatchupChannels.CHANNELS)channels.put(c.providerId(),c);
        List<CatalogItem> recent=new ArrayList<>(),latest=new ArrayList<>(),classics=new ArrayList<>(),free=new ArrayList<>();
        Map<String,List<CatalogItem>> lang=new LinkedHashMap<>();for(String[] l:LANGUAGES)lang.put(l[1],new ArrayList<>());
        Set<String> seenSeries=new HashSet<>(),seen=new HashSet<>();
        for(CatchupEpisode e:sorted){
            if(!seen.add(e.episodeId))continue;
            CatchupChannels.Channel ch=channels.get(e.providerId);boolean classic=ch!=null&&CatchupChannels.CLASSICS.equals(ch.role);
            CatalogItem c=item(e);
            if(classic){classics.add(c);continue;}
            if(now-e.airDate<=RECENT_MS)recent.add(c);
            if(e.serial()){
                if(seenSeries.add(e.seriesId))latest.add(c);
                List<CatalogItem> l=lang.get(e.language);if(l!=null)l.add(c);
            }else free.add(c);
        }
        List<Batch> out=new ArrayList<>();
        out.add(new Batch(ROW_RECENT,recent,false));out.add(new Batch(ROW_LATEST,latest,false));
        for(String[] l:LANGUAGES)out.add(new Batch(langRow(l[0]),lang.get(l[1]),false));
        out.add(new Batch(ROW_CLASSICS,classics,false));out.add(new Batch(ROW_FREE,free,false));
        return out;
    }
    static CatalogItem item(CatchupEpisode e){
        String name=e.serial()?e.seriesTitle:e.episodeTitle;
        Library.Title t=new Library.Title(e.episodeId,name,e.description,"","",year(e.airDate),e.genre);
        t.provider=PROVIDER;t.providerName=e.channelTitle;t.type="catchup";t.language=e.language;t.group=e.genre;
        t.poster=e.posterUrl;t.background=e.backdropUrl;t.released=date(e.airDate);
        CatalogItem c=new CatalogItem(t);c.kind=CatalogItem.CATCHUP;c.playable=false;c.releaseDate=date(e.airDate);c.sourceUpdatedAt=e.airDate;
        c.episodeLabel=CatalogViews.join(e.serial()?(e.seasonNumber>0?"S"+e.seasonNumber+" ":"")+"Ep "+e.episodeNumber:"",e.channelTitle,CatalogViews.time(e.airDate));
        c.accessType=e.accessType;c.playbackType=e.playbackType;c.playbackReference=e.playbackReference;c.episode=e;
        if(!e.language.isEmpty())c.audio.add(e.language);c.sources.add(e.channelTitle);
        return c;
    }
    /** Sun NXT catch-up per language: subscription only, opened in the official app. */
    static List<CatalogItem> subscriptionTiles(){
        List<CatalogItem> out=new ArrayList<>();
        for(String[] s:CatchupChannels.SUBSCRIPTION_APPS){
            Library.Title t=new Library.Title("sunnxt."+s[2].toLowerCase(Locale.ROOT),s[1],
                "Sun TV Network serials in "+s[2]+" are available to watch again in Sun NXT, Sun TV Network's official app. A Sun NXT subscription is required. Saandio does not stream these episodes.","","","","Serials");
            t.provider=PROVIDER;t.providerName=OfficialAppHandoff.appName(s[0]);t.type="catchup";t.language=s[2];t.group="Subscription";
            CatalogItem c=new CatalogItem(t);c.kind=CatalogItem.CATCHUP;c.accessType=CatalogItem.SUBSCRIPTION;c.playbackType=CatalogItem.APP_HANDOFF;c.playbackReference=s[0];
            c.channelState=ChannelState.SUBSCRIPTION_REQUIRED.name();c.episodeLabel="Subscription · opens Sun NXT";c.audio.add(s[2]);
            out.add(c);
        }
        return out;
    }
    private static String thumbnail(JSONObject sn){
        String direct=sn.optString("thumbnail");if(Library.https(direct))return direct;
        JSONObject t=sn.optJSONObject("thumbnails");if(t==null)return "";
        for(String k:new String[]{"maxres","standard","high","medium","default"}){JSONObject o=t.optJSONObject(k);String u=o==null?"":o.optString("url");if(Library.https(u))return u;}
        return "";
    }
    static long instant(String iso){try{return Instant.parse(iso).toEpochMilli();}catch(DateTimeParseException|NullPointerException e){return 0;}}
    private static String date(long at){return at<=0?"":Instant.ofEpochMilli(at).atZone(ZoneId.of("Asia/Kolkata")).toLocalDate().toString();}
    private static String year(long at){return at<=0?"":String.valueOf(Instant.ofEpochMilli(at).atZone(ZoneId.of("Asia/Kolkata")).getYear());}
    private static String enc(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(java.io.UnsupportedEncodingException e){return "";}}
}
