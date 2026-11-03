package com.astratv.app;

import android.content.Context;
import org.json.*;
import java.net.URLEncoder;
import java.util.*;

/** TMDB / OMDb / Trakt overlay. Never writes into library JSON. Never fabricates scores. */
final class MetadataRepository {
    private final Context context;
    private final MetadataStore store;
    private long nextAllowed;
    MetadataRepository(Context context){this.context=context;this.store=new MetadataStore(context);}
    JSONObject cached(Library.Title title){return store.get(title.key(),true);}
    JSONObject enrich(Library.Title title)throws Exception{return enrich(title,null);}
    JSONObject enrich(Library.Title title,String forceTmdbId)throws Exception{
        if(!ApiKeys.metadataReady(context))return null;
        if(forceTmdbId==null){JSONObject hit=store.get(title.key(),false);if(hit!=null&&hit.optBoolean("manual"))return hit;if(hit!=null&&forceTmdbId==null)return hit;}
        String kind=MetadataMatcher.kind(title);
        MetadataMatcher.Candidate pick;
        float confidence;
        int[] known=forceTmdbId==null?TmdbCatalogSource.ids(title):null;
        if(forceTmdbId!=null){pick=new MetadataMatcher.Candidate(forceTmdbId,title.name,"",title.year,"",kind);confidence=1f;}
        else if(known!=null){kind=known[0]==1?"tv":"movie";pick=new MetadataMatcher.Candidate(String.valueOf(known[1]),title.name,"",title.year,"",kind);confidence=1f;}
        else{
            List<MetadataMatcher.Candidate> found=search(title,kind);
            pick=MetadataMatcher.best(title,found);
            confidence=pick==null?0:MetadataMatcher.score(title,pick);
            if(pick==null||!MetadataMatcher.autoApply(confidence)&&forceTmdbId==null){
                JSONObject miss=new JSONObject().put("confidence",confidence).put("status","unmatched");
                store.put(title.key(),miss,false);return miss;
            }
        }
        JSONObject payload=details(pick,kind);
        payload.put("confidence",confidence).put("tmdbId",pick.id).put("type",kind);
        fillRatings(payload,pick,kind);
        store.put(title.key(),payload,forceTmdbId!=null);return payload;
    }
    List<MetadataMatcher.Candidate> search(Library.Title title,String kind)throws Exception{
        String path="/search/"+kind+"?query="+enc(title.name);
        int year=CatalogSort.year(title);if(year>0)path+=(kind.equals("movie")?"&year=":"&first_air_date_year=")+year;
        JSONObject data=tmdb(path);JSONArray results=data.optJSONArray("results");List<MetadataMatcher.Candidate> out=new ArrayList<>();
        if(results==null)return out;
        for(int i=0;i<Math.min(results.length(),8);i++){
            JSONObject o=results.getJSONObject(i);
            String name=o.optString(kind.equals("movie")?"title":"name");
            String date=o.optString(kind.equals("movie")?"release_date":"first_air_date");
            out.add(new MetadataMatcher.Candidate(Integer.toString(o.getInt("id")),name,o.optString("original_"+ (kind.equals("movie")?"title":"name")),date,o.optString("original_language"),kind));
        }return out;
    }
    JSONArray filmography(String personId)throws Exception{
        JSONObject data=tmdb("/person/"+personId+"/combined_credits");JSONArray cast=data.optJSONArray("cast");return cast==null?new JSONArray():cast;
    }
    JSONObject person(String personId)throws Exception{return tmdb("/person/"+personId);}
    /** TMDB movie trending for "day" or "week", ranked 1..n. Cached for 3 hours. Empty without a TMDB key. */
    List<CatalogRanking.Trend> trending(String window)throws Exception{
        List<CatalogRanking.Trend> out=new ArrayList<>();
        if(!ApiKeys.metadataReady(context)||!(window.equals("day")||window.equals("week")))return out;
        android.content.SharedPreferences ui=context.getSharedPreferences("astra_ui_v1",0);String cacheKey="trending."+window+".v1";
        JSONObject cache=null;try{cache=new JSONObject(ui.getString(cacheKey,"{}"));}catch(JSONException ignored){/* refetch */}
        JSONArray items=cache!=null&&System.currentTimeMillis()-cache.optLong("at")<3L*60*60*1000?cache.optJSONArray("items"):null;
        if(items==null){
            items=new JSONArray();
            for(int page=1;page<=3;page++){
                JSONArray results=tmdb("/trending/movie/"+window+"?page="+page).optJSONArray("results");if(results==null)break;
                for(int i=0;i<results.length();i++){JSONObject r=results.getJSONObject(i);String date=r.optString("release_date");
                    items.put(new JSONObject().put("title",r.optString("title")).put("original",r.optString("original_title")).put("year",date.length()>=4?date.substring(0,4):""));}
            }
            ui.edit().putString(cacheKey,new JSONObject().put("at",System.currentTimeMillis()).put("items",items).toString()).apply();
        }
        for(int i=0;i<items.length();i++){JSONObject o=items.getJSONObject(i);int year=0;try{year=Integer.parseInt(o.optString("year"));}catch(NumberFormatException ignored){/* unknown */}
            out.add(new CatalogRanking.Trend(o.optString("title"),o.optString("original"),year,i+1));}
        return out;
    }
    private JSONObject details(MetadataMatcher.Candidate pick,String kind)throws Exception{
        JSONObject o=tmdb("/"+kind+"/"+pick.id+"?append_to_response=credits,videos,reviews,similar,release_dates,content_ratings,images");
        JSONObject out=new JSONObject();
        out.put("name",o.optString(kind.equals("movie")?"title":"name"));
        out.put("tagline",o.optString("tagline"));
        out.put("overview",o.optString("overview"));
        out.put("year",(kind.equals("movie")?o.optString("release_date"):o.optString("first_air_date")));
        int runtime=kind.equals("movie")?o.optInt("runtime"):firstRuntime(o.optJSONArray("episode_run_time"));
        if(runtime>0)out.put("runtime",runtime);
        out.put("poster",image(o.optString("poster_path"),"w342"));
        out.put("backdrop",image(o.optString("backdrop_path"),"w780"));
        out.put("genres",names(o.optJSONArray("genres")));
        out.put("languages",names(o.optJSONArray("spoken_languages"),"english_name"));
        out.put("collection",o.optJSONObject("belongs_to_collection")==null?"":o.getJSONObject("belongs_to_collection").optString("name"));
        out.put("age",age(o,kind));
        if(o.optDouble("vote_average")>0&&o.optInt("vote_count")>0)out.put("tmdbScore",String.format(Locale.US,"%.1f",o.optDouble("vote_average")));
        JSONArray reviews=new JSONArray();JSONObject reviewBlock=o.optJSONObject("reviews");JSONArray reviewResults=reviewBlock==null?null:reviewBlock.optJSONArray("results");
        if(reviewResults!=null)for(int i=0;i<Math.min(reviewResults.length(),3);i++){
            JSONObject r=reviewResults.getJSONObject(i);String content=r.optString("content");if(content.isEmpty())continue;
            if(content.length()>600)content=content.substring(0,600)+"…";
            reviews.put(new JSONObject().put("author",r.optString("author")).put("content",content));
        }
        if(reviews.length()>0)out.put("tmdbReviews",reviews);
        JSONArray cast=new JSONArray();JSONObject credits=o.optJSONObject("credits");JSONArray creditCast=credits==null?null:credits.optJSONArray("cast");
        if(creditCast!=null)for(int i=0;i<Math.min(creditCast.length(),12);i++){
            JSONObject p=creditCast.getJSONObject(i);String photo=image(p.optString("profile_path"),"w185");
            cast.put(new JSONObject().put("id",p.optInt("id")).put("name",p.optString("name")).put("character",p.optString("character")).put("photo",photo));
        }
        if(cast.length()>0)out.put("cast",cast);
        JSONArray similar=new JSONArray();JSONObject sim=o.optJSONObject("similar");JSONArray simResults=sim==null?null:sim.optJSONArray("results");
        if(simResults!=null)for(int i=0;i<Math.min(simResults.length(),12);i++){
            JSONObject s=simResults.getJSONObject(i);
            similar.put(new JSONObject().put("name",s.optString(kind.equals("movie")?"title":"name")).put("year",s.optString(kind.equals("movie")?"release_date":"first_air_date")).put("poster",image(s.optString("poster_path"),"w185")).put("id",s.optInt("id")).put("type",kind));
        }
        if(similar.length()>0)out.put("similar",similar);
        String trailer=trailerKey(o.optJSONObject("videos"));if(!trailer.isEmpty())out.put("trailerKey",trailer);
        String official=officialTrailerKey(o.optJSONObject("videos"));if(!official.isEmpty())out.put("officialTrailerKey",official);
        if(o.has("imdb_id")&&!o.optString("imdb_id").isEmpty())out.put("imdbId",o.optString("imdb_id"));
        return out;
    }
    private void fillRatings(JSONObject payload,MetadataMatcher.Candidate pick,String kind){
        String omdb=ApiKeys.omdb(context);String imdb=payload.optString("imdbId");
        if(!omdb.isEmpty()&&(!imdb.isEmpty()||!pick.title.isEmpty()))try{
            String path="https://www.omdbapi.com/?apikey="+enc(omdb)+(imdb.isEmpty()?"&t="+enc(pick.title)+"&y="+enc(pick.year):"&i="+enc(imdb));
            JSONObject data=new JSONObject(ProviderRepository.request(path,"").body);
            if(data.optString("Response","TRUE").equalsIgnoreCase("False"))return;
            if(!data.optString("imdbRating").isEmpty()&&!data.optString("imdbRating").equals("N/A"))payload.put("imdbScore",data.getString("imdbRating"));
            JSONArray ratings=data.optJSONArray("Ratings");if(ratings!=null)for(int i=0;i<ratings.length();i++){
                JSONObject r=ratings.getJSONObject(i);String src=r.optString("Source"),val=r.optString("Value");
                if(val.isEmpty()||val.equals("N/A"))continue;
                if(src.contains("Rotten"))payload.put("rtScore",val);
                if(src.contains("Metacritic"))payload.put("metaScore",val);
            }
        }catch(Exception ignored){}
        String trakt=ApiKeys.trakt(context);
        if(!trakt.isEmpty())try{
            Map<String,String> headers=new LinkedHashMap<>();headers.put("trakt-api-version","2");headers.put("trakt-api-key",trakt);headers.put("Content-Type","application/json");
            String body=ProviderRepository.request("https://api.trakt.tv/search/tmdb/"+pick.id+"?type="+(kind.equals("tv")?"show":"movie"),"",headers).body;
            JSONArray found=new JSONArray(body);if(found.length()==0)return;
            JSONObject node=found.getJSONObject(0).optJSONObject(kind.equals("tv")?"show":"movie");if(node==null)return;
            JSONObject ids=node.optJSONObject("ids");if(ids==null||ids.optInt("trakt")<=0)return;
            JSONObject ratings=new JSONObject(ProviderRepository.request("https://api.trakt.tv/"+(kind.equals("tv")?"shows":"movies")+"/"+ids.getInt("trakt")+"/ratings","",headers).body);
            if(ratings.optDouble("rating")>0&&ratings.optInt("votes")>0)payload.put("traktScore",String.format(Locale.US,"%.1f",ratings.optDouble("rating")));
        }catch(Exception ignored){}
    }
    private JSONObject tmdb(String path)throws Exception{
        throttle();TmdbRoute route=ApiKeys.tmdbRoute(context);
        if(route==null)throw new IllegalStateException("No catalog service configured");
        Exception last=null;
        for(int attempt=0;attempt<3;attempt++){
            try{return new JSONObject(ProviderRepository.request(route.url(path),"",route.headers()).body);}
            catch(Exception e){last=e;if(e.getMessage()!=null&&e.getMessage().contains("HTTP 429"))Thread.sleep(1500L*(attempt+1));else throw e;}
        }
        throw last;
    }
    private synchronized void throttle()throws InterruptedException{
        long wait=nextAllowed-System.currentTimeMillis();if(wait>0)Thread.sleep(wait);nextAllowed=System.currentTimeMillis()+280;
    }
    private static String trailerKey(JSONObject videos){
        if(videos==null)return "";JSONArray results=videos.optJSONArray("results");if(results==null)return "";
        String fallback="";
        for(int i=0;i<results.length();i++){
            JSONObject v=results.optJSONObject(i);if(v==null)continue;
            if(!"YouTube".equalsIgnoreCase(v.optString("site"))||v.optString("key").isEmpty())continue;
            boolean trailer=v.optString("type").equalsIgnoreCase("Trailer");
            boolean official=v.optBoolean("official");
            if(trailer&&official)return v.optString("key");
            if(trailer&&fallback.isEmpty())fallback=v.optString("key");
        }
        return fallback;
    }
    /** A YouTube trailer TMDB marks as official (uploaded by the studio or distributor); empty otherwise. */
    static String officialTrailerKey(JSONObject videos){
        JSONArray results=videos==null?null:videos.optJSONArray("results");if(results==null)return "";
        for(int i=0;i<results.length();i++){
            JSONObject v=results.optJSONObject(i);if(v==null)continue;
            if("YouTube".equalsIgnoreCase(v.optString("site"))&&"Trailer".equalsIgnoreCase(v.optString("type"))&&v.optBoolean("official")&&v.optString("key").matches("[A-Za-z0-9_-]{11}"))return v.optString("key");
        }
        return "";
    }
    private static String image(String path,String size){return path==null||path.isEmpty()?"":"https://image.tmdb.org/t/p/"+size+path;}
    private static String names(JSONArray a){return names(a,"name");}
    private static String names(JSONArray a,String key){
        if(a==null)return "";List<String> out=new ArrayList<>();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&!o.optString(key).isEmpty())out.add(o.optString(key));}return String.join(" · ",out);
    }
    private static int firstRuntime(JSONArray a){return a==null||a.length()==0?0:a.optInt(0);}
    private static String age(JSONObject o,String kind){
        if(kind.equals("movie")){
            JSONObject block=o.optJSONObject("release_dates");JSONArray results=block==null?null:block.optJSONArray("results");
            if(results!=null)for(int i=0;i<results.length();i++){
                JSONObject country=results.optJSONObject(i);if(country==null)continue;
                JSONArray dates=country.optJSONArray("release_dates");if(dates==null)continue;
                for(int j=0;j<dates.length();j++){JSONObject date=dates.optJSONObject(j);String cert=date==null?"":date.optString("certification");if(!cert.isEmpty())return cert;}
            }
        }else{
            JSONObject block=o.optJSONObject("content_ratings");JSONArray results=block==null?null:block.optJSONArray("results");
            if(results!=null)for(int i=0;i<results.length();i++){JSONObject row=results.optJSONObject(i);String rating=row==null?"":row.optString("rating");if(!rating.isEmpty())return rating;}
        }
        return "";
    }
    private static String enc(String s){try{return URLEncoder.encode(s==null?"":s,"UTF-8").replace("+","%20");}catch(java.io.UnsupportedEncodingException e){return "";}}
}
