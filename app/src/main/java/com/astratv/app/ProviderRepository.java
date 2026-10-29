package com.astratv.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Catalog -> metadata -> playable streams. There is deliberately no browser fallback. */
public final class ProviderRepository {
    public static final class Catalog {
        public final List<Library.Title> titles;public final String status;public final boolean stale;
        Catalog(List<Library.Title> titles,String status,boolean stale){this.titles=titles;this.status=status;this.stale=stale;}
    }
    public static final class Stream {
        public final String name,url;public final Map<String,String> headers;
        public final JSONArray subtitles=new JSONArray();
        Stream(String name,String url,Map<String,String> headers){this.name=name;this.url=url;this.headers=headers;}
    }
    public final ExecutorService io=Executors.newFixedThreadPool(4);
    private final SharedPreferences prefs;
    private final File cache;
    private final NetMirrorProvider netmirror;
    public ProviderRepository(Context c){prefs=c.getSharedPreferences("astra",0);cache=new File(c.getCacheDir(),"catalogs");cache.mkdirs();netmirror=new NetMirrorProvider(prefs);}
    public String endpoint(String id){return prefs.getString("provider."+id,"");}
    public boolean enabled(String id){return prefs.getBoolean("addon."+id,true);}
    public void configure(String id,String manifest)throws IOException{
        if(!manifest.isEmpty()&&(!Library.https(manifest)||!manifest.endsWith("/manifest.json")))throw new IOException("Enter an HTTPS URL ending in /manifest.json");
        prefs.edit().putString("provider."+id,manifest).apply();
        File f=new File(cache,id+".json");if(f.exists())f.delete();
    }
    public Catalog load(Library.Addon addon,String search,int page)throws Exception{
        List<Library.Title> titles;
        try{
            if(!endpoint(addon.id).isEmpty())titles=manifestCatalog(addon,search,page);
            else if(addon.id.equals("netmirror"))titles=netmirror.catalog(search);
            else if(addon.id.equals("news"))titles=news();
            else{
                String url=addon.url+(page>1?"page/"+page+"/":"")+(search.isEmpty()?"":"?s="+encode(search));
                Response r=request(url,"");titles=ProviderParser.catalog(addon,r.body,r.url);
                if(titles.isEmpty())throw new IOException("Provider returned no catalog items. Its format or availability may have changed.");
            }
            if(search.isEmpty()&&page==1)saveCatalog(addon.id,titles);
            return new Catalog(titles,titles.size()+" titles",false);
        }catch(Exception e){
            if(search.isEmpty()&&page==1){List<Library.Title> saved=readCatalog(addon.id);if(!saved.isEmpty())return new Catalog(saved,"Saved catalog · "+message(e),true);}
            throw e;
        }
    }
    public List<Library.Title> cached(String id){return readCatalog(id);}
    public Library.Title details(Library.Title t)throws Exception{
        String manifest=endpoint(t.provider);
        if(!manifest.isEmpty()){
            JSONObject data=new JSONObject(request(base(manifest)+"/meta/"+encode(t.type)+"/"+encode(t.id)+".json","").body);
            if(data.optJSONObject("meta")!=null){Library.Title full=ProviderParser.meta(data.getJSONObject("meta"),t.provider,t.providerName);if(full.poster.isEmpty())full.poster=t.poster;return full;}
        }else if(t.provider.equals("netmirror"))return netmirror.details(t);
        else if(!t.pageUrl.isEmpty()){Response r=request(t.pageUrl,"");return ProviderParser.details(t,r.body,r.url);}
        return t;
    }
    private List<Library.Title> manifestCatalog(Library.Addon addon,String search,int page)throws Exception{
        String endpoint=endpoint(addon.id);JSONObject manifest=new JSONObject(request(endpoint,"").body);
        JSONArray catalogs=manifest.optJSONArray("catalogs");List<Library.Title> out=new ArrayList<>();
        if(catalogs==null)throw new IOException("This manifest does not provide a catalog");
        for(int i=0;i<Math.min(catalogs.length(),4);i++){
            JSONObject cat=catalogs.getJSONObject(i);boolean supportsSearch=false,supportsSkip=false,requiredOther=false;
            JSONArray extras=cat.optJSONArray("extra");if(extras!=null)for(int j=0;j<extras.length();j++){
                Object extra=extras.get(j);String name=extra instanceof JSONObject?((JSONObject)extra).optString("name"):extra.toString();
                if(name.equals("search"))supportsSearch=true;else if(name.equals("skip"))supportsSkip=true;else if(extra instanceof JSONObject&&((JSONObject)extra).optBoolean("isRequired"))requiredOther=true;
            }
            if(requiredOther||(!search.isEmpty()&&!supportsSearch)||(page>1&&!supportsSkip))continue;
            String path=base(endpoint)+"/catalog/"+encode(cat.getString("type"))+"/"+encode(cat.getString("id"));
            List<String> args=new ArrayList<>();if(!search.isEmpty())args.add("search="+encode(search));if(page>1)args.add("skip="+((page-1)*100));
            if(!args.isEmpty())path+="/"+String.join("&",args);
            JSONObject response=new JSONObject(request(path+".json","").body);JSONArray metas=response.optJSONArray("metas");
            if(metas!=null)for(int j=0;j<Math.min(metas.length(),100);j++)out.add(ProviderParser.meta(metas.getJSONObject(j),addon.id,manifest.optString("name",addon.name)));
        }
        return out;
    }
    public List<Stream> streams(Library.Title t)throws Exception{
        if(t.provider.equals("news")&&t.videos.length()>0){List<Stream> out=new ArrayList<>();for(int i=0;i<t.videos.length();i++){JSONObject v=t.videos.getJSONObject(i);if(Library.https(v.optString("url")))out.add(new Stream(v.optString("name","Live source"),v.getString("url"),Collections.emptyMap()));}if(!out.isEmpty())return out;}
        if(Library.https(t.url))return Collections.singletonList(new Stream("Direct playback",t.url,Collections.emptyMap()));
        String manifest=endpoint(t.provider);
        if(!manifest.isEmpty()){
            JSONArray streams=new JSONObject(request(base(manifest)+"/stream/"+encode(t.type)+"/"+encode(t.id)+".json","").body).optJSONArray("streams");
            List<Stream> out=new ArrayList<>();if(streams!=null)for(int i=0;i<streams.length();i++){
                JSONObject item=streams.getJSONObject(i);String url=item.optString("url");if(!Library.https(url))continue;
                Map<String,String> headers=new HashMap<>();JSONObject hints=item.optJSONObject("behaviorHints");JSONObject proxy=hints==null?null:hints.optJSONObject("proxyHeaders");JSONObject req=proxy==null?null:proxy.optJSONObject("request");
                if(req!=null){Iterator<String> keys=req.keys();while(keys.hasNext()){String key=keys.next();if(key.equalsIgnoreCase("Referer")||key.equalsIgnoreCase("Origin")||key.equalsIgnoreCase("User-Agent"))headers.put(key,req.getString(key));}}
                out.add(new Stream(item.optString("name","Source "+(i+1))+" "+item.optString("title",""),url,headers));
            }
            if(out.isEmpty())throw new IOException("This provider returned no direct HTTPS streams. Browser links, torrents and DRM-only sources cannot be played by this integration.");return out;
        }
        if(t.provider.equals("netmirror"))return netmirror.streams(t);
        if(t.pageUrl.isEmpty())throw new IOException("No native stream has been supplied for this title.");
        List<Stream> out=new ArrayList<>();Set<String> seen=new HashSet<>();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
        resolvePage(t.pageUrl,"",0,seen,out,deadline);
        if(out.isEmpty())throw new IOException("The host did not expose a supported direct video stream. This source may require an interactive browser, authentication or a host-specific adapter. No website will be opened.");
        return out;
    }
    private void resolvePage(String url,String referer,int depth,Set<String> seen,List<Stream> out,long deadline)throws Exception{
        if(depth>2||seen.size()>=7||System.nanoTime()>deadline||!seen.add(url))return;
        Response r=request(url,referer);
        Map<String,String> headers=new HashMap<>();if(!referer.isEmpty())headers.put("Referer",referer);
        if(r.media){out.add(new Stream("Source "+(out.size()+1),r.url,headers));return;}
        for(String direct:ProviderParser.mediaUrls(r.body,r.url)){
            Map<String,String> h=new HashMap<>();h.put("Referer",r.url);out.add(new Stream("Source "+(out.size()+1),direct,h));
        }
        if(!out.isEmpty())return;
        for(String embed:ProviderParser.embeds(r.body,r.url)){
            if(System.nanoTime()>deadline)break;
            try{resolvePage(embed,r.url,depth+1,seen,out,deadline);}catch(IOException ignored){/* Try the next explicit video source. */}
            if(!out.isEmpty())break;
        }
    }
    private static List<Library.Title> news()throws Exception{
        List<Library.Title> channels=ProviderParser.newsPlaylist(request("https://iptv-org.github.io/iptv/languages/tel.m3u","",Collections.emptyMap(),true).body);
        if(channels.isEmpty())throw new IOException("No HTTPS Telugu news streams were returned by the directory.");return channels;
    }
    public List<Library.Title> episodes(Library.Title t,int season)throws Exception{
        if(t.provider.equals("netmirror")&&endpoint(t.provider).isEmpty())return netmirror.episodes(t,season);
        List<Library.Title> out=new ArrayList<>();for(int i=0;i<t.videos.length();i++){JSONObject video=t.videos.optJSONObject(i);if(video==null)continue;Library.Title episode=Library.Title.from(t.json());episode.id=video.getString("id");episode.name=video.optString("title",video.optString("name","Episode "+(i+1)));episode.type=t.type;episode.videos=new JSONArray();episode.seasons=new JSONArray();out.add(episode);}return out;
    }
    private void saveCatalog(String id,List<Library.Title> titles){try{JSONArray a=new JSONArray();for(Library.Title t:titles)a.put(t.json());try(Writer w=new OutputStreamWriter(new FileOutputStream(new File(cache,id+".json")),StandardCharsets.UTF_8)){w.write(a.toString());}}catch(Exception ignored){}}
    private List<Library.Title> readCatalog(String id){List<Library.Title> out=new ArrayList<>();try{File f=new File(cache,id+".json");if(!f.exists())return out;try(InputStream in=new FileInputStream(f)){JSONArray a=new JSONArray(read(in,4*1024*1024));for(int i=0;i<a.length();i++)out.add(Library.Title.from(a.getJSONObject(i)));}}catch(Exception ignored){}return out;}
    private static String base(String manifest){return manifest.substring(0,manifest.length()-"/manifest.json".length());}
    private static String encode(String s){try{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}catch(UnsupportedEncodingException e){throw new IllegalStateException(e);}}
    public static String message(Exception e){String m=e.getMessage();return m==null?"Provider unavailable":m;}
    public static final class Response {public String body,url;public boolean media;Response(String body,String url,boolean media){this.body=body;this.url=url;this.media=media;}}
    public static Response request(String address,String referer)throws IOException{
        return request(address,referer,Collections.emptyMap());
    }
    public static Response request(String address,String referer,Map<String,String> headers)throws IOException{
        return request(address,referer,headers,false);
    }
    private static Response request(String address,String referer,Map<String,String> headers,boolean readPlaylist)throws IOException{
        String current=address;
        for(int redirect=0;redirect<5;redirect++){
            if(!Library.https(current))throw new IOException("Only HTTPS providers are supported");
            HttpURLConnection c=(HttpURLConnection)new URL(current).openConnection();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(8000);c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent","AstraTV/0.2 (Android TV; native media client)");c.setRequestProperty("Accept","application/json,text/html,*/*");
            if(!referer.isEmpty())c.setRequestProperty("Referer",referer);
            for(Map.Entry<String,String> h:headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());
            try{
                int status=c.getResponseCode();
                if(status>=300&&status<400){String location=c.getHeaderField("Location");if(location==null)throw new IOException("Provider sent an invalid redirect");String next=ProviderParser.absolute(current,location);if(!new URL(current).getHost().equalsIgnoreCase(new URL(next).getHost()))headers=Collections.emptyMap();current=next;continue;}
                if(status<200||status>=300)throw new IOException("Provider returned HTTP "+status);
                String type=c.getContentType();boolean media=type!=null&&(type.startsWith("video/")||type.contains("mpegurl")||type.contains("dash+xml"));
                if(media&&!readPlaylist)return new Response("",current,true);
                try(InputStream in=c.getInputStream()){return new Response(read(in,4*1024*1024),current,false);}
            }finally{c.disconnect();}
        }throw new IOException("Too many provider redirects");
    }
    private static String read(InputStream in,int limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))!=-1){if(out.size()+n>limit)throw new IOException("Provider response is too large");out.write(buf,0,n);}return out.toString(StandardCharsets.UTF_8.name());
    }
}
