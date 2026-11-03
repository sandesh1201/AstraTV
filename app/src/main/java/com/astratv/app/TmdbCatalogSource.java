package com.astratv.app;

import org.json.*;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Movie and TV discovery from the TMDB API (used under TMDB's API terms; Saandio is not endorsed
 * or certified by TMDB). Metadata only: TMDB titles are never presented as playable.
 * Watch-provider data comes from JustWatch via TMDB and must be attributed where shown.
 */
final class TmdbCatalogSource implements CatalogSource {
    static final String MOVIES="tmdb.movies",TV="tmdb.tv";
    static final String ROW_TRENDING_IN="movies.trending_in",ROW_RECENT_EPISODES="tv.recent_episodes",ROW_TV_NEW="tv.latest_updates";
    static final String[][] MOVIE_LANGUAGES={{"te","Telugu"},{"ta","Tamil"},{"hi","Hindi"},{"ml","Malayalam"},{"kn","Kannada"},{"en","Hollywood"}};
    static final String[][] SERIAL_LANGUAGES={{"te","Telugu"},{"ta","Tamil"},{"hi","Hindi"}};
    static final String IMG="https://image.tmdb.org/t/p/";
    static String latestRow(String code){return "movies.latest."+code;}
    static String serialRow(String code){return "tv.serials."+code;}
    interface Keys{String tmdb();}
    interface Today{LocalDate today();}
    static final class Page{
        final List<CatalogItem> items;final int page,totalPages;final boolean fromCache;
        Page(List<CatalogItem> items,int page,int totalPages,boolean fromCache){this.items=items;this.page=page;this.totalPages=totalPages;this.fromCache=fromCache;}
        boolean hasMore(){return page<totalPages;}
    }
    private final boolean tv;private final Keys keys;private final Today today;
    private final Map<String,Map<Integer,String>> genres=new ConcurrentHashMap<>();
    TmdbCatalogSource(boolean tv,Keys keys,Today today){this.tv=tv;this.keys=keys;this.today=today;}

    @Override public String id(){return tv?TV:MOVIES;}
    @Override public String label(){return tv?"TV series · TMDB":"Movies · TMDB";}
    @Override public long defaultTtlMs(){return (tv?30L:15L)*60_000L;}
    @Override public boolean checkOnForeground(){return true;}
    @Override public boolean ready(){return !keys.tmdb().isEmpty();}
    @Override public String notReadyReason(){return "Add a free TMDB API key in Settings → Metadata API keys to load movie and TV catalogs.";}

    @Override public List<Batch> fetch(CatalogHttp http)throws Exception{
        String d=today.today().toString();Map<Integer,String> g;
        try{g=genreMap(http,tv?"tv":"movie");}catch(Exception e){g=Collections.emptyMap();}
        Map<String,String> rows=new LinkedHashMap<>();
        if(!tv){
            rows.put(ROW_TRENDING_IN,"/discover/movie?include_adult=false&region=IN&with_release_type=2%7C3%7C4&release_date.gte="+today.today().minusDays(120)+"&release_date.lte="+d+"&sort_by=popularity.desc");
            for(String[] l:MOVIE_LANGUAGES){
                String extra=l[0].equals("en")?"&with_origin_country=US&vote_count.gte=2":"";
                rows.put(latestRow(l[0]),"/discover/movie?include_adult=false&with_original_language="+l[0]+"&sort_by=primary_release_date.desc&primary_release_date.lte="+d+extra);
            }
        }else{
            for(String[] l:SERIAL_LANGUAGES)rows.put(serialRow(l[0]),"/discover/tv?include_adult=false&with_original_language="+l[0]+"&sort_by=popularity.desc&air_date.gte="+today.today().minusDays(30)+"&air_date.lte="+d);
            rows.put(ROW_RECENT_EPISODES,"/discover/tv?include_adult=false&with_origin_country=IN&sort_by=popularity.desc&air_date.gte="+today.today().minusDays(7)+"&air_date.lte="+d);
            rows.put(ROW_TV_NEW,"/discover/tv?include_adult=false&with_origin_country=IN&sort_by=first_air_date.desc&first_air_date.lte="+d);
        }
        List<Batch> out=new ArrayList<>();Exception first=null;
        for(Map.Entry<String,String> row:rows.entrySet()){
            try{
                List<CatalogItem> items=new ArrayList<>();boolean notModified=true;Set<String> seen=new HashSet<>();
                for(int page=1;page<=2;page++){
                    CatalogHttp.Result r=http.get(url(row.getValue()+"&page="+page));notModified&=r.notModified;
                    JSONObject body=new JSONObject(r.body);
                    for(CatalogItem c:parse(body,tv,g))if(seen.add(c.key()))items.add(c);
                    if(body.optInt("total_pages",1)<=page)break;
                }
                if(row.getKey().equals(ROW_RECENT_EPISODES)&&!notModified)for(int i=0;i<Math.min(15,items.size());i++)try{lastEpisode(http,items.get(i));}catch(Exception ignored){/* row still lists the series */}
                out.add(new Batch(row.getKey(),items,notModified));
            }catch(Exception e){if(first==null)first=e;}
        }
        if(out.isEmpty()&&first!=null)throw first;
        return out;
    }

    /** Remote page for the All Movies / TV grids. Falls back to the last stored copy of the same page when offline. */
    Page discover(CatalogHttp http,DiscoverQuery q,int page)throws Exception{
        String u=url(q.path(page,today.today().toString()));Map<Integer,String> g;
        try{g=genreMap(http,q.movie()?"movie":"tv");}catch(Exception e){g=Collections.emptyMap();}
        try{JSONObject body=new JSONObject(http.get(u).body);return new Page(parse(body,!q.movie(),g),body.optInt("page",page),Math.min(500,body.optInt("total_pages",1)),false);}
        catch(Exception e){
            CatalogHttp.Entry saved=http.cached(u);if(saved==null||saved.body.isEmpty())throw e;
            JSONObject body=new JSONObject(saved.body);return new Page(parse(body,!q.movie(),g),body.optInt("page",page),Math.min(500,body.optInt("total_pages",1)),true);
        }
    }
    /** Cached copy of a discover page without network, for instant display before revalidation. */
    Page cachedPage(CatalogHttp http,DiscoverQuery q,int page){
        try{CatalogHttp.Entry saved=http.cached(url(q.path(page,today.today().toString())));if(saved==null||saved.body.isEmpty())return null;
            Map<Integer,String> g=genres.getOrDefault(q.movie()?"movie":"tv",Collections.emptyMap());
            JSONObject body=new JSONObject(saved.body);return new Page(parse(body,!q.movie(),g),body.optInt("page",page),Math.min(500,body.optInt("total_pages",1)),true);}
        catch(Exception e){return null;}
    }
    List<CatalogItem> search(CatalogHttp http,String query)throws Exception{
        Map<Integer,String> mg,tg;
        try{mg=genreMap(http,"movie");tg=genreMap(http,"tv");}catch(Exception e){mg=Collections.emptyMap();tg=Collections.emptyMap();}
        JSONObject body=new JSONObject(http.get(url("/search/multi?include_adult=false&query="+enc(query))).body);
        JSONArray results=body.optJSONArray("results");List<CatalogItem> out=new ArrayList<>();if(results==null)return out;
        JSONArray movies=new JSONArray(),shows=new JSONArray();
        for(int i=0;i<results.length();i++){JSONObject r=results.optJSONObject(i);if(r==null)continue;String type=r.optString("media_type");if(type.equals("movie"))movies.put(r);else if(type.equals("tv"))shows.put(r);}
        out.addAll(parse(new JSONObject().put("results",movies),false,mg));out.addAll(parse(new JSONObject().put("results",shows),true,tg));
        return out;
    }
    /** Genre names for filters, straight from TMDB rather than a fixed list. */
    Map<Integer,String> genreMap(CatalogHttp http,String kind)throws Exception{
        Map<Integer,String> hit=genres.get(kind);if(hit!=null&&!hit.isEmpty())return hit;
        JSONArray list=new JSONObject(http.get(url("/genre/"+kind+"/list?language=en-US")).body).optJSONArray("genres");
        Map<Integer,String> out=new LinkedHashMap<>();if(list!=null)for(int i=0;i<list.length();i++){JSONObject o=list.optJSONObject(i);if(o!=null&&o.optInt("id")>0)out.put(o.optInt("id"),str(o,"name"));}
        if(!out.isEmpty())genres.put(kind,out);return out;
    }
    /** Fills the latest aired episode (season, episode, air date) for a series card. */
    void lastEpisode(CatalogHttp http,CatalogItem c)throws Exception{
        if(c.tmdbId<=0)return;
        JSONObject o=new JSONObject(http.get(url("/tv/"+c.tmdbId)).body);
        applyLastEpisode(c,o);
    }
    static void applyLastEpisode(CatalogItem c,JSONObject show){
        JSONObject last=show.optJSONObject("last_episode_to_air");
        JSONArray seasons=new JSONArray();JSONArray s=show.optJSONArray("seasons");
        if(s!=null)for(int i=0;i<s.length();i++){JSONObject season=s.optJSONObject(i);if(season!=null&&season.optInt("season_number")>0)try{seasons.put(new JSONObject().put("s",season.optInt("season_number")).put("episodes",season.optInt("episode_count")));}catch(JSONException ignored){/* skip */}}
        if(seasons.length()>0)c.title.seasons=seasons;
        if(last==null)return;
        String air=str(last,"air_date");int se=last.optInt("season_number"),ep=last.optInt("episode_number");
        c.title.season=se;c.title.episode=ep;
        c.episodeLabel="S"+se+" · E"+ep+(air.isEmpty()?"":" · "+shortDate(air));
        c.sourceUpdatedAt=epoch(air);
    }
    List<CatalogItem> season(CatalogHttp http,int tvId,int season,Library.Title show)throws Exception{
        JSONObject o=new JSONObject(http.get(url("/tv/"+tvId+"/season/"+season)).body);
        return parseSeason(o,tvId,show);
    }
    static List<CatalogItem> parseSeason(JSONObject season,int tvId,Library.Title show){
        List<CatalogItem> out=new ArrayList<>();JSONArray eps=season.optJSONArray("episodes");if(eps==null)return out;
        for(int i=0;i<eps.length();i++){JSONObject e=eps.optJSONObject(i);if(e==null)continue;
            int se=e.optInt("season_number"),ep=e.optInt("episode_number");String air=str(e,"air_date");
            Library.Title t=new Library.Title("tv:"+tvId+":s"+se+":e"+ep,"E"+ep+" · "+str(e,"name"),str(e,"overview"),"","",air.length()>=4?air.substring(0,4):"",show==null?"":show.genre);
            t.provider="tmdb";t.providerName="TMDB";t.type="episode";t.season=se;t.episode=ep;t.released=air;t.language=show==null?"":show.language;
            String still=str(e,"still_path");t.poster=still.isEmpty()?(show==null?"":show.background):IMG+"w300"+still;t.background=t.poster;
            CatalogItem c=new CatalogItem(t);c.kind=CatalogItem.SERIES;c.tmdbId=tvId;c.releaseDate=air;c.sourceUpdatedAt=epoch(air);
            c.episodeLabel="S"+se+" · E"+ep+(air.isEmpty()?"":" · "+shortDate(air));
            if(e.optInt("vote_count")>0){c.rating=e.optDouble("vote_average");c.ratingSource="TMDB";}
            out.add(c);
        }
        return out;
    }
    /** "Where to watch" in a region: service names grouped by offer type. Data: JustWatch via TMDB. */
    List<String> watchProviders(CatalogHttp http,boolean series,int id,String region)throws Exception{
        JSONObject o=new JSONObject(http.get(url("/"+(series?"tv":"movie")+"/"+id+"/watch/providers")).body);
        return parseWatchProviders(o,region);
    }
    static List<String> parseWatchProviders(JSONObject body,String region){
        List<String> out=new ArrayList<>();JSONObject results=body.optJSONObject("results");JSONObject r=results==null?null:results.optJSONObject(region);if(r==null)return out;
        String[][] groups={{"flatrate","Stream"},{"free","Free"},{"ads","Free with ads"},{"rent","Rent"},{"buy","Buy"}};
        for(String[] g:groups){JSONArray a=r.optJSONArray(g[0]);if(a==null||a.length()==0)continue;List<String> names=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p!=null&&!str(p,"provider_name").isEmpty()&&!names.contains(str(p,"provider_name")))names.add(str(p,"provider_name"));}if(!names.isEmpty())out.add(g[1]+": "+String.join(", ",names));}
        return out;
    }

    /** Pure mapping of a TMDB discover/search page. Entries without artwork are dropped; nothing is invented. */
    static List<CatalogItem> parse(JSONObject page,boolean tv,Map<Integer,String> genreNames){
        List<CatalogItem> out=new ArrayList<>();JSONArray results=page.optJSONArray("results");if(results==null)return out;
        for(int i=0;i<results.length();i++){
            JSONObject r=results.optJSONObject(i);if(r==null)continue;
            int id=r.optInt("id");String poster=str(r,"poster_path");if(id<=0||poster.isEmpty())continue;
            String name=str(r,tv?"name":"title");if(name.isEmpty())name=str(r,tv?"original_name":"original_title");if(name.isEmpty())continue;
            String date=str(r,tv?"first_air_date":"release_date"),code=str(r,"original_language"),backdrop=str(r,"backdrop_path");
            List<String> g=new ArrayList<>();JSONArray ids=r.optJSONArray("genre_ids");
            if(ids!=null)for(int j=0;j<ids.length();j++){String n=genreNames.get(ids.optInt(j));if(n!=null&&!n.isEmpty())g.add(n);}
            Library.Title t=new Library.Title((tv?"tv:":"movie:")+id,name,str(r,"overview"),"","",date.length()>=4?date.substring(0,4):"",String.join(" · ",g));
            t.provider="tmdb";t.providerName="TMDB";t.type=tv?"series":"movie";t.released=date;t.language=languageName(code);
            t.poster=IMG+"w342"+poster;t.background=backdrop.isEmpty()?IMG+"w780"+poster:IMG+"w780"+backdrop;
            int votes=r.optInt("vote_count");double avg=r.optDouble("vote_average",0);
            if(votes>0&&avg>0)t.rating=String.format(Locale.US,"%.1f",avg);
            CatalogItem c=new CatalogItem(t);c.kind=tv?CatalogItem.SERIES:CatalogItem.MOVIE;c.tmdbId=id;c.languageCode=code;c.releaseDate=date;
            c.popularity=r.optDouble("popularity",0);if(votes>0&&avg>0){c.rating=avg;c.votes=votes;c.ratingSource="TMDB";}
            if(!t.language.isEmpty())c.audio.add(t.language);
            out.add(c);
        }
        return out;
    }
    static String languageName(String code){
        if(code==null||code.isEmpty())return "";
        String known=LanguageTaxonomy.canonical(code);if(!known.equals(LanguageTaxonomy.OTHER))return known;
        String display=new Locale(code).getDisplayLanguage(Locale.ENGLISH);return display.isEmpty()||display.equalsIgnoreCase(code)?code.toUpperCase(Locale.ROOT):display;
    }
    /** TMDB id and media kind from a TMDB-backed title key, or null for other providers. */
    static int[] ids(Library.Title t){
        if(t==null||!"tmdb".equals(t.provider))return null;
        String[] p=t.id.split(":");if(p.length<2)return null;
        try{return new int[]{p[0].equals("tv")?1:0,Integer.parseInt(p[1])};}catch(NumberFormatException e){return null;}
    }
    static long epoch(String date){try{return date.length()==10?LocalDate.parse(date).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli():0;}catch(Exception e){return 0;}}
    static String shortDate(String date){try{LocalDate d=LocalDate.parse(date);return d.getDayOfMonth()+" "+d.getMonth().getDisplayName(java.time.format.TextStyle.SHORT,Locale.ENGLISH)+(d.getYear()==LocalDate.now().getYear()?"":" "+d.getYear());}catch(Exception e){return date;}}
    private String url(String path){return "https://api.themoviedb.org/3"+path+(path.contains("?")?"&":"?")+"api_key="+enc(keys.tmdb());}
    static String str(JSONObject o,String key){return o==null||o.isNull(key)?"":o.optString(key,"");}
    private static String enc(String s){try{return URLEncoder.encode(s==null?"":s,"UTF-8").replace("+","%20");}catch(java.io.UnsupportedEncodingException e){return "";}}
}
