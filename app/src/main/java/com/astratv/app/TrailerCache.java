package com.astratv.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.*;

/**
 * Validated trailer ids per title, plus video ids the YouTube player refused (deleted, private,
 * not embeddable), so a broken trailer is not retried on every focus.
 */
final class TrailerCache {
    static final long HIT_TTL_MS=30L*86_400_000L,MISS_TTL_MS=3L*86_400_000L,BAD_TTL_MS=30L*86_400_000L;
    static final int MAX_ENTRIES=400;
    static final class Entry{
        final String videoId,source;final long at;
        Entry(String videoId,String source,long at){this.videoId=videoId;this.source=source;this.at=at;}
        boolean hit(){return !videoId.isEmpty();}
    }
    private final SharedPreferences prefs;
    TrailerCache(Context c){prefs=c.getSharedPreferences("astra_trailers_v1",0);}

    /** A cached decision that is still fresh; null when the title must be looked up again. */
    synchronized Entry get(String titleKey,long now){
        String raw=prefs.getString("t."+titleKey,null);if(raw==null)return null;
        try{JSONObject o=new JSONObject(raw);Entry e=new Entry(o.optString("v"),o.optString("s"),o.optLong("at"));
            if(!fresh(e.hit(),e.at,now)||(e.hit()&&bad(e.videoId,now)))return null;return e;}
        catch(Exception ex){return null;}
    }
    synchronized void put(String titleKey,String videoId,String source,long now){
        try{prefs.edit().putString("t."+titleKey,new JSONObject().put("v",videoId==null?"":videoId).put("s",source==null?"":source).put("at",now).toString()).apply();}catch(Exception ignored){/* cache is best effort */}
        trim();
    }
    synchronized void markBad(String videoId,long now){prefs.edit().putLong("bad."+videoId,now).apply();}
    synchronized boolean bad(String videoId,long now){long at=prefs.getLong("bad."+videoId,0);return at>0&&now-at<BAD_TTL_MS;}
    synchronized void forget(String titleKey){prefs.edit().remove("t."+titleKey).apply();}

    static boolean fresh(boolean hit,long at,long now){return at>0&&now-at<(hit?HIT_TTL_MS:MISS_TTL_MS);}
    /** YouTube IFrame error codes that mean this video will not play embedded, now or later. */
    static boolean permanent(int youtubeError){return youtubeError==100||youtubeError==101||youtubeError==150||youtubeError==2;}

    private void trim(){
        Map<String,?> all=prefs.getAll();if(all.size()<=MAX_ENTRIES)return;
        List<Map.Entry<String,Long>> ages=new ArrayList<>();
        for(Map.Entry<String,?> e:all.entrySet()){
            long at;Object v=e.getValue();
            if(v instanceof Long)at=(Long)v;else{try{at=new JSONObject(String.valueOf(v)).optLong("at");}catch(Exception ex){at=0;}}
            ages.add(new AbstractMap.SimpleEntry<>(e.getKey(),at));
        }
        ages.sort(Map.Entry.comparingByValue());
        SharedPreferences.Editor ed=prefs.edit();for(int i=0;i<ages.size()-MAX_ENTRIES;i++)ed.remove(ages.get(i).getKey());ed.apply();
    }
}
