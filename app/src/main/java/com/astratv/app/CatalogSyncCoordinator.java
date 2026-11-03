package com.astratv.app;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Runs source syncs in the background: TTL and backoff per source, one in-flight sync per source,
 * transactional row replacement, and change notifications for the rows that actually changed.
 * A failing source keeps its previous rows; nothing here touches the main thread.
 */
final class CatalogSyncCoordinator {
    interface Listener{void onCatalogChanged(String sourceId,Set<String> changedRows);}
    interface TtlOverride{long ttl(String sourceId,long defaultMs);}
    /** Real playback check for a channel; returns normally when the stream answers with media. */
    interface Prober{void probe(Library.Title channel)throws Exception;}
    static final long HEALTH_TTL_MS=6L*60*60_000L,FAILED_HEALTH_TTL_MS=60L*60_000L;
    static final String HEALTH_SOURCE="health";
    private static final Pattern SECRET=Pattern.compile("(api_key=|/json/)[^&/\\s]+");

    private final CatalogStorage store;private final CatalogHttp http;private final ExecutorService workers,probes;private final LongSupplier clock;private final TtlOverride ttl;
    private final Map<String,CatalogSource> sources=new LinkedHashMap<>();
    private final Set<String> running=ConcurrentHashMap.newKeySet();
    private final Set<String> probing=ConcurrentHashMap.newKeySet();
    private final List<Listener> listeners=new CopyOnWriteArrayList<>();

    /** {@code probes} bounds channel health checks separately so they never delay catalog syncs. */
    CatalogSyncCoordinator(CatalogStorage store,CatalogHttp http,ExecutorService workers,ExecutorService probes,LongSupplier clock,TtlOverride ttl){
        this.store=store;this.http=http;this.workers=workers;this.probes=probes;this.clock=clock;this.ttl=ttl==null?(id,d)->d:ttl;
    }
    synchronized void register(CatalogSource s){sources.put(s.id(),s);}
    synchronized List<CatalogSource> sources(){return new ArrayList<>(sources.values());}
    synchronized CatalogSource source(String id){return sources.get(id);}
    long ttl(CatalogSource s){long v=ttl.ttl(s.id(),s.defaultTtlMs());return v>0?v:s.defaultTtlMs();}
    boolean running(String id){return running.contains(id);}
    boolean anyRunning(){return !running.isEmpty();}
    void addListener(Listener l){listeners.add(l);}
    void removeListener(Listener l){listeners.remove(l);}
    CatalogFreshnessTracker.State state(String id){CatalogFreshnessTracker.State s=store.state(id);return s==null?new CatalogFreshnessTracker.State():s;}

    /**
     * Launch, foreground and periodic entry point: starts every ready source whose window has passed.
     * {@code foreground} lets cheap sources revalidate (conditional requests) even inside their TTL.
     */
    int syncDue(boolean foreground){
        long now=clock.getAsLong();int started=0;
        for(CatalogSource s:sources()){
            if(!s.ready()&&(s instanceof SourceMovieCatalog||s instanceof SerialCatchupProvider))Diagnostics.get().unsupported(s.id(),s.notReadyReason());
            if(!s.ready()||running.contains(s.id()))continue;
            if(CatalogFreshnessTracker.due(store.state(s.id()),ttl(s),now,foreground&&s.checkOnForeground())&&start(s))started++;
        }
        return started;
    }
    /** Viewer-requested refresh of one source; TTL is ignored but an in-flight sync is reused. */
    boolean syncNow(String id){CatalogSource s=source(id);return s!=null&&s.ready()&&start(s);}
    int syncAll(){int n=0;for(CatalogSource s:sources())if(s.ready()&&start(s))n++;return n;}

    private boolean start(CatalogSource s){
        if(!running.add(s.id()))return false;
        try{workers.execute(()->run(s));return true;}
        catch(RejectedExecutionException e){running.remove(s.id());return false;}
    }
    void run(CatalogSource s){
        Set<String> changed=Collections.emptySet();
        try{
            CatalogFreshnessTracker.State state=CatalogFreshnessTracker.started(store.state(s.id()),clock.getAsLong());
            store.saveState(s.id(),state);
            try{
                List<CatalogSource.Batch> fetched=s.fetch(http),batches=new ArrayList<>(fetched.size());int items=0;
                for(CatalogSource.Batch b:fetched){List<CatalogItem> merged=CatalogDeduplication.merge(b.items);items+=merged.size();batches.add(new CatalogSource.Batch(b.rowId,merged,b.notModified));}
                changed=store.replaceRows(s.id(),batches,clock.getAsLong());
                store.saveState(s.id(),CatalogFreshnessTracker.succeeded(state,clock.getAsLong(),items));
            }catch(Exception e){
                String reason=describe(e);Diagnostics.get().metadataFailure(s.id(),reason,e instanceof SocketTimeoutException);
                Diagnostics.get().provider(Diagnostics.ofCatalog(e),s.id(),reason);
                store.saveState(s.id(),CatalogFreshnessTracker.failed(state,clock.getAsLong(),ttl(s),reason));
            }
        }catch(RuntimeException storageFailure){
            /* Database unavailable: keep whatever the UI already shows; the next window retries. */
        }finally{
            running.remove(s.id());
            for(Listener l:listeners)try{l.onCatalogChanged(s.id(),changed);}catch(RuntimeException ignored){/* listener bugs must not stop sync */}
        }
    }

    /** Probes channels whose health is unknown or old, at most {@code limit} per call. Results are stored apart from catalog metadata. */
    int checkHealth(List<CatalogItem> channels,Prober prober,int limit){
        long now=clock.getAsLong();Map<String,long[]> known=store.health();List<CatalogItem> due=new ArrayList<>();
        for(CatalogItem c:channels){
            if(due.size()>=limit)break;
            if(!CatalogItem.CHANNEL.equals(c.kind)||probing.contains(c.key()))continue;
            long[] h=known.get(c.key());
            if(h==null||healthExpired(h,now))due.add(c);
        }
        if(due.isEmpty())return 0;
        int queued=0;
        for(CatalogItem c:due){
            if(!probing.add(c.key()))continue;
            try{
                probes.execute(()->{
                    if(Thread.currentThread().isInterrupted()){probing.remove(c.key());return;}
                    boolean ok;
                    try{prober.probe(c.title);ok=true;}catch(Exception ignored){ok=false;}
                    try{store.saveHealth(c.key(),ok,clock.getAsLong());}catch(RuntimeException ignored){/* retried next pass */}
                    probing.remove(c.key());
                    for(Listener l:listeners)try{l.onCatalogChanged(HEALTH_SOURCE,Collections.singleton(c.key()));}catch(RuntimeException ignored){/* keep probing */}
                });
                queued++;
            }catch(RejectedExecutionException e){probing.remove(c.key());}
        }
        return queued;
    }
    static boolean healthExpired(long[] h,long now){
        long okAt=h[0],failAt=h[1];int fails=h.length>2?(int)h[2]:0;
        if(okAt>=failAt)return now-okAt>HEALTH_TTL_MS;
        return now-failAt>(retired(okAt,fails,now)?RETIRED_HEALTH_TTL_MS:FAILED_HEALTH_TTL_MS);
    }
    static final int RETIRE_AFTER_FAILS=3;
    static final long RETIRE_AFTER_MS=3L*24*60*60_000L,RETIRED_HEALTH_TTL_MS=24L*60*60_000L;
    /**
     * A channel whose last {@value #RETIRE_AFTER_FAILS}+ checks failed and that has not worked for
     * three days leaves the main listing. Its record and history stay, it is re-checked daily, and
     * one successful check brings it back.
     */
    static boolean retired(long okAt,int fails,long now){return fails>=RETIRE_AFTER_FAILS&&(okAt<=0||now-okAt>RETIRE_AFTER_MS);}
    static boolean retired(CatalogItem c,long now){return CatalogItem.CHANNEL.equals(c.kind)&&retired(c.healthOkAt,c.healthFails,now);}
    /** Viewer-facing failure text. Never includes request URLs or keys. */
    static String describe(Throwable e){
        if(e instanceof UnknownHostException)return "No internet connection";
        if(e instanceof SocketTimeoutException)return "Source timed out";
        if(e instanceof CatalogHttp.HttpStatusException){int s=((CatalogHttp.HttpStatusException)e).status;return s==401||s==403?"Source rejected the API key (HTTP "+s+")":s==429?"Source rate limit reached":"Source returned HTTP "+s;}
        if(e instanceof org.json.JSONException)return "Source sent an unexpected response";
        String m=e.getMessage();if(m==null||m.trim().isEmpty())return e instanceof IOException?"Network error":"Sync failed";
        m=SECRET.matcher(m).replaceAll("$1…");
        return m.length()>140?m.substring(0,140)+"…":m;
    }
    void shutdown(){workers.shutdownNow();probes.shutdownNow();}
}
