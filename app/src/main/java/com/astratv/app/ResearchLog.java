package com.astratv.app;

import android.content.SharedPreferences;
import org.json.*;

/** An allowlisted event record: never stores URLs, headers, cookies, responses or account IDs. */
final class ResearchLog {
    private final SharedPreferences prefs;
    ResearchLog(SharedPreferences prefs){this.prefs=prefs;}
    static JSONObject event(String provider,String stage,boolean success)throws JSONException{
        String id="unknown";for(Library.Addon addon:Library.addons())if(addon.id.equals(provider))id=provider;
        return new JSONObject().put("provider",id).put("stage",stage.equals("catalog")?"catalog":"source_check").put("success",success).put("timeUtcMillis",System.currentTimeMillis());
    }
    synchronized void record(String provider,String stage,boolean success){try{JSONArray old=new JSONArray(prefs.getString("research.events","[]")),next=new JSONArray();for(int i=Math.max(0,old.length()-99);i<old.length();i++)next.put(old.get(i));next.put(event(provider,stage,success));prefs.edit().putString("research.events",next.toString()).apply();}catch(JSONException ignored){}}
    synchronized String export()throws JSONException{return new JSONObject().put("schema",1).put("appVersion","0.3.0").put("events",new JSONArray(prefs.getString("research.events","[]"))).toString(2);}
}
