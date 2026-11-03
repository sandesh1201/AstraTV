package com.astratv.app;

import org.json.*;
import java.util.*;

/**
 * Filters for the All Movies, HD and TV grids. Maps to TMDB discover parameters for remote paging,
 * or filters already-synced items locally for sorts TMDB cannot express ("recently added") and for
 * verified quality, which only authorized playback providers report.
 */
final class DiscoverQuery {
    static final List<String> SORTS=Collections.unmodifiableList(Arrays.asList("newest","added","trending","rating"));
    String kind="movie",languageCode="",genreName="",sort="newest";
    int year,genreId,minHeight;
    float minRating;

    static String sortLabel(String sort){
        switch(sort){case "added":return "Recently added";case "trending":return "Trending";case "rating":return "Top rated (TMDB)";default:return "Newest first";}
    }
    /** Remote queries go to TMDB discover; the rest are answered from the local catalog store. */
    boolean remote(){return !"added".equals(sort)&&minHeight==0;}
    boolean movie(){return !"tv".equals(kind);}

    /** TMDB discover path (without api key). {@code today} is yyyy-MM-dd so unreleased titles stay out of "newest". */
    String path(int page,String today){
        boolean movie=movie();String date=movie?"primary_release_date":"first_air_date";
        StringBuilder p=new StringBuilder("/discover/").append(movie?"movie":"tv").append("?include_adult=false&page=").append(Math.max(1,page));
        switch(sort){
            case "trending":p.append("&sort_by=popularity.desc");break;
            case "rating":p.append("&sort_by=vote_average.desc&vote_count.gte=50");break;
            default:p.append("&sort_by=").append(date).append(".desc");
        }
        p.append("&").append(date).append(".lte=").append(today);
        if(!languageCode.isEmpty())p.append("&with_original_language=").append(languageCode);
        else if(movie)p.append("&region=IN&with_release_type=2%7C3%7C4");
        else p.append("&with_origin_country=IN");
        if(year>0)p.append(movie?"&primary_release_year=":"&first_air_date_year=").append(year);
        if(genreId>0)p.append("&with_genres=").append(genreId);
        if(minRating>0){p.append("&vote_average.gte=").append(trim(minRating));if(!"rating".equals(sort))p.append("&vote_count.gte=10");}
        return p.toString();
    }
    /** Local filter used for "recently added" and verified-quality views. */
    boolean matches(CatalogItem c){
        if(!(movie()?CatalogItem.MOVIE:CatalogItem.SERIES).equals(c.kind))return false;
        if(!languageCode.isEmpty()&&!languageCode.equalsIgnoreCase(c.languageCode)){
            String wanted=LanguageTaxonomy.canonical(languageCode);
            if(wanted.equals(LanguageTaxonomy.OTHER)){if(!languageCode.trim().equalsIgnoreCase(c.title.language.trim()))return false;}
            else if(!wanted.equals(LanguageTaxonomy.of(c.title)))return false;
        }
        if(year>0&&c.year()!=year)return false;
        if(!genreName.isEmpty()){boolean hit=false;for(String g:GenreTaxonomy.of(c.title))if(g.equalsIgnoreCase(GenreTaxonomy.canonical(genreName))){hit=true;break;}if(!hit&&!c.title.genre.toLowerCase(Locale.ROOT).contains(genreName.toLowerCase(Locale.ROOT)))return false;}
        if(minHeight>0&&c.verifiedHeight<minHeight)return false;
        if(minRating>0&&c.rating<minRating)return false;
        return true;
    }
    /**
     * Local ordering matching the chosen sort. Newest uses the official release date; added uses the
     * provider's own added date when it reports one, otherwise Saandio's first-seen time.
     */
    static List<CatalogItem> order(List<CatalogItem> items,String sort){
        List<CatalogItem> out=new ArrayList<>(items);
        Comparator<CatalogItem> newest=Comparator.comparingInt(CatalogItem::dateKey).reversed().thenComparing(c->c.title.name,String.CASE_INSENSITIVE_ORDER);
        switch(sort){
            case "added":out.sort(Comparator.comparingLong((CatalogItem c)->c.addedAt>0?c.addedAt:Math.max(0,c.firstSeen)).reversed().thenComparing(newest));break;
            case "trending":out.sort(Comparator.comparingDouble((CatalogItem c)->c.popularity).reversed().thenComparing(newest));break;
            case "rating":out.sort(Comparator.comparingDouble((CatalogItem c)->c.rating).reversed().thenComparing(newest));break;
            default:out.sort(newest);
        }
        return out;
    }
    String key(){return kind+"|"+languageCode+"|"+year+"|"+genreId+"|"+genreName+"|"+sort+"|"+trim(minRating)+"|"+minHeight;}
    DiscoverQuery copy(){DiscoverQuery q=new DiscoverQuery();q.kind=kind;q.languageCode=languageCode;q.genreName=genreName;q.sort=sort;q.year=year;q.genreId=genreId;q.minHeight=minHeight;q.minRating=minRating;return q;}
    JSONObject json(){try{return new JSONObject().put("kind",kind).put("lang",languageCode).put("genre",genreName).put("genreId",genreId).put("sort",sort).put("year",year).put("minHeight",minHeight).put("minRating",(double)minRating);}catch(JSONException e){return new JSONObject();}}
    static DiscoverQuery from(String json,String kind){
        DiscoverQuery q=new DiscoverQuery();q.kind=kind;
        try{JSONObject o=new JSONObject(json==null||json.isEmpty()?"{}":json);q.languageCode=o.optString("lang");q.genreName=o.optString("genre");q.genreId=o.optInt("genreId");
            q.sort=SORTS.contains(o.optString("sort"))?o.optString("sort"):"newest";q.year=o.optInt("year");q.minHeight=o.optInt("minHeight");q.minRating=(float)o.optDouble("minRating",0);}
        catch(JSONException ignored){/* defaults */}
        return q;
    }
    private static String trim(float f){return f==(int)f?String.valueOf((int)f):String.valueOf(f);}
}
