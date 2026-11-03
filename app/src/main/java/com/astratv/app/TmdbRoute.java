package com.astratv.app;

import java.net.URLEncoder;
import java.util.*;

/**
 * Where TMDB requests go. Release builds use the Saandio Catalog API, which keeps the TMDB
 * credential on the server; a personal key (debug builds, or Settings → Developer) calls TMDB directly.
 */
final class TmdbRoute {
    static final String DIRECT="https://api.themoviedb.org/3";
    static final String CLIENT_HEADER="X-Saandio-Client";
    private final String base,key,clientToken;
    private TmdbRoute(String base,String key,String clientToken){this.base=base;this.key=key;this.clientToken=clientToken;}

    /** Null when the build has no catalog service and no developer key, so TMDB sources stay idle. */
    static TmdbRoute of(String catalogApiBase,String clientToken,String developerKey){
        if(developerKey!=null&&!developerKey.trim().isEmpty())return new TmdbRoute(DIRECT,developerKey.trim(),"");
        String b=catalogApiBase==null?"":catalogApiBase.trim();
        while(b.endsWith("/"))b=b.substring(0,b.length()-1);
        if(!Library.https(b))return null;
        return new TmdbRoute(b,"",clientToken==null?"":clientToken.trim());
    }
    boolean proxied(){return key.isEmpty();}
    String label(){return proxied()?"Saandio Catalog API":"developer TMDB key";}
    /** {@code path} is a TMDB v3 path with optional query, e.g. {@code /discover/movie?page=1}. */
    String url(String path){
        if(!proxied())return DIRECT+path+(path.contains("?")?"&":"?")+"api_key="+enc(key);
        int q=path.indexOf('?');
        String p=q<0?path:path.substring(0,q),rest=q<0?"":path.substring(q+1);
        return base+"/api/tmdb?path="+enc(p)+(rest.isEmpty()?"":"&"+rest);
    }
    Map<String,String> headers(){return proxied()&&!clientToken.isEmpty()?Collections.singletonMap(CLIENT_HEADER,clientToken):Collections.emptyMap();}
    private static String enc(String s){try{return URLEncoder.encode(s,"UTF-8").replace("+","%20");}catch(java.io.UnsupportedEncodingException e){return "";}}
}
