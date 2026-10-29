package com.astratv.app;

import android.content.Context;
import org.json.*;
import java.util.*;

/** Data-only providers: no downloaded executable add-on code. */
public final class Library {
    public static class Title {
        public String id, name, description, url, cast, year, genre;
        public Title(String id, String name, String description, String url, String cast, String year, String genre) {
            this.id=id; this.name=name; this.description=description; this.url=url; this.cast=cast; this.year=year; this.genre=genre;
        }
    }
    public static class Addon {
        public String id, name, description, url;
        public Addon(String id,String name,String description,String url) { this.id=id;this.name=name;this.description=description;this.url=url; }
    }
    public static List<Title> titles(Context c) {
        List<Title> out=new ArrayList<>();
        out.add(new Title("bbb","Big Buck Bunny","A gentle giant. Three tiny troublemakers. One extraordinary afternoon. An open movie from the Blender Foundation, provided here as a playback demonstration.","https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4","Blender Foundation · Director: Sacha Goedegebure","2008","Animation · Open movie"));
        try { out.addAll(parse(c.getSharedPreferences("astra",0).getString("catalog","[]"))); } catch(Exception ignored) { }
        return out;
    }
    public static boolean https(String value) {
        try { java.net.URI u=new java.net.URI(value);return "https".equalsIgnoreCase(u.getScheme()) && u.getHost()!=null && u.getUserInfo()==null; } catch(Exception e) {return false;}
    }
    public static List<Title> parse(String json) throws JSONException {
        JSONArray a=new JSONArray(json); if(a.length()>500) throw new JSONException("Maximum 500 titles");
        List<Title> out=new ArrayList<>(); Set<String> ids=new HashSet<>();ids.add("bbb");
        for(int i=0;i<a.length();i++) {
            JSONObject o=a.getJSONObject(i);String id=o.getString("id"),name=o.getString("title"),url=o.getString("url");
            if(id.isBlank() || name.isBlank() || !ids.add(id) || !https(url)) throw new JSONException("Unique IDs, a title and an HTTPS media URL are required");
            out.add(new Title(id,name,o.optString("description",""),url,o.optString("cast","Not supplied"),o.optString("year",""),o.optString("genre","Your library")));
        }
        return out;
    }
    public static List<Addon> addons() {return Arrays.asList(
        new Addon("netmirror","NetMirror","Launch your installed TV app, or open the website.","https://netmirror.gg/10/en-us"),
        new Addon("movierulz","Latest Movies","MovieRulz · website add-on","https://www.5movierulz.green/"),
        new Addon("tamil","Tamil Catch-up","TamilTVSerial · serials and shows","https://www.tamiltvserial.com/"),
        new Addon("telugu","Telugu Catch-up","TeluguStream · serials and shows","https://telugustream.com/"),
        new Addon("news","Telugu News","Official broadcaster pages in one place.","")); }
}
