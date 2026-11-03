package com.astratv.app;

import java.util.*;

/**
 * Remembers artwork URLs that recently failed so cards stop re-requesting them. A definitive
 * failure (HTTP 4xx, undecodable bytes) is skipped for a day; a transient one, after its bounded
 * retries, for ten minutes. Bounded LRU so a long session cannot grow it without limit.
 */
final class ArtworkFailures {
    static final long DEFINITIVE_MS=24L*60*60_000L,TRANSIENT_MS=10L*60_000L;
    static final int MAX_ENTRIES=512;
    private static final long[] RETRY_DELAYS_MS={600,1800};
    private final LinkedHashMap<String,Long> until=new LinkedHashMap<String,Long>(64,0.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Long> eldest){return size()>MAX_ENTRIES;}
    };
    synchronized boolean known(String url,long now){
        Long u=until.get(url);if(u==null)return false;
        if(now>=u){until.remove(url);return false;}
        return true;
    }
    synchronized void failed(String url,boolean definitive,long now){until.put(url,now+(definitive?DEFINITIVE_MS:TRANSIENT_MS));}
    synchronized int size(){return until.size();}
    synchronized void clear(){until.clear();}
    /** HTTPS with a host and a file name; rejects paths built from a missing value ("…/w342null", "…/w342"). */
    static boolean validUrl(String url){
        if(!Library.https(url))return false;
        try{
            java.net.URI u=new java.net.URI(url);String host=u.getHost(),path=u.getRawPath();
            if(host==null||host.isEmpty()||path==null)return false;
            String file=path.substring(path.lastIndexOf('/')+1);
            if(file.isEmpty()||file.equals("null")||file.endsWith("null")&&!file.contains("."))return false;
            return !host.equals("image.tmdb.org")||file.contains(".");
        }catch(java.net.URISyntaxException e){return false;}
    }
    /** 4xx other than timeout/rate limit means the URL itself is bad; retrying cannot help. */
    static boolean definitive(int status){return status>=400&&status<500&&status!=408&&status!=429;}
    /** Delay before retry {@code attempt} (0-based), or -1 once the retries are used up. */
    static long retryDelayMs(int attempt){return attempt>=0&&attempt<RETRY_DELAYS_MS.length?RETRY_DELAYS_MS[attempt]:-1;}
}
