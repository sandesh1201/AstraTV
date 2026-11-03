package com.astratv.app;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Catalog HTTP: conditional requests (ETag / Last-Modified), identical in-flight requests shared,
 * bounded concurrency per host, and retries with exponential backoff for transient failures.
 * Cache keys are hashes, so API keys inside URLs are never stored in clear text.
 */
final class CatalogHttp {
    interface Transport{Reply get(String url,Map<String,String> headers)throws IOException;}
    interface Cache{Entry get(String key);void put(String key,Entry entry);}
    interface Sleeper{void sleep(long ms)throws InterruptedException;}
    static final class Reply{
        final int status;final String body,etag,lastModified;final long retryAfterMs;
        Reply(int status,String body,String etag,String lastModified,long retryAfterMs){this.status=status;this.body=body==null?"":body;this.etag=etag==null?"":etag;this.lastModified=lastModified==null?"":lastModified;this.retryAfterMs=retryAfterMs;}
    }
    static final class Entry{
        final String etag,lastModified,body;final long fetchedAt;
        Entry(String etag,String lastModified,String body,long fetchedAt){this.etag=etag==null?"":etag;this.lastModified=lastModified==null?"":lastModified;this.body=body==null?"":body;this.fetchedAt=fetchedAt;}
    }
    static final class Result{
        final String body;final boolean notModified;final long fetchedAt;
        Result(String body,boolean notModified,long fetchedAt){this.body=body;this.notModified=notModified;this.fetchedAt=fetchedAt;}
    }
    static final class HttpStatusException extends IOException{
        final int status;HttpStatusException(int status){super("Source returned HTTP "+status);this.status=status;}
    }
    static final int MAX_ATTEMPTS=3,PER_HOST=3;
    private final Transport transport;private final Cache cache;private final Sleeper sleeper;
    private final Map<String,CompletableFuture<Result>> inflight=new ConcurrentHashMap<>();
    private final Map<String,Semaphore> hosts=new ConcurrentHashMap<>();
    CatalogHttp(Transport transport,Cache cache,Sleeper sleeper){this.transport=transport;this.cache=cache;this.sleeper=sleeper;}
    static CatalogHttp android(Cache cache){return new CatalogHttp(CatalogHttp::fetch,cache,Thread::sleep);}

    /** Fresh body, or the cached body with {@code notModified=true} when the server confirms nothing changed. */
    Result get(String url)throws IOException{return get(url,Collections.emptyMap());}
    /** {@code extra} headers identify the client to a service; they are not part of the cache key. */
    Result get(String url,Map<String,String> extra)throws IOException{
        String key=PlaylistParser.stable(url);
        CompletableFuture<Result> mine=new CompletableFuture<>(),running=inflight.putIfAbsent(key,mine);
        if(running!=null)return await(running);
        try{Result r=load(url,key,extra);mine.complete(r);return r;}
        catch(IOException|RuntimeException e){mine.completeExceptionally(e);throw e;}
        finally{inflight.remove(key,mine);}
    }
    /** Last stored body for this URL without touching the network, or null. */
    Entry cached(String url){return cache==null?null:cache.get(PlaylistParser.stable(url));}
    private static Result await(CompletableFuture<Result> f)throws IOException{
        try{return f.get();}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("Catalog request interrupted");}
        catch(ExecutionException e){Throwable c=e.getCause();if(c instanceof IOException)throw (IOException)c;if(c instanceof RuntimeException)throw (RuntimeException)c;throw new IOException(c);}
    }
    private Result load(String url,String key,Map<String,String> extra)throws IOException{
        Entry cached=cache==null?null:cache.get(key);
        Map<String,String> headers=new LinkedHashMap<>(extra);
        if(cached!=null&&!cached.body.isEmpty()){if(!cached.etag.isEmpty())headers.put("If-None-Match",cached.etag);if(!cached.lastModified.isEmpty())headers.put("If-Modified-Since",cached.lastModified);}
        Semaphore gate=hosts.computeIfAbsent(host(url),h->new Semaphore(PER_HOST));
        IOException last=null;
        for(int attempt=0;attempt<MAX_ATTEMPTS;attempt++){
            long wait=0;
            try{
                gate.acquire();Reply r;
                try{r=transport.get(url,headers);}finally{gate.release();}
                long now=System.currentTimeMillis();
                if(r.status==304&&cached!=null){if(cache!=null)cache.put(key,new Entry(cached.etag,cached.lastModified,cached.body,now));return new Result(cached.body,true,now);}
                if(r.status>=200&&r.status<300){if(cache!=null)cache.put(key,new Entry(r.etag,r.lastModified,r.body,now));return new Result(r.body,false,now);}
                last=new HttpStatusException(r.status);
                if(!retryable(r.status))throw last;
                wait=r.retryAfterMs>0?Math.min(r.retryAfterMs,8000):backoff(attempt);
            }catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("Catalog request interrupted");}
            catch(UnknownHostException|javax.net.ssl.SSLException e){throw e;}
            catch(HttpStatusException e){if(!retryable(e.status))throw e;last=e;wait=backoff(attempt);}
            catch(IOException e){last=e;wait=backoff(attempt);}
            if(attempt+1<MAX_ATTEMPTS)try{sleeper.sleep(wait);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("Catalog request interrupted");}
        }
        throw last==null?new IOException("Catalog request failed"):last;
    }
    static boolean retryable(int status){return status==408||status==429||status>=500;}
    static long backoff(int attempt){return 500L<<Math.min(attempt,4);}
    private static String host(String url){try{return new URL(url).getHost().toLowerCase(Locale.ROOT);}catch(MalformedURLException e){return "";}}

    /** HTTPS only, no private-network hosts, bounded redirects and response size. */
    static Reply fetch(String address,Map<String,String> headers)throws IOException{
        String current=address;
        for(int redirect=0;redirect<5;redirect++){
            if(!Library.https(current))throw new IOException("Only HTTPS sources are supported");
            if(ProviderRiskScanner.privateHost(new URL(current).getHost()))throw new IOException("Source pointed at a local network address, which is blocked");
            HttpURLConnection c=(HttpURLConnection)new URL(current).openConnection();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(6000);c.setReadTimeout(20000);c.setUseCaches(false);
            c.setRequestProperty("User-Agent","SaandioTV/0.4 (Android TV catalog sync; https://github.com/sandesh1201/AstraTV)");c.setRequestProperty("Accept","application/json,*/*");
            for(Map.Entry<String,String> h:headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());
            try{
                int status=c.getResponseCode();
                if(status>=300&&status<400&&status!=304){String location=c.getHeaderField("Location");if(location==null)throw new IOException("Source sent an invalid redirect");current=ProviderParser.absolute(current,location);continue;}
                long retryAfter=0;String ra=c.getHeaderField("Retry-After");if(ra!=null)try{retryAfter=Long.parseLong(ra.trim())*1000L;}catch(NumberFormatException ignored){/* HTTP-date form: use backoff */}
                String body="";
                if(status>=200&&status<300)try(InputStream in=c.getInputStream()){body=read(in,6*1024*1024);}
                return new Reply(status,body,c.getHeaderField("ETag"),c.getHeaderField("Last-Modified"),retryAfter);
            }finally{c.disconnect();}
        }
        throw new IOException("Too many source redirects");
    }
    private static String read(InputStream in,int limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[16384];int n;
        while((n=in.read(buf))!=-1){if(out.size()+n>limit)throw new IOException("Source response is too large");out.write(buf,0,n);}
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
