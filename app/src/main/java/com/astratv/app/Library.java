package com.astratv.app;

import android.content.Context;
import org.json.*;
import java.net.URI;
import java.util.*;

/** Serializable catalog data. Providers supply data, never executable code. */
public final class Library {
    private Library() {}
    public static final class Title {
        public String id, name, description, url, cast, year, genre;
        public String provider="library", providerName="Your library", type="movie";
        public String pageUrl="", poster="", background="", rating="";
        public JSONArray videos=new JSONArray(), seasons=new JSONArray();
        public Title(String id,String name,String description,String url,String cast,String year,String genre) {
            this.id=id;this.name=name;this.description=description;this.url=url;this.cast=cast;this.year=year;this.genre=genre;
        }
        public JSONObject json() throws JSONException {
            return new JSONObject().put("id",id).put("title",name).put("description",description).put("url",url)
                .put("cast",cast).put("year",year).put("genre",genre).put("provider",provider).put("providerName",providerName)
                .put("type",type).put("pageUrl",pageUrl).put("poster",poster).put("background",background).put("rating",rating).put("videos",videos).put("seasons",seasons);
        }
        public static Title from(JSONObject o) throws JSONException {
            Title t=new Title(o.getString("id"),o.optString("title",o.optString("name")),o.optString("description"),o.optString("url"),
                o.optString("cast"),o.optString("year",o.optString("releaseInfo")),o.optString("genre"));
            t.provider=o.optString("provider","library");t.providerName=o.optString("providerName","Your library");
            t.type=o.optString("type","movie");t.pageUrl=o.optString("pageUrl");t.poster=o.optString("poster");
            t.background=o.optString("background",t.poster);t.rating=o.optString("rating");
            if(o.optJSONArray("videos")!=null)t.videos=o.getJSONArray("videos");if(o.optJSONArray("seasons")!=null)t.seasons=o.getJSONArray("seasons");return t;
        }
        public String key(){return provider+":"+id;}
    }
    public static final class Addon {
        public final String id,name,description,url;
        public Addon(String id,String name,String description,String url){this.id=id;this.name=name;this.description=description;this.url=url;}
    }
    public static boolean https(String value){try{URI u=new URI(value);return "https".equalsIgnoreCase(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null;}catch(Exception e){return false;}}
    public static List<Title> parse(String json) throws JSONException {
        JSONArray a=new JSONArray(json);if(a.length()>500)throw new JSONException("Maximum 500 titles");
        List<Title> out=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(int i=0;i<a.length();i++){
            Title t=Title.from(a.getJSONObject(i));
            if(t.id.trim().isEmpty()||t.name.trim().isEmpty()||!ids.add(t.id)||!https(t.url))throw new JSONException("Each video needs a unique ID, title and HTTPS media URL");
            t.provider="library";t.providerName="Your library";out.add(t);
        }return out;
    }
    public static List<Title> titles(Context c){try{return parse(c.getSharedPreferences("astra",0).getString("catalog","[]"));}catch(JSONException ignored){return new ArrayList<>();}}
    public static List<Title> samples(){
        List<Title> out=new ArrayList<>();
        Title bunny=new Title("bbb","Big Buck Bunny · Trailer","Official open-movie trailer. A gentle giant takes on three tiny troublemakers. Blender Foundation.","https://media.w3.org/2010/05/bunny/trailer.mp4","Director · Sacha Goedegebure","2008","Animation · Trailer");
        bunny.poster="https://media.w3.org/2010/05/bunny/poster.png";bunny.background=bunny.poster;out.add(bunny);
        Title sintel=new Title("sintel","Sintel · Trailer","Official open-movie trailer. A young traveler searches for an injured dragon. Blender Foundation.","https://media.w3.org/2010/05/sintel/trailer.mp4","Director · Colin Levy","2010","Fantasy · Trailer");
        sintel.poster="https://media.w3.org/2010/05/sintel/poster.png";sintel.background=sintel.poster;out.add(sintel);
        for(Title t:out){t.provider="samples";t.providerName="Open cinema";}return out;
    }
    public static List<Addon> addons(){return Arrays.asList(
        new Addon("movierulz","Latest movies","MovieRulz","https://www.5movierulz.green/"),
        new Addon("tamil","Tamil catch-up","TamilTVSerial","https://www.tamiltvserial.com/"),
        new Addon("telugu","Telugu catch-up","TeluguStream","https://telugustream.com/"),
        new Addon("news","Telugu news","Broadcaster streams",""),
        new Addon("netmirror","NetMirror","Native provider connection required",""));}
}
