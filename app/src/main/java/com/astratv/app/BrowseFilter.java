package com.astratv.app;

import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Combinable Home/Search/Library filters. Stored in astra_ui_v1, not library JSON. */
final class BrowseFilter {
    final Set<String> genres=new LinkedHashSet<>();
    final Set<String> languages=new LinkedHashSet<>();
    final Set<String> providers=new LinkedHashSet<>();
    final Set<String> types=new LinkedHashSet<>();
    int yearMin,yearMax,runtimeMin,runtimeMax;
    float minRating;
    String country="",contentRating="",quality="",watched="all",sort="newest";
    static BrowseFilter load(SharedPreferences ui){
        BrowseFilter f=new BrowseFilter();
        try{JSONObject o=new JSONObject(ui.getString("browse.filter.v1","{}"));
            add(f.genres,o.optJSONArray("genres"));add(f.languages,o.optJSONArray("languages"));
            add(f.providers,o.optJSONArray("providers"));add(f.types,o.optJSONArray("types"));
            f.yearMin=o.optInt("yearMin");f.yearMax=o.optInt("yearMax");f.runtimeMin=o.optInt("runtimeMin");f.runtimeMax=o.optInt("runtimeMax");
            f.minRating=(float)o.optDouble("minRating");f.country=o.optString("country");f.contentRating=o.optString("contentRating");
            f.quality=o.optString("quality");f.watched=o.optString("watched","all");f.sort=o.optString("sort","newest");
        }catch(JSONException ignored){}
        if(f.sort.isEmpty())f.sort="newest";return f;
    }
    void save(SharedPreferences ui){
        try{JSONObject o=new JSONObject().put("genres",array(genres)).put("languages",array(languages)).put("providers",array(providers)).put("types",array(types))
            .put("yearMin",yearMin).put("yearMax",yearMax).put("runtimeMin",runtimeMin).put("runtimeMax",runtimeMax).put("minRating",minRating)
            .put("country",country).put("contentRating",contentRating).put("quality",quality).put("watched",watched).put("sort",sort);
            ui.edit().putString("browse.filter.v1",o.toString()).apply();
        }catch(JSONException ignored){}
    }
    boolean active(){return !genres.isEmpty()||!languages.isEmpty()||!providers.isEmpty()||!types.isEmpty()||yearMin>0||yearMax>0||runtimeMin>0||runtimeMax>0||minRating>0||!country.isEmpty()||!contentRating.isEmpty()||!quality.isEmpty()||!"all".equals(watched);}
    boolean matches(Library.Title t,int position){return matches(t,position,0,0,"","");}
    boolean matches(Library.Title t,int position,int runtime,int tmdbYear,String originCountry,String age){
        if(!genres.isEmpty()){boolean hit=false;for(String g:GenreTaxonomy.of(t))if(genres.contains(g)){hit=true;break;}if(!hit)return false;}
        int y=CatalogSort.year(t);if(y<=0)y=tmdbYear;
        if(yearMin>0&&(y<=0||y<yearMin))return false;
        if(yearMax>0&&(y<=0||y>yearMax))return false;
        if(!languages.isEmpty()&&!languages.contains(t.language)&&!languages.contains(LanguageTaxonomy.of(t)))return false;
        if(!country.isEmpty()&&(originCountry==null||!originCountry.equalsIgnoreCase(country)))return false;
        if(minRating>0&&CatalogSort.popularity(t)<minRating)return false;
        if(runtimeMin>0&&(runtime<=0||runtime<runtimeMin))return false;
        if(runtimeMax>0&&(runtime<=0||runtime>runtimeMax))return false;
        if(!contentRating.isEmpty()&&(age==null||!age.equalsIgnoreCase(contentRating)))return false;
        if(!quality.isEmpty()&&(t.name+" "+t.genre).toLowerCase(Locale.ROOT).indexOf(quality.toLowerCase(Locale.ROOT))<0)return false;
        if(!providers.isEmpty()&&!providers.contains(t.provider))return false;
        if("watched".equals(watched)&&position<=0)return false;
        if("unwatched".equals(watched)&&position>0)return false;
        if(!types.isEmpty()){String type=t.type==null||t.type.isEmpty()?"movie":t.type;if(type.equals("episode"))type="series";if(!types.contains(type))return false;}
        return true;
    }
    private static void add(Set<String> set,JSONArray a){if(a==null)return;for(int i=0;i<a.length();i++){String v=a.optString(i);if(!v.isEmpty())set.add(v);}}
    private static JSONArray array(Set<String> set){JSONArray a=new JSONArray();for(String s:set)a.put(s);return a;}
}
