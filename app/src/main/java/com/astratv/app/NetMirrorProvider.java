package com.astratv.app;

import android.content.SharedPreferences;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Independent implementation of the HTTP shapes in the user-supplied Flutter reference.
 * No copied Flutter code, bundled APK, session forging, or ad-verification impersonation.
 */
final class NetMirrorProvider {
    private final SharedPreferences prefs;
    interface Transport{String get(String url,Map<String,String> headers)throws IOException;}
    private final Transport transport;
    NetMirrorProvider(SharedPreferences prefs){this(prefs,(url,h)->ProviderRepository.request(url,h.getOrDefault("Referer",""),h).body);}
    NetMirrorProvider(SharedPreferences prefs,Transport transport){this.prefs=prefs;this.transport=transport;}
    String base(){return prefs.getString("netmirror.api","https://net50.cc/mobile").replaceAll("/+$","");}
    private Map<String,String> headers(String ott){
        Map<String,String> h=new HashMap<>();String cookie=prefs.getString("netmirror.session","");
        if(!cookie.isEmpty())h.put("Cookie","t_hash_t="+cookie+"; ott="+ott+";");
        h.put("Referer",base()+"/home?app=1");return h;
    }
    private String get(String path,String ott)throws IOException{
        return transport.get(base()+path,headers(ott));
    }
    private JSONObject object(String path,String ott)throws Exception{
        String body=get(path,ott).trim();if(!body.startsWith("{"))throw new IOException("NetMirror returned a session or redirect page instead of API data. Its server needs a valid session or updated API address.");return new JSONObject(body);
    }
    private static String prefix(String ott){return ott.equals("nf")?"":ott+"/";}
    private static String encode(String s){try{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}catch(java.io.UnsupportedEncodingException e){throw new IllegalStateException(e);}}
    private static String[] parts(Library.Title t){return t.id.split("~",2);}
    private static String poster(String ott,String id){return "https://imgcdn.media/"+(ott.equals("nf")?"poster/v/":ott.equals("hs")?"hs/v/166/":ott+"/341/")+id+".jpg";}
    private static Library.Title title(String ott,String id,String name){
        Library.Title t=new Library.Title(ott+"~"+id,name,"","","","","NetMirror");t.provider="netmirror";t.providerName="NetMirror";t.poster=poster(ott,id);t.background=t.poster;return t;
    }
    List<Library.Title> catalog(String search)throws Exception{
        String ott=prefs.getString("netmirror.ott","nf");List<Library.Title> out=new ArrayList<>();
        if(!search.isEmpty()){
            JSONObject o=object("/"+prefix(ott)+"search.php?s="+encode(search),ott);JSONArray items=o.optJSONArray("searchResult");
            if(items!=null)for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);Library.Title t=title(ott,item.getString("id"),item.optString("t"));t.year=item.optString("y");out.add(t);}
        }else{
            Document doc=Jsoup.parse(get("/home?app=1",ott),base()+"/");Set<String> ids=new HashSet<>();
            for(Element el:doc.select("[data-post]")){
                String id=el.attr("data-post");if(!id.matches("[a-zA-Z0-9_-]+")||!ids.add(id))continue;
                Element image=el.selectFirst("img");String name=el.attr("data-title");if(name.isEmpty()&&image!=null)name=image.attr("alt");if(name.isEmpty())name="NetMirror · "+id;
                out.add(title(ott,id,name));if(out.size()>=60)break;
            }
        }
        if(out.isEmpty())throw new IOException("NetMirror API returned no catalog. The source repository's server may be unavailable or require a valid session. Configure its API or connect a manifest in Add-ons.");
        return out;
    }
    Library.Title details(Library.Title t)throws Exception{
        String[] p=parts(t);if(p.length!=2)throw new IOException("Invalid NetMirror title ID");
        JSONObject o=object("/"+prefix(p[0])+"post.php?id="+encode(p[1]),p[0]);
        if(!o.has("title"))throw new IOException("NetMirror did not return title metadata. Reconnect the provider.");
        t.name=o.optString("title",t.name);t.description=o.optString("desc");t.cast=o.optString("cast");t.genre=o.optString("genre");t.year=o.optString("year");
        t.type=o.optString("type","m").equals("m")?"movie":"series";
        if(o.optJSONArray("episodes")!=null)t.videos=o.getJSONArray("episodes");if(o.optJSONArray("season")!=null)t.seasons=o.getJSONArray("season");return t;
    }
    List<ProviderRepository.Stream> streams(Library.Title t)throws Exception{
        String[] p=parts(t);if(p.length!=2)throw new IOException("Invalid NetMirror title ID");
        String body=get("/"+prefix(p[0])+"playlist.php?id="+encode(p[1]),p[0]).trim();if(!body.startsWith("["))throw new IOException("NetMirror did not return a playlist. Check the source API and session.");JSONArray response=new JSONArray(body);
        List<ProviderRepository.Stream> out=new ArrayList<>();
        for(int i=0;i<response.length();i++){
            JSONObject item=response.getJSONObject(i);JSONArray sources=item.optJSONArray("sources");if(sources==null)continue;
            for(int j=0;j<sources.length();j++){
                JSONObject s=sources.getJSONObject(j);String url=ProviderParser.absolute(base()+"/",s.optString("file"));if(!Library.https(url))continue;
                Map<String,String> playbackHeaders=new HashMap<>();playbackHeaders.put("Referer",base()+"/watch/"+encode(p[1]));
                ProviderRepository.Stream stream=new ProviderRepository.Stream(s.optString("label","Auto"),url,playbackHeaders);
                JSONArray tracks=item.optJSONArray("tracks");if(tracks!=null)for(int k=0;k<tracks.length();k++){JSONObject track=tracks.getJSONObject(k);if(track.optString("kind").equals("captions")){String subtitle=ProviderParser.absolute(base()+"/",track.optString("file"));if(Library.https(subtitle))stream.subtitles.put(new JSONObject().put("url",subtitle).put("lang",track.optString("label","und")));}}
                out.add(stream);
            }
        }
        if(out.isEmpty())throw new IOException("NetMirror returned no playable sources for this title or session.");return out;
    }
    List<Library.Title> episodes(Library.Title t,int season)throws Exception{
        String[] p=parts(t);JSONArray items;
        if(season<0)items=t.videos;else{JSONObject response=object("/"+prefix(p[0])+"episodes.php?s="+season+"&series="+encode(p[1]),p[0]);items=response.optJSONArray("episodes");}
        List<Library.Title> out=new ArrayList<>();if(items==null)return out;
        for(int i=0;i<items.length();i++){JSONObject e=items.optJSONObject(i);if(e==null)continue;
            Library.Title episode=title(p[0],e.optString("id"),e.optString("t",e.optString("title","Episode "+(i+1))));episode.description=e.optString("d",t.description);episode.type="episode";episode.poster=t.poster;episode.background=t.background;out.add(episode);
        }return out;
    }
}
