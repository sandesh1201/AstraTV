package com.astratv.app;

import org.json.*;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Official YouTube Data API v3 lookup for trailer candidates. Release builds call the Saandio
 * Catalog API ({@code /api/trailer}), which holds the YouTube key server-side; debug builds may use
 * a developer key directly. Both return YouTube {@code videos.list} items (snippet, contentDetails,
 * status), so embeddability, privacy, region blocks and duration are known before anything plays.
 */
final class YoutubeTrailerSearch {
    static final int MAX_RESULTS=8;
    private static final String API="https://www.googleapis.com/youtube/v3";
    private static final Pattern DURATION=Pattern.compile("P(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?");
    private YoutubeTrailerSearch(){}

    /** True when trailers can be searched: the catalog service is set, or (debug only) a developer key exists. */
    static boolean configured(android.content.Context c){
        return backendUrl(BuildConfig.CATALOG_API_BASE,"q")!=null||!ApiKeys.youtube(c).isEmpty();
    }
    /** Backend route; null when the build has no HTTPS catalog service. */
    static String backendUrl(String catalogApiBase,String query){
        String b=catalogApiBase==null?"":catalogApiBase.trim();while(b.endsWith("/"))b=b.substring(0,b.length()-1);
        return Library.https(b)?b+"/api/trailer?q="+enc(query):null;
    }
    static String searchUrl(String query,String key){
        return API+"/search?part=snippet&type=video&videoEmbeddable=true&videoSyndicated=true&maxResults="+MAX_RESULTS+"&q="+enc(query)+"&key="+enc(key);
    }
    static String videosUrl(List<String> ids,String key){
        return API+"/videos?part=snippet,contentDetails,status&id="+enc(String.join(",",ids))+"&key="+enc(key);
    }
    static List<String> searchIds(JSONObject search){
        List<String> out=new ArrayList<>();JSONArray items=search==null?null:search.optJSONArray("items");if(items==null)return out;
        for(int i=0;i<items.length();i++){JSONObject o=items.optJSONObject(i);JSONObject id=o==null?null:o.optJSONObject("id");String v=id==null?"":id.optString("videoId");if(v.matches("[A-Za-z0-9_-]{11}"))out.add(v);}
        return out;
    }
    /** Parses {@code videos.list} items. A video blocked in {@code country} is treated as not embeddable. */
    static List<TrailerMatcher.Candidate> candidates(JSONObject videos,String country){
        List<TrailerMatcher.Candidate> out=new ArrayList<>();JSONArray items=videos==null?null:videos.optJSONArray("items");if(items==null)return out;
        String cc=country==null?"":country.toUpperCase(Locale.ROOT);
        for(int i=0;i<items.length();i++){
            JSONObject o=items.optJSONObject(i);if(o==null)continue;
            JSONObject sn=o.optJSONObject("snippet"),cd=o.optJSONObject("contentDetails"),st=o.optJSONObject("status");
            if(sn==null||st==null)continue;
            boolean embeddable=st.optBoolean("embeddable",false)&&!blocked(cd,cc);
            out.add(new TrailerMatcher.Candidate(o.optString("id"),sn.optString("title"),sn.optString("channelTitle"),sn.optString("description"),
                seconds(cd==null?"":cd.optString("duration")),embeddable,"public".equals(st.optString("privacyStatus")),!"none".equals(sn.optString("liveBroadcastContent","none"))));
        }
        return out;
    }
    static boolean blocked(JSONObject contentDetails,String country){
        if(contentDetails==null||country.isEmpty())return false;JSONObject r=contentDetails.optJSONObject("regionRestriction");if(r==null)return false;
        JSONArray blocked=r.optJSONArray("blocked"),allowed=r.optJSONArray("allowed");
        if(blocked!=null)for(int i=0;i<blocked.length();i++)if(country.equalsIgnoreCase(blocked.optString(i)))return true;
        if(allowed!=null){for(int i=0;i<allowed.length();i++)if(country.equalsIgnoreCase(allowed.optString(i)))return false;return true;}
        return false;
    }
    /** ISO-8601 duration (PT2M31S) in seconds; 0 when absent or unparseable. */
    static int seconds(String iso){
        if(iso==null||iso.isEmpty())return 0;Matcher m=DURATION.matcher(iso);if(!m.matches())return 0;
        long d=num(m.group(1)),h=num(m.group(2)),min=num(m.group(3)),s=num(m.group(4));
        return (int)Math.min(Integer.MAX_VALUE,((d*24+h)*60+min)*60+s);
    }
    private static long num(String g){return g==null?0:Long.parseLong(g);}
    private static String enc(String s){try{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}catch(java.io.UnsupportedEncodingException e){return "";}}
}
