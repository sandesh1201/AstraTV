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
        public String language="", group="", released="", seriesUrl="";
        public int season=1,episode=0;
        public JSONObject headers=new JSONObject();
        public JSONArray videos=new JSONArray(), seasons=new JSONArray();
        public Title(String id,String name,String description,String url,String cast,String year,String genre) {
            this.id=id;this.name=name;this.description=description;this.url=url;this.cast=cast;this.year=year;this.genre=genre;
        }
        public JSONObject json() throws JSONException {
            return new JSONObject().put("id",id).put("title",name).put("description",description).put("url",url)
                .put("cast",cast).put("year",year).put("genre",genre).put("provider",provider).put("providerName",providerName)
                .put("type",type).put("pageUrl",pageUrl).put("poster",poster).put("background",background).put("rating",rating).put("videos",videos).put("seasons",seasons)
                .put("language",language).put("group",group).put("released",released).put("seriesUrl",seriesUrl).put("season",season).put("episode",episode).put("headers",headers);
        }
        public static Title from(JSONObject o) throws JSONException {
            Title t=new Title(o.getString("id"),o.optString("title",o.optString("name")),o.optString("description"),o.optString("url"),
                o.optString("cast"),o.optString("year",o.optString("releaseInfo")),o.optString("genre"));
            t.provider=o.optString("provider","library");t.providerName=o.optString("providerName","Your library");
            t.type=o.optString("type","movie");t.pageUrl=o.optString("pageUrl");t.poster=o.optString("poster");
            t.background=o.optString("background",t.poster);t.rating=o.optString("rating");
            if(o.optJSONArray("videos")!=null)t.videos=o.getJSONArray("videos");if(o.optJSONArray("seasons")!=null)t.seasons=o.getJSONArray("seasons");
            t.language=o.optString("language");t.group=o.optString("group");t.released=o.optString("released");t.seriesUrl=o.optString("seriesUrl");t.season=o.optInt("season",1);t.episode=o.optInt("episode");
            if(o.optJSONObject("headers")!=null)t.headers=o.getJSONObject("headers");return t;
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
        new Addon("netmirror","Netflix (NetMirror)","NetMirror · Netflix",""),
        new Addon("netmirror_pv","Prime (NetMirror)","NetMirror · Prime Video",""),
        new Addon("netmirror_hs","Hotstar (NetMirror)","NetMirror · Hotstar",""),
        new Addon("movierulz","MovieRulz","Telugu & Hindi movies","https://www.5movierulz.green/"),
        new Addon("tamil","TamilTVSerials","Tamil series & episodes","https://www.tamiltvserial.com/"),
        new Addon("telugu","TeluguStream","Telugu series & episodes","https://telugustream.com/"),
        new Addon("news","TV channels","Indian live TV · language & genre",""));}
    public static boolean netmirror(String id){return id.equals("netmirror")||id.equals("netmirror_pv")||id.equals("netmirror_hs");}
    public static String platform(String id){return id.equals("netmirror_pv")?"pv":id.equals("netmirror_hs")?"hs":"nf";}
    public static String providerName(String id){for(Addon a:addons())if(a.id.equals(id))return a.name;return "Your library";}
}
