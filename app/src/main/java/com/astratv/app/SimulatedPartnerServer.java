package com.astratv.app;

import org.json.*;
import java.net.URLDecoder;
import java.util.*;

/** In-process mock of the partner API documented in docs/EINTHUSAN.md. It never opens a socket. */
public final class SimulatedPartnerServer implements EinthusanSimulatedProvider.Transport {
    private final JSONObject fixture;
    private final Map<String,JSONObject> faults=new HashMap<>();
    public SimulatedPartnerServer(String fixtureJson)throws JSONException{fixture=new JSONObject(fixtureJson);}
    /** Forces a response for every request whose path starts with the prefix (contract tests). */
    public SimulatedPartnerServer fault(String pathPrefix,int status,String code){
        try{faults.put(pathPrefix,new JSONObject().put("status",status).put("code",code));}catch(JSONException e){throw new IllegalStateException(e);}return this;
    }
    @Override public EinthusanSimulatedProvider.Response get(String pathAndQuery,String token){
        try{
            for(Map.Entry<String,JSONObject> f:faults.entrySet())if(pathAndQuery.startsWith(f.getKey()))return error(f.getValue().getInt("status"),f.getValue().getString("code"));
            if(!fixture.optString("token").equals(token))return error(401,"unauthenticated");
            String path=pathAndQuery,query="";int q=pathAndQuery.indexOf('?');if(q>=0){path=pathAndQuery.substring(0,q);query=pathAndQuery.substring(q+1);}
            Map<String,String> args=new HashMap<>();for(String pair:query.split("&")){int e=pair.indexOf('=');if(e>0)args.put(pair.substring(0,e),URLDecoder.decode(pair.substring(e+1),"UTF-8"));}
            JSONArray titles=fixture.getJSONArray("titles");
            if(path.equals("/v1/catalog")){
                String language=args.getOrDefault("language",""),search=args.getOrDefault("q","").toLowerCase(Locale.ROOT);
                int page=Math.max(1,parse(args.get("page"),1)),size=Math.max(1,Math.min(50,parse(args.get("pageSize"),20)));
                List<JSONObject> matched=new ArrayList<>();
                for(int i=0;i<titles.length();i++){JSONObject t=titles.getJSONObject(i);
                    if(!language.isEmpty()&&!contains(t.optJSONArray("languages"),language))continue;
                    if(!search.isEmpty()&&!t.optString("title").toLowerCase(Locale.ROOT).contains(search))continue;
                    matched.add(t);}
                int pages=Math.max(1,(matched.size()+size-1)/size);JSONArray items=new JSONArray();
                for(int i=(page-1)*size;i<Math.min(matched.size(),page*size);i++)items.put(summary(matched.get(i)));
                return ok(new JSONObject().put("page",page).put("totalPages",pages).put("items",items));
            }
            if(path.startsWith("/v1/titles/")){
                String rest=path.substring("/v1/titles/".length());boolean playback=rest.endsWith("/playback");String id=playback?rest.substring(0,rest.length()-"/playback".length()):rest;
                JSONObject t=null;for(int i=0;i<titles.length();i++)if(titles.getJSONObject(i).getString("id").equals(id))t=titles.getJSONObject(i);
                if(t==null)return error(404,"not_found");
                if(!playback){JSONObject full=summary(t);full.put("cast",t.optJSONArray("cast"));return ok(full);}
                JSONObject fault=t.optJSONObject("fault");if(fault!=null)return error(fault.getInt("status"),fault.getString("code"));
                return ok(new JSONObject().put("streams",t.optJSONArray("streams")));
            }
            return error(404,"not_found");
        }catch(Exception e){return error(500,"internal");}
    }
    private static JSONObject summary(JSONObject t)throws JSONException{
        JSONObject o=new JSONObject();for(String k:new String[]{"id","title","year","releaseDate","addedAt","languages","audioLanguages","genres","runtimeMinutes","synopsis","license","poster"})if(t.has(k))o.put(k,t.get(k));return o;
    }
    private static boolean contains(JSONArray a,String v){if(a==null)return false;for(int i=0;i<a.length();i++)if(a.optString(i).equalsIgnoreCase(v))return true;return false;}
    private static int parse(String v,int fallback){try{return v==null?fallback:Integer.parseInt(v);}catch(NumberFormatException e){return fallback;}}
    private static EinthusanSimulatedProvider.Response ok(JSONObject body){return new EinthusanSimulatedProvider.Response(200,body.toString());}
    private static EinthusanSimulatedProvider.Response error(int status,String code){
        try{return new EinthusanSimulatedProvider.Response(status,new JSONObject().put("error",new JSONObject().put("code",code)).toString());}catch(JSONException e){return new EinthusanSimulatedProvider.Response(status,"");}
    }
}
