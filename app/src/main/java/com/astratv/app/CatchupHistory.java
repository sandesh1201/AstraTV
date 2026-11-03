package com.astratv.app;

import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/**
 * Catch-up episodes the viewer opened, newest first, for the Continue Watching row. Playback runs
 * in the official app, which keeps the resume position itself; Saandio stores only which episode
 * was opened and when.
 */
final class CatchupHistory {
    static final String KEY="catchup.history";static final int MAX=40;
    private CatchupHistory(){}

    static void opened(SharedPreferences p,CatalogItem c,long now){
        if(c.episode==null)return;
        try{p.edit().putString(KEY,add(p.getString(KEY,"[]"),c,now)).apply();}catch(JSONException ignored){/* history is best effort */}
    }
    static String add(String stored,CatalogItem c,long now)throws JSONException{
        JSONArray old=parse(stored),out=new JSONArray();
        out.put(new JSONObject().put("at",now).put("item",c.json()));
        for(int i=0;i<old.length()&&out.length()<MAX;i++){JSONObject o=old.optJSONObject(i);JSONObject item=o==null?null:o.optJSONObject("item");
            if(item!=null&&!c.key().equals(key(item)))out.put(o);}
        return out.toString();
    }
    static List<CatalogItem> items(SharedPreferences p,int limit){return items(p.getString(KEY,"[]"),limit);}
    static List<CatalogItem> items(String stored,int limit){
        List<CatalogItem> out=new ArrayList<>();JSONArray a=parse(stored);
        for(int i=0;i<a.length()&&out.size()<limit;i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;try{out.add(CatalogItem.from(o.getJSONObject("item")));}catch(JSONException ignored){/* skip damaged entry */}}
        return out;
    }
    private static String key(JSONObject item){JSONObject t=item.optJSONObject("t");return t==null?"":t.optString("provider","library")+":"+t.optString("id");}
    private static JSONArray parse(String s){try{return new JSONArray(s==null?"[]":s);}catch(JSONException e){return new JSONArray();}}
}
