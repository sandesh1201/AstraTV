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
        public final int total,page;public final boolean hasMore;
        Catalog(List<Library.Title> titles,String status,boolean stale){this(titles,status,stale,titles.size(),1,titles.size()>=18);}
        Catalog(List<Library.Title> titles,String status,boolean stale,int total,int page,boolean hasMore){this.titles=titles;this.status=status;this.stale=stale;this.total=total;this.page=page;this.hasMore=hasMore;}
    }
    public static final class Stream {
        public final String name,url;public final Map<String,String> headers;
        public final JSONArray subtitles=new JSONArray();
        public String mimeType="";
        /** Declared by authorized providers; defaults mean "not stated". expiresAt 0 = read from the signed URL, if any. */
        public int height;public boolean available=true;public long expiresAt;
        public String drmScheme="",drmLicenseUrl="";
        public final List<String> audio=new ArrayList<>();public final Set<String> regions=new HashSet<>();
        Stream(String name,String url,Map<String,String> headers){this.name=name;this.url=url;this.headers=headers;}
    }
    public final ExecutorService io=Executors.newFixedThreadPool(4);
    private final SharedPreferences prefs;
    private final File cache;
    private final NetMirrorProvider netmirror;
    private final IndianTvProvider tv=new IndianTvProvider();
    public ProviderRepository(Context c){prefs=c.getSharedPreferences("astra",0);cache=new File(c.getCacheDir(),"catalogs-v3");cache.mkdirs();netmirror=new NetMirrorProvider(prefs);}
    public String language(String id){return prefs.getString("browse.language."+id,id.equals("movierulz")?"Telugu + Hindi":id.equals("news")?"Telugu":"All");}
    public String genre(String id){return prefs.getString("browse.genre."+id,"All");}
    public String endpoint(String id){return prefs.getString("provider."+id,"");}
    public boolean enabled(String id){return prefs.getBoolean("addon."+id,true);}
    public void configure(String id,String manifest)throws IOException{
        if(!manifest.isEmpty()&&(!Library.https(manifest)||!manifest.endsWith("/manifest.json")))throw new IOException("Enter an HTTPS URL ending in /manifest.json");
        prefs.edit().putString("provider."+id,manifest).apply();
        File f=new File(cache,id+".json");if(f.exists())f.delete();
    }
    public Catalog load(Library.Addon addon,String search,int page)throws Exception{
        requireAuthorized(addon.id);requireEnabled(addon.id);
        List<Library.Title> titles;
        try{
            Catalog catalog=null;
            if(!endpoint(addon.id).isEmpty())titles=manifestCatalog(addon,search,page);
            else if(Library.netmirror(addon.id))titles=netmirror.catalog(search,addon.id);
            else if(addon.id.equals("news")){catalog=tv.catalog(search,page,search.isEmpty()?language(addon.id):"All",search.isEmpty()?genre(addon.id):"All");titles=catalog.titles;}
            else{
                String url=addon.url+(page>1?"page/"+page+"/":"")+(search.isEmpty()?"":"?s="+encode(search));
                Response r=request(url,"");titles=ProviderParser.catalog(addon,r.body,r.url);
                if(titles.isEmpty())throw new IOException("Provider returned no catalog items. Its format or availability may have changed.");
                if(addon.id.equals("tamil")||addon.id.equals("telugu"))titles=SeriesIndex.group(titles);
            }
            if(catalog==null)titles=CatalogSort.newestFirst(titles);
            Catalog result=catalog==null?new Catalog(titles,titles.size()+" titles",false):catalog;
            if(search.isEmpty()&&page==1)saveCatalog(addon.id,result);
            return result;
        }catch(Exception e){
            if(search.isEmpty()&&page==1){Catalog saved=readCached(addon.id);if(saved!=null)saved.titles.removeIf(SunNetworkChannels::rightsAsserted);if(saved!=null&&!saved.titles.isEmpty())return new Catalog(saved.titles,"Saved catalog · "+message(e),true,saved.total,1,saved.hasMore);}
            throw e;
        }
    }
    public List<Library.Title> cached(String id){
        if(unauthorized(id))return new ArrayList<>();
        List<Library.Title> out=new ArrayList<>(readCatalog(id));out.removeIf(SunNetworkChannels::rightsAsserted);return out;
    }
    /** Sources with no authorized catalog or playback access. Their saved lists are not shown and their links are not played. */
    static boolean unauthorized(String providerId){return !unauthorizedReason(providerId).isEmpty();}
    static String unauthorizedReason(String providerId){
        if(MovieRulzCatalogProvider.ORIGIN.equals(providerId))return MovieRulzCatalogProvider.REASON;
        if(TamilTvSerialProvider.LEGACY_ADDON.equals(providerId))return TamilTvSerialProvider.REASON;
        if(TeluguStreamProvider.LEGACY_ADDON.equals(providerId))return TeluguStreamProvider.REASON;
        return "";
    }
    private static void requireAuthorized(String providerId)throws IOException{if(unauthorized(providerId))throw new IOException(unauthorizedReason(providerId));}
    public Library.Title details(Library.Title t)throws Exception{
        if(!enabled(t.provider)||unauthorized(t.provider))return t;
        String manifest=endpoint(t.provider);
        if(!manifest.isEmpty()){
            JSONObject data=new JSONObject(request(base(manifest)+"/meta/"+encode(t.type)+"/"+encode(t.id)+".json","").body);
            if(data.optJSONObject("meta")!=null){Library.Title full=ProviderParser.meta(data.getJSONObject("meta"),t.provider,t.providerName);if(full.poster.isEmpty())full.poster=t.poster;return full;}
        }else if(Library.netmirror(t.provider))return netmirror.details(t);
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
        requireAuthorized(t.provider);requireEnabled(t.provider);
        if(t.provider.equals("news")&&t.videos.length()>0){List<Stream> out=new ArrayList<>();for(int i=0;i<t.videos.length();i++){JSONObject v=t.videos.getJSONObject(i);if(Library.https(v.optString("url")))out.add(new Stream(v.optString("name","Source"),v.getString("url"),PlaylistParser.safeHeaders(v.optJSONObject("headers"))));}if(!out.isEmpty())return out;}
        if(Library.https(t.url))return Collections.singletonList(new Stream("Direct playback",t.url,PlaylistParser.safeHeaders(t.headers)));
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
        if(Library.netmirror(t.provider))return netmirror.streams(t);
        if(t.pageUrl.isEmpty())throw new IOException("No native stream has been supplied for this title.");
        List<Stream> out=new ArrayList<>();Set<String> seen=new HashSet<>();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
        resolvePage(t.pageUrl,"",0,seen,out,deadline);
        if(out.isEmpty())throw new IOException("The host did not expose a supported direct video stream. This source may require an interactive browser, authentication or a host-specific adapter. No website will be opened.");
        return out;
    }
    /** A licensed partner's playback descriptor as a stream the player understands. */
    static Stream stream(LicensedProvider.Playback p){
        Stream s=new Stream(p.quality.isEmpty()?"Source · "+p.protocol.toUpperCase(Locale.ROOT):p.quality+" · "+p.protocol.toUpperCase(Locale.ROOT),p.url,Collections.emptyMap());
        s.height=p.maxHeight;s.audio.addAll(p.audioLanguages);s.drmScheme=p.drm;
        s.mimeType=p.protocol.equalsIgnoreCase("hls")?"application/x-mpegURL":p.protocol.equalsIgnoreCase("dash")?"application/dash+xml":p.protocol.equalsIgnoreCase("mp4")?"video/mp4":"";
        return s;
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
    public List<Library.Title> episodes(Library.Title t,int season)throws Exception{
        return episodes(t,season,1);
    }
    public List<Library.Title> episodes(Library.Title t,int season,int page)throws Exception{
        requireAuthorized(t.provider);requireEnabled(t.provider);
        if(Library.netmirror(t.provider)&&endpoint(t.provider).isEmpty())return netmirror.episodes(t,season);
        List<Library.Title> out=new ArrayList<>();
        if((t.provider.equals("tamil")||t.provider.equals("telugu"))&&!t.seriesUrl.isEmpty()){
            Library.Addon addon=null;for(Library.Addon a:Library.addons())if(a.id.equals(t.provider))addon=a;
            String url=addon.url+"?s="+encode(t.name)+(page>1?"&paged="+page:"");Response r=request(url,"");for(Library.Title post:ProviderParser.catalog(addon,r.body,r.url))if(SeriesIndex.normalized(SeriesIndex.nameOf(post.name)).equals(SeriesIndex.normalized(t.name))&&(season<0||post.season==season)){post.type="episode";post.name=post.episode>0?"Episode "+post.episode+(post.released.isEmpty()?"":" · "+post.released):post.released;out.add(post);}
            if(!out.isEmpty())return out;
        }
        if(page>1)return out;
        for(int i=0;i<t.videos.length();i++){JSONObject video=t.videos.optJSONObject(i);if(video==null)continue;Library.Title episode=video.has("pageUrl")?Library.Title.from(video):Library.Title.from(t.json());episode.id=video.getString("id");episode.name=video.optString("title",video.optString("name","Episode "+(i+1)));if(!video.has("pageUrl"))episode.type=t.type;episode.videos=new JSONArray();episode.seasons=new JSONArray();out.add(episode);}return out;
    }
    private void requireEnabled(String id)throws IOException{if(!enabled(id))throw new IOException("This provider is disabled. Enable it in Add-ons to use its sources.");}
    private File cacheFile(String id){String sessionScope=Library.netmirror(id)?prefs.getString("netmirror.api","")+"|"+prefs.getInt("netmirror.session.revision",0):"";return new File(cache,id+"-"+PlaylistParser.stable(language(id)+"|"+genre(id)+"|"+endpoint(id)+"|"+sessionScope)+".json");}
    private void saveCatalog(String id,Catalog catalog){try{JSONArray a=new JSONArray();for(Library.Title t:catalog.titles)a.put(t.json());JSONObject data=new JSONObject().put("items",a).put("total",catalog.total).put("hasMore",catalog.hasMore).put("savedAt",System.currentTimeMillis());try(Writer w=new OutputStreamWriter(new FileOutputStream(cacheFile(id)),StandardCharsets.UTF_8)){w.write(data.toString());}}catch(Exception ignored){}}
    private Catalog readCached(String id){try{File f=cacheFile(id);if(!f.exists())return null;try(InputStream in=new FileInputStream(f)){JSONObject data=new JSONObject(read(in,4*1024*1024));JSONArray a=data.getJSONArray("items");List<Library.Title> titles=new ArrayList<>();for(int i=0;i<a.length();i++)titles.add(Library.Title.from(a.getJSONObject(i)));return new Catalog(titles,"Saved catalog",true,data.optInt("total",titles.size()),1,data.optBoolean("hasMore"));}}catch(Exception ignored){return null;}}
    private List<Library.Title> readCatalog(String id){Catalog c=readCached(id);return c==null?new ArrayList<>():c.titles;}
    private static String base(String manifest){return manifest.substring(0,manifest.length()-"/manifest.json".length());}
    private static String encode(String s){try{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}catch(UnsupportedEncodingException e){throw new IllegalStateException(e);}}
    public static String message(Exception e){
        if(e instanceof java.net.SocketTimeoutException)return "The source did not respond in time. Try again.";
        if(e instanceof java.net.UnknownHostException)return "The source host could not be reached.";
        if(e instanceof javax.net.ssl.SSLException)return "A secure connection to this source could not be established.";
        if(e instanceof org.json.JSONException)return "The source returned an unsupported data format.";
        String m=e.getMessage();if(m!=null)for(String prefix:new String[]{"Source ","Provider ","NetMirror ","No ","The host ","This provider ","This manifest ","Only HTTPS ","Enter an HTTPS ","The device ","Too many ","Each video ","Maximum ","Catalog must ","This title ","The partner ","The provider ","The playback ","This stream ","This TV ","This link ","This source ","Not available. ","Authorization required. "})if(m.startsWith(prefix))return m;
        return "This source is unavailable. Check your connection and provider settings.";
    }
    public static final class Response {public String body,url;public boolean media;Response(String body,String url,boolean media){this.body=body;this.url=url;this.media=media;}}
    public static Response request(String address,String referer)throws IOException{
        return request(address,referer,Collections.emptyMap());
    }
    public static String requestText(String address)throws IOException{return request(address,"",Collections.emptyMap(),true).body;}
    public static Response request(String address,String referer,Map<String,String> headers)throws IOException{
        return request(address,referer,headers,false);
    }
    private static Response request(String address,String referer,Map<String,String> headers,boolean readPlaylist)throws IOException{
        String current=address;
        for(int redirect=0;redirect<5;redirect++){
            if(!Library.https(current))throw new IOException("Only HTTPS providers are supported");
            if(ProviderRiskScanner.privateHost(new URL(current).getHost()))throw new IOException("Provider pointed at a local network address, which is blocked");
            HttpURLConnection c=(HttpURLConnection)new URL(current).openConnection();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(8000);c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent","AstraTV/0.3 (Android TV; native media client)");c.setRequestProperty("Accept","application/json,text/html,*/*");
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
