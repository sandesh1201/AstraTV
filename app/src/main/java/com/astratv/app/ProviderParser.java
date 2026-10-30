package com.astratv.app;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.net.URI;
import java.util.*;
import java.util.regex.*;

/** Pure parsing code. No WebView or JavaScript execution. */
public final class ProviderParser {
    private ProviderParser(){}
    public static List<Library.Title> catalog(Library.Addon addon,String html,String base){
        Document doc=Jsoup.parse(html,base);List<Library.Title> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(Element block:doc.select("article, .boxed.film")){
            Element link=block.selectFirst("a[href]:has(img)");if(link==null)continue;Element image=link.selectFirst("img");String url=link.absUrl("href");
            if(!Library.https(url)||!seen.add(url))continue;Element heading=block.selectFirst("h2,h3,h4");
            String name=link.attr("title");if(name.isEmpty()&&heading!=null)name=heading.text();if(name.isEmpty())name=image.attr("alt");if(name.isEmpty())continue;
            Library.Title t=new Library.Title(Integer.toHexString(url.hashCode()),name,"","","","",addon.id.equals("movierulz")?"Movie":"Episode");
            t.provider=addon.id;t.providerName=addon.description;t.pageUrl=url;t.type=addon.id.equals("movierulz")?"movie":"series";
            t.poster=image.absUrl(image.hasAttr("data-src")?"data-src":"src");t.background=t.poster;
            Matcher year=Pattern.compile("\\b(19|20)\\d{2}\\b").matcher(name);if(year.find())t.year=year.group();
            String clean=name.replaceAll("(?i)\\s*(HDRip|DVDScr|WEBRip|WEB-DL|BRRip|BluRay|HDTS)\\b.*","").replaceAll("\\s*\\((19|20)\\d{2}\\)","");
            if(!addon.id.equals("movierulz")){clean=clean.replaceAll("\\s*[-–]?\\s*\\d{2}-\\d{2}-\\d{4}.*","");Element badge=block.selectFirst(".skytamil-episode-badge");if(badge!=null)t.genre=badge.text();}
            if(!clean.trim().isEmpty())t.name=clean.trim();
            Matcher date=Pattern.compile("(\\d{2})-(\\d{2})-((?:19|20)\\d{2})").matcher(name);if(date.find())t.released=date.group(3)+"-"+date.group(2)+"-"+date.group(1);
            Matcher episode=Pattern.compile("(?i)episode\\s*(\\d+)").matcher(name);if(episode.find())t.episode=number(episode.group(1),0);
            Matcher season=Pattern.compile("(?i)season\\s*(\\d+)").matcher(name);if(season.find())t.season=number(season.group(1),1);
            t.language=addon.id.equals("telugu")?"Telugu":addon.id.equals("tamil")?"Tamil":name.toLowerCase(Locale.ROOT).contains("hindi")?"Hindi":name.toLowerCase(Locale.ROOT).contains("telugu")?"Telugu":"";
            for(String language:new String[]{"Malayalam","Kannada","Telugu","Hindi","Tamil"})if(name.toLowerCase(Locale.ROOT).contains(language.toLowerCase(Locale.ROOT))){t.language=language;break;}
            if(addon.id.equals("tamil")||addon.id.equals("telugu"))t.seriesUrl=ProviderParser.absolute(base,"/?s="+encode(t.name));
            result.add(t);if(result.size()==60)break;
        }return result;
    }
    public static Library.Title details(Library.Title t,String html,String base){
        Document doc=Jsoup.parse(html,base);Element description=doc.selectFirst(".synopsis-section p, .st-episode-blurb");
        if(description!=null)t.description=description.text();else{Element og=doc.selectFirst("meta[property=og:description],meta[name=description]");if(og!=null)t.description=og.attr("content");}
        List<String> actors=new ArrayList<>();for(Element e:doc.select(".movie-info-block a[href*=/actor/], .movie-info-block a[href*=/director/]"))actors.add(e.text());if(!actors.isEmpty())t.cast=String.join(" · ",actors);
        List<String> genres=new ArrayList<>();for(Element e:doc.select(".movie-info-block a[href*=/genre/]"))genres.add(e.text());if(!genres.isEmpty())t.genre=String.join(" · ",genres);
        if(t.description.length()>1500)t.description=t.description.substring(0,1500);return t;
    }
    public static boolean media(String url){return Library.https(url)&&url.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|m3u8|mpd|webm|mkv)([?#].*)?");}
    public static String absolute(String base,String value){try{return new URI(base).resolve(value.replace("\\/","/").replace("&amp;","&")).toString();}catch(Exception e){return "";}}
    public static List<String> mediaUrls(String html,String base){
        Document doc=Jsoup.parse(html,base);Set<String> urls=new LinkedHashSet<>();
        for(Element el:doc.select("video[src],video source[src],a[href]")){String u=el.absUrl(el.hasAttr("src")?"src":"href");if(media(u))urls.add(u);}
        StringBuilder scripts=new StringBuilder();for(Element script:doc.select("script"))scripts.append(script.data()).append('\n');
        Matcher m=Pattern.compile("(?i)(?:file|contentUrl|hls|dash|src)\\s*[\"']?\\s*[:=]\\s*[\"']([^\"']+)[\"']").matcher(scripts);
        while(m.find()){String u=absolute(base,m.group(1));if(media(u))urls.add(u);}return new ArrayList<>(urls);
    }
    public static List<String> embeds(String html,String base){
        Document doc=Jsoup.parse(html,base);Set<String> urls=new LinkedHashSet<>();
        for(Element el:doc.select("iframe[src],.st-video-poster[href],a.skytamil-source-link[href]")){String u=el.absUrl(el.hasAttr("src")?"src":"href");if(Library.https(u))urls.add(u);}
        Matcher m=Pattern.compile("var\\s+locations\\s*=\\s*(\\[[^;]+])").matcher(html);
        if(m.find())try{JSONArray a=new JSONArray(m.group(1));for(int i=0;i<a.length();i++){String u=absolute(base,a.getString(i));if(Library.https(u))urls.add(u);}}catch(JSONException ignored){}
        return new ArrayList<>(urls);
    }
    public static Library.Title meta(JSONObject o,String provider,String providerName)throws JSONException{
        Library.Title t=Library.Title.from(o);t.provider=provider;t.providerName=providerName;t.name=o.optString("name",o.optString("title"));t.rating=o.optString("imdbRating");
        if(o.optJSONArray("genres")!=null)t.genre=join(o.getJSONArray("genres"));if(o.optJSONArray("cast")!=null)t.cast=join(o.getJSONArray("cast"));return t;
    }
    private static String join(JSONArray array)throws JSONException{List<String> v=new ArrayList<>();for(int i=0;i<array.length();i++)v.add(array.getString(i));return String.join(" · ",v);}
    private static String encode(String text){try{return java.net.URLEncoder.encode(text,"UTF-8");}catch(java.io.UnsupportedEncodingException e){throw new IllegalStateException(e);}}
    private static int number(String text,int fallback){try{return Math.min(1000000,Integer.parseInt(text));}catch(NumberFormatException e){return fallback;}}
    public static List<Library.Title> newsPlaylist(String playlist)throws JSONException{
        Map<String,Library.Title> channels=new LinkedHashMap<>();String info="";
        for(String line:playlist.split("\\r?\\n")){
            line=line.trim();if(line.startsWith("#EXTINF:")){info=line;continue;}
            if(line.startsWith("#")||line.isEmpty())continue;
            if(!Library.https(line)||!info.matches(".*group-title=\"[^\"]*News[^\"]*\".*")){info="";continue;}
            int comma=info.lastIndexOf(',');if(comma<0){info="";continue;}String name=info.substring(comma+1).trim();
            Matcher idMatch=Pattern.compile("tvg-id=\"([^\"]+)\"").matcher(info);String id=idMatch.find()?idMatch.group(1):name;
            Library.Title t=channels.get(id);if(t==null){t=new Library.Title(id,name.replaceAll("\\s*\\([^)]*\\)",""),"Live Telugu news from the public IPTV-org directory. Stream availability depends on the broadcaster and your region.",line,"","LIVE","Telugu · News");t.provider="news";t.providerName="Telugu news";t.type="tv";
                Matcher logo=Pattern.compile("tvg-logo=\"([^\"]+)\"").matcher(info);if(logo.find())t.poster=logo.group(1);t.background=t.poster;channels.put(id,t);}
            t.videos.put(new JSONObject().put("url",line).put("name",name));info="";
        }return new ArrayList<>(channels.values());
    }
}
