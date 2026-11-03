package com.astratv.app;

import org.json.*;
import java.time.*;
import java.util.*;

/**
 * Recently aired episodes from the TVMaze public API (no key; data CC BY-SA, attributed in Settings).
 * One card per show, labelled with its latest aired episode. Metadata only, never playable.
 */
final class TvMazeSource implements CatalogSource {
    static final String ID="tvmaze.schedule",PROVIDER="tvmaze";
    static final String ROW_INDIA="tv.open.india",ROW_WORLD="tv.open.world";
    static final String API="https://api.tvmaze.com";
    private static final Set<String> INDIAN=new HashSet<>(Arrays.asList("hi","te","ta","ml","kn","bn","mr","pa","gu"));
    private final Clock clock;
    TvMazeSource(Clock clock){this.clock=clock;}

    @Override public String id(){return ID;}
    @Override public String label(){return "TV episodes · TVMaze (CC BY-SA)";}
    @Override public long defaultTtlMs(){return 60L*60_000L;}
    @Override public boolean checkOnForeground(){return false;}
    @Override public boolean ready(){return true;}
    @Override public String notReadyReason(){return "";}

    @Override public List<Batch> fetch(CatalogHttp http)throws Exception{
        LocalDate today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
        List<JSONObject> india=new ArrayList<>(),world=new ArrayList<>();Exception first=null;int ok=0;
        for(int d=0;d<7;d++)try{collect(http.get(API+"/schedule?country=IN&date="+today.minusDays(d)).body,india);ok++;}catch(Exception e){if(first==null)first=e;}
        for(int d=0;d<2;d++)try{collect(http.get(API+"/schedule/web?date="+today.minusDays(d)).body,world);ok++;}catch(Exception e){if(first==null)first=e;}
        if(ok==0&&first!=null)throw first;
        long now=clock.millis();
        List<CatalogItem> worldCards=shows(world,now),indiaCards=shows(india,now);
        for(CatalogItem c:worldCards)if(INDIAN.contains(c.languageCode))indiaCards.add(c);
        indiaCards=dedupe(indiaCards);indiaCards.sort(Comparator.comparingLong((CatalogItem c)->c.sourceUpdatedAt).reversed());
        List<CatalogItem> globe=new ArrayList<>();for(CatalogItem c:worldCards)if(!c.title.poster.isEmpty())globe.add(c);
        globe.sort(Comparator.comparingDouble((CatalogItem c)->c.popularity).reversed());
        return Arrays.asList(new Batch(ROW_INDIA,indiaCards,false),new Batch(ROW_WORLD,globe.size()>60?new ArrayList<>(globe.subList(0,60)):globe,false));
    }
    private static void collect(String body,List<JSONObject> into)throws JSONException{JSONArray a=new JSONArray(body);for(int i=0;i<a.length();i++){JSONObject e=a.optJSONObject(i);if(e!=null)into.add(e);}}

    /** Pure: schedule episodes (either /schedule or /schedule/web shape) to one card per show, aired episodes only. */
    static List<CatalogItem> shows(List<JSONObject> episodes,long now){
        Map<Integer,CatalogItem> byShow=new LinkedHashMap<>();
        for(JSONObject e:episodes){
            JSONObject show=e.optJSONObject("show");if(show==null){JSONObject emb=e.optJSONObject("_embedded");show=emb==null?null:emb.optJSONObject("show");}
            if(show==null||show.optInt("id")<=0)continue;
            long aired=airstamp(TmdbCatalogSource.str(e,"airstamp"));if(aired<=0||aired>now)continue;
            if(adult(show))continue;
            CatalogItem prev=byShow.get(show.optInt("id"));if(prev!=null&&prev.sourceUpdatedAt>=aired)continue;
            CatalogItem c=card(show,e,aired);if(c!=null)byShow.put(show.optInt("id"),c);
        }
        return new ArrayList<>(byShow.values());
    }
    static CatalogItem card(JSONObject show,JSONObject ep,long aired){
        String name=TmdbCatalogSource.str(show,"name");if(name.isEmpty())return null;
        String premiered=TmdbCatalogSource.str(show,"premiered"),code=languageCode(TmdbCatalogSource.str(show,"language"));
        List<String> genres=new ArrayList<>();JSONArray g=show.optJSONArray("genres");if(g!=null)for(int i=0;i<g.length()&&genres.size()<3;i++)if(!g.optString(i).isEmpty())genres.add(g.optString(i));
        JSONObject channel=show.optJSONObject("network");if(channel==null)channel=show.optJSONObject("webChannel");
        String where=channel==null?"":TmdbCatalogSource.str(channel,"name");
        Library.Title t=new Library.Title("show:"+show.optInt("id"),name,plain(TmdbCatalogSource.str(show,"summary")),"",where.isEmpty()?"":"On "+where,premiered.length()>=4?premiered.substring(0,4):"",String.join(" · ",genres));
        t.provider=PROVIDER;t.providerName="TVMaze";t.type="series";t.released=premiered;t.language=code.isEmpty()?TmdbCatalogSource.str(show,"language"):TmdbCatalogSource.languageName(code);
        JSONObject img=show.optJSONObject("image");
        String poster=img==null?"":https(TmdbCatalogSource.str(img,"medium")),large=img==null?"":https(TmdbCatalogSource.str(img,"original"));
        t.poster=poster;t.background=large.isEmpty()?poster:large;
        int se=ep.optInt("season"),num=ep.optInt("number");String airdate=TmdbCatalogSource.str(ep,"airdate");
        if(se>0&&num>0){t.season=se;t.episode=num;}
        CatalogItem c=new CatalogItem(t);c.kind=CatalogItem.SERIES;c.languageCode=code;c.releaseDate=premiered;c.sourceUpdatedAt=aired;c.playable=false;
        c.episodeLabel=(se>0&&num>0?"S"+se+" · E"+num:"New episode")+(airdate.isEmpty()?"":" · "+TmdbCatalogSource.shortDate(airdate));
        c.popularity=show.optDouble("weight",0);
        JSONObject rating=show.optJSONObject("rating");double avg=rating==null||rating.isNull("average")?0:rating.optDouble("average",0);
        if(avg>0){c.rating=avg;c.ratingSource="TVMaze";t.rating=String.format(Locale.US,"%.1f",avg);}
        if(!t.language.isEmpty())c.audio.add(t.language);
        return c;
    }
    private static boolean adult(JSONObject show){JSONArray g=show.optJSONArray("genres");if(g!=null)for(int i=0;i<g.length();i++)if("Adult".equalsIgnoreCase(g.optString(i)))return true;return false;}
    private static List<CatalogItem> dedupe(List<CatalogItem> in){Map<String,CatalogItem> out=new LinkedHashMap<>();for(CatalogItem c:in){CatalogItem p=out.get(c.key());if(p==null||c.sourceUpdatedAt>p.sourceUpdatedAt)out.put(c.key(),c);}return new ArrayList<>(out.values());}
    static long airstamp(String s){try{return s.isEmpty()?0:OffsetDateTime.parse(s).toInstant().toEpochMilli();}catch(Exception e){return 0;}}
    static String plain(String html){return html.replaceAll("<[^>]+>","").replace("&amp;","&").replace("&quot;","\"").replace("&#39;","'").replace("&nbsp;"," ").trim();}
    private static String https(String url){return url.startsWith("http://")?"https://"+url.substring(7):url;}
    /** ISO 639-1 code for TVMaze's English language names ("Hindi" → "hi"); empty when unknown. */
    static String languageCode(String name){
        if(name.isEmpty())return "";
        for(String code:Locale.getISOLanguages())if(new Locale(code).getDisplayLanguage(Locale.ENGLISH).equalsIgnoreCase(name))return code;
        return "";
    }
}
