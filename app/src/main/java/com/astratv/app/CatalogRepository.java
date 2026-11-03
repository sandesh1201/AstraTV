package com.astratv.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * The UI's single entry point to catalog data. Every database read and network call runs on a
 * background executor and delivers results on the main thread; screens render the saved catalog
 * immediately and are told which rows changed when a background sync lands.
 */
final class CatalogRepository implements CatalogSyncCoordinator.Listener {
    interface Listener{void onRowsChanged(Set<String> rows);}
    /** {@code done} is true on the last delivery for a request (network copy, or saved copy after a failure). */
    interface PageCallback{void page(TmdbCatalogSource.Page page,boolean fromCache,boolean done);}
    static final String HOME_RECENT="home.recent",HOME_CONTINUE="home.continue",HOME_LIVE="live.home",HOME_SPORTS="sports.home",ROW_FAVORITES="live.favorites";
    static final String HOME_LIBRARY="home.library",HOME_SAMPLES="home.samples";
    static final String TV_CONTINUE="tv.continue",SPORTS_LIVE="sports.live",SPORTS_UPCOMING="sports.upcoming",SPORTS_RESULTS="sports.results",SPORTS_OTHER="sports.unconfirmed";
    static final String SYNC_TOKEN="sync:";
    static final int PAGE=40;
    private static final long PERIODIC_MS=5L*60_000L,DEBOUNCE_MS=350L;
    private static CatalogRepository instance;

    final CatalogStore store;final CatalogHttp http;final CatalogSyncCoordinator sync;final TmdbCatalogSource movies,tv;final PlaybackSourceResolver resolver;
    private final Context app;private final SharedPreferences prefs,ui;
    private final ExecutorService db=Executors.newSingleThreadExecutor(r->named(r,"catalog-db"));
    private final ExecutorService net=new ThreadPoolExecutor(3,3,30,TimeUnit.SECONDS,new LinkedBlockingQueue<>(),r->named(r,"catalog-net"));
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<Listener> listeners=new ArrayList<>();
    private final Set<String> pending=new LinkedHashSet<>();
    private boolean flushScheduled,foreground;
    private EinthusanSimulatedProvider simulation;
    private final Runnable flush=this::flush;
    private final Runnable periodic=new Runnable(){@Override public void run(){if(!foreground)return;db.execute(()->sync.syncDue(false));main.postDelayed(this,PERIODIC_MS);}};

    static synchronized CatalogRepository get(Context c){if(instance==null)instance=new CatalogRepository(c.getApplicationContext());return instance;}
    private CatalogRepository(Context app){
        this.app=app;prefs=app.getSharedPreferences("astra",0);ui=app.getSharedPreferences("astra_ui_v1",0);
        store=new CatalogStore(app);http=CatalogHttp.android(store);resolver=new PlaybackSourceResolver(store);
        movies=new TmdbCatalogSource(false,()->ApiKeys.tmdb(app),LocalDate::now);tv=new TmdbCatalogSource(true,()->ApiKeys.tmdb(app),LocalDate::now);
        ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new LinkedBlockingQueue<>(),r->named(r,"catalog-sync"));workers.allowCoreThreadTimeOut(true);
        ThreadPoolExecutor probes=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new LinkedBlockingQueue<>(),r->named(r,"channel-probe"));probes.allowCoreThreadTimeOut(true);
        sync=new CatalogSyncCoordinator(store,http,workers,probes,System::currentTimeMillis,(id,d)->{long minutes=ui.getLong("sync.ttl."+id,0);return minutes>0?minutes*60_000L:d;});
        sync.register(movies);sync.register(tv);
        sync.register(new HdCatalogSource(this::licensed));
        sync.register(new LiveTvSource(()->prefs.getBoolean("addon.news",true)));
        sync.register(new SportsSource(()->ApiKeys.sportsDb(app),java.time.Clock::systemUTC));
        sync.addListener(this);
        ((ThreadPoolExecutor)net).allowCoreThreadTimeOut(true);
    }
    private static Thread named(Runnable r,String name){Thread t=new Thread(r,name);t.setPriority(Thread.NORM_PRIORITY-1);return t;}

    // ---- lifecycle -------------------------------------------------------------------------
    /** App visible: revalidate what is due without blocking the UI, then keep checking while visible. */
    void onForeground(){
        foreground=true;main.removeCallbacks(periodic);
        db.execute(()->{store.trimHttp(System.currentTimeMillis());sync.syncDue(true);});
        main.postDelayed(periodic,PERIODIC_MS);
    }
    void onBackground(){foreground=false;main.removeCallbacks(periodic);}
    void refresh(String sourceId){db.execute(()->sync.syncNow(sourceId));}
    void refreshAll(){db.execute(sync::syncAll);}

    // ---- change notifications ---------------------------------------------------------------
    void addListener(Listener l){if(!listeners.contains(l))listeners.add(l);}
    void removeListener(Listener l){listeners.remove(l);}
    @Override public void onCatalogChanged(String sourceId,Set<String> rows){
        Set<String> affected=new LinkedHashSet<>();
        if(CatalogSyncCoordinator.HEALTH_SOURCE.equals(sourceId)){affected.add(LiveTvSource.ROW);affected.add(HOME_LIVE);affected.add(ROW_FAVORITES);}
        else{
            affected.add(SYNC_TOKEN+sourceId);affected.addAll(rows);
            if(SportsSource.ID.equals(sourceId))affected.add(SportsSource.ROW);
            for(String r:affected.toArray(new String[0])){
                if(r.startsWith("movies."))affected.add(HOME_RECENT);
                if(r.equals(LiveTvSource.ROW)){affected.add(HOME_LIVE);affected.add(ROW_FAVORITES);}
                if(r.equals(SportsSource.ROW))affected.addAll(Arrays.asList(HOME_SPORTS,SPORTS_LIVE,SPORTS_UPCOMING,SPORTS_RESULTS,SPORTS_OTHER));
            }
        }
        main.post(()->{pending.addAll(affected);if(!flushScheduled){flushScheduled=true;main.postDelayed(flush,DEBOUNCE_MS);}});
    }
    private void flush(){
        flushScheduled=false;if(pending.isEmpty())return;
        Set<String> rows=new LinkedHashSet<>(pending);pending.clear();
        for(Listener l:new ArrayList<>(listeners))l.onRowsChanged(rows);
    }
    /** Personal rows changed locally (watchlist, playback progress, favorites). */
    void touched(String...rows){main.post(()->{pending.addAll(Arrays.asList(rows));if(!flushScheduled){flushScheduled=true;main.postDelayed(flush,DEBOUNCE_MS);}});}

    // ---- rows ------------------------------------------------------------------------------
    void row(String rowId,int limit,Consumer<List<CatalogItem>> callback){
        db.execute(()->{List<CatalogItem> items=read(rowId,limit);main.post(()->callback.accept(items));});
    }
    /** Reads rows in the given order, delivering each as soon as it is ready so the first visible rows paint first. */
    void rows(List<String> rowIds,int limit,BiConsumer<String,List<CatalogItem>> callback){
        db.execute(()->{for(String id:rowIds){List<CatalogItem> items=read(id,limit);main.post(()->callback.accept(id,items));}});
    }
    private List<CatalogItem> read(String rowId,int limit){
        switch(rowId){
            case HOME_RECENT:return store.recent(CatalogItem.MOVIE,limit);
            case HOME_CONTINUE:return continueWatching(limit);
            case HOME_LIBRARY:{List<CatalogItem> out=new ArrayList<>();for(Library.Title t:Library.titles(app))out.add(CatalogItem.of(t));return DiscoverQuery.order(out,"newest");}
            case HOME_SAMPLES:{List<CatalogItem> out=new ArrayList<>();for(Library.Title t:Library.samples())out.add(CatalogItem.of(t));return out;}
            case TV_CONTINUE:{List<CatalogItem> out=new ArrayList<>();for(CatalogItem c:continueWatching(200))if(CatalogItem.SERIES.equals(c.kind)&&out.size()<limit)out.add(c);return out;}
            case SPORTS_LIVE:return events(SportsSource.LIVE,limit);
            case SPORTS_UPCOMING:return events(SportsSource.UPCOMING,limit);
            case SPORTS_RESULTS:return events(SportsSource.FINISHED,limit);
            case SPORTS_OTHER:{List<CatalogItem> out=events(SportsSource.UNCONFIRMED,limit);out.addAll(events(SportsSource.POSTPONED,limit));out.addAll(events(SportsSource.CANCELLED,limit));return out.size()>limit?new ArrayList<>(out.subList(0,limit)):out;}
            case HOME_LIVE:{List<CatalogItem> all=channels(store.row(LiveTvSource.ROW,2000));return all.subList(0,Math.min(limit,all.size()));}
            case ROW_FAVORITES:{Set<String> fav=favorites();List<CatalogItem> out=new ArrayList<>();for(CatalogItem c:channels(store.row(LiveTvSource.ROW,2000)))if(fav.contains(c.key()))out.add(c);return out;}
            case SportsSource.ROW:return sports(limit);
            case HOME_SPORTS:{List<CatalogItem> out=new ArrayList<>();for(CatalogItem e:sports(300)){if(SportsSource.LIVE.equals(e.status)||SportsSource.UPCOMING.equals(e.status))out.add(e);if(out.size()>=limit)break;}return out;}
            case LiveTvSource.ROW:return channels(store.row(rowId,limit));
            default:return store.row(rowId,limit);
        }
    }
    private List<CatalogItem> events(String status,int limit){
        List<CatalogItem> out=new ArrayList<>();
        for(CatalogItem e:sports(300))if(status.equals(e.status)&&out.size()<limit)out.add(e);
        return out;
    }
    private List<CatalogItem> sports(int limit){
        CatalogFreshnessTracker.State s=store.state(SportsSource.ID);
        List<CatalogItem> out=SportsSource.reconcile(store.row(SportsSource.ROW,limit),s==null?0:s.syncedAt,System.currentTimeMillis());
        return out.size()>limit?new ArrayList<>(out.subList(0,limit)):out;
    }
    /** Favorites first, then channels whose last probe succeeded, then unchecked, then failed. */
    private List<CatalogItem> channels(List<CatalogItem> raw){
        Set<String> fav=favorites();
        for(CatalogItem c:raw)c.playable="ok".equals(c.health);
        List<CatalogItem> out=new ArrayList<>(raw);
        out.sort(Comparator.comparingInt((CatalogItem c)->fav.contains(c.key())?0:"ok".equals(c.health)?1:c.health.isEmpty()?2:3));
        return out;
    }
    void freshness(String rowId,Consumer<String> callback){
        String source=sourceFor(rowId);
        db.execute(()->{
            String label;CatalogSource s=source==null?null:sync.source(source);
            if(s==null)label="";
            else if(!s.ready())label=s.notReadyReason();
            else{CatalogFreshnessTracker.State st=sync.state(source);label=sync.running(source)&&st.syncedAt==0?"Loading…":CatalogFreshnessTracker.label(st,sync.ttl(s),System.currentTimeMillis());}
            String result=label;main.post(()->callback.accept(result));
        });
    }
    /** True when the row's source is older than it should be or failing; drives the subtle freshness dot. */
    void stale(String rowId,Consumer<Boolean> callback){
        String source=sourceFor(rowId);
        db.execute(()->{CatalogSource s=source==null?null:sync.source(source);boolean stale=s!=null&&s.ready()&&CatalogFreshnessTracker.stale(sync.state(source),sync.ttl(s),System.currentTimeMillis());main.post(()->callback.accept(stale));});
    }
    static String sourceFor(String rowId){
        if(rowId.startsWith("movies.")||rowId.equals(HOME_RECENT))return TmdbCatalogSource.MOVIES;
        if(rowId.startsWith("tv."))return TmdbCatalogSource.TV;
        if(rowId.startsWith("hd.")||rowId.startsWith("licensed."))return HdCatalogSource.ID;
        if(rowId.startsWith("live."))return LiveTvSource.ID;
        if(rowId.startsWith("sports."))return SportsSource.ID;
        return null;
    }
    static final class SourceStatus{
        final String id,label,freshness,reason;final boolean ready,running;final long ttlMs;final int items;
        SourceStatus(String id,String label,boolean ready,String reason,boolean running,long ttlMs,int items,String freshness){this.id=id;this.label=label;this.ready=ready;this.reason=reason;this.running=running;this.ttlMs=ttlMs;this.items=items;this.freshness=freshness;}
    }
    void sources(Consumer<List<SourceStatus>> callback){
        db.execute(()->{
            List<SourceStatus> out=new ArrayList<>();long now=System.currentTimeMillis();
            for(CatalogSource s:sync.sources()){CatalogFreshnessTracker.State st=sync.state(s.id());out.add(new SourceStatus(s.id(),s.label(),s.ready(),s.ready()?"":s.notReadyReason(),sync.running(s.id()),sync.ttl(s),st.items,CatalogFreshnessTracker.label(st,sync.ttl(s),now)));}
            main.post(()->callback.accept(out));
        });
    }
    void setTtlMinutes(String sourceId,long minutes){ui.edit().putLong("sync.ttl."+sourceId,Math.max(0,minutes)).apply();}

    // ---- grids, filters, search ---------------------------------------------------------------
    /**
     * Stale-while-revalidate paging: the stored copy of a page (if any) is delivered first, then the
     * network copy. Local-only queries (recently added, verified quality) page through the store.
     */
    void discover(DiscoverQuery q,int page,PageCallback callback,Consumer<String> error){
        if(!q.remote()){
            db.execute(()->{
                List<CatalogItem> pool=q.minHeight>0?store.row(HdCatalogSource.ROW_PLAYABLE,500):store.recent(q.movie()?CatalogItem.MOVIE:CatalogItem.SERIES,1500);
                List<CatalogItem> hits=new ArrayList<>();for(CatalogItem c:pool)if(q.matches(c))hits.add(c);
                hits=DiscoverQuery.order(hits,q.sort);
                int from=(page-1)*PAGE,to=Math.min(hits.size(),from+PAGE),total=Math.max(1,(hits.size()+PAGE-1)/PAGE);
                TmdbCatalogSource.Page p=new TmdbCatalogSource.Page(from<to?new ArrayList<>(hits.subList(from,to)):new ArrayList<>(),page,total,true);
                main.post(()->callback.page(p,false,true));
            });
            return;
        }
        TmdbCatalogSource source=q.movie()?movies:tv;
        if(!source.ready()){main.post(()->error.accept(source.notReadyReason()));return;}
        net.execute(()->{
            TmdbCatalogSource.Page cached=source.cachedPage(http,q,page);
            if(cached!=null)main.post(()->callback.page(cached,true,false));
            try{TmdbCatalogSource.Page fresh=source.discover(http,q,page);TmdbCatalogSource.Page shown=cached!=null&&same(cached,fresh)?cached:fresh;boolean stored=shown==cached?false:fresh.fromCache;main.post(()->callback.page(shown,stored,true));}
            catch(Exception e){
                if(cached!=null)main.post(()->callback.page(cached,true,true));
                else{String m=CatalogSyncCoordinator.describe(e);main.post(()->error.accept(m));}
            }
        });
    }
    private static boolean same(TmdbCatalogSource.Page a,TmdbCatalogSource.Page b){
        if(a.items.size()!=b.items.size()||a.totalPages!=b.totalPages)return false;
        for(int i=0;i<a.items.size();i++)if(!a.items.get(i).signature().equals(b.items.get(i).signature()))return false;
        return true;
    }
    void genres(boolean movie,Consumer<Map<Integer,String>> callback){
        TmdbCatalogSource source=movie?movies:tv;
        if(!source.ready()){main.post(()->callback.accept(Collections.emptyMap()));return;}
        net.execute(()->{Map<Integer,String> g;try{g=source.genreMap(http,movie?"movie":"tv");}catch(Exception e){g=Collections.emptyMap();}Map<Integer,String> r=g;main.post(()->callback.accept(r));});
    }
    void years(String kind,Consumer<List<Integer>> callback){
        db.execute(()->{List<Integer> y=store.years(kind);int now=LocalDate.now().getYear();if(!y.contains(now))y.add(0,now);main.post(()->callback.accept(y));});
    }
    /** Languages that actually occur in the synced catalog, mapped to display names. */
    void languages(String kind,Consumer<List<String[]>> callback){
        db.execute(()->{
            Map<String,String[]> out=new LinkedHashMap<>();
            for(String[] l:TmdbCatalogSource.MOVIE_LANGUAGES)out.put(l[0],new String[]{l[0],TmdbCatalogSource.languageName(l[0])});
            for(String code:store.languages(kind).keySet()){if(code.length()>3)continue;out.putIfAbsent(code,new String[]{code,TmdbCatalogSource.languageName(code)});}
            List<String[]> r=new ArrayList<>(out.values());main.post(()->callback.accept(r));
        });
    }
    void search(String query,Consumer<List<CatalogItem>> local,Consumer<List<CatalogItem>> remote){
        db.execute(()->{List<CatalogItem> found=store.search(query,60);main.post(()->local.accept(found));});
        if(movies.ready())net.execute(()->{try{List<CatalogItem> found=movies.search(http,query);main.post(()->remote.accept(found));}catch(Exception e){main.post(()->remote.accept(Collections.emptyList()));}});
    }

    // ---- details ----------------------------------------------------------------------------
    void resolve(CatalogItem item,Consumer<PlaybackSourceResolver.Resolution> callback){
        db.execute(()->{PlaybackSourceResolver.Resolution r=resolver.resolve(item);main.post(()->callback.accept(r));});
    }
    void watchProviders(Library.Title t,Consumer<List<String>> callback){
        int[] ids=TmdbCatalogSource.ids(t);if(ids==null||!movies.ready()){main.post(()->callback.accept(Collections.emptyList()));return;}
        net.execute(()->{List<String> out;try{out=movies.watchProviders(http,ids[0]==1,ids[1],"IN");}catch(Exception e){out=Collections.emptyList();}List<String> r=out;main.post(()->callback.accept(r));});
    }
    /** Fills seasons for a TMDB series (needed before the episode browser). */
    void seriesDetails(CatalogItem item,Consumer<CatalogItem> callback){
        if(item.tmdbId<=0||!tv.ready()){main.post(()->callback.accept(item));return;}
        net.execute(()->{try{tv.lastEpisode(http,item);}catch(Exception ignored){/* show what we have */}main.post(()->callback.accept(item));});
    }
    void season(int tvId,int season,Library.Title show,Consumer<List<CatalogItem>> callback,Consumer<String> error){
        net.execute(()->{try{List<CatalogItem> eps=tv.season(http,tvId,season,show);main.post(()->callback.accept(eps));}catch(Exception e){String m=CatalogSyncCoordinator.describe(e);main.post(()->error.accept(m));}});
    }

    // ---- channels -----------------------------------------------------------------------------
    /** Probes the given channels (normally the ones on screen) in the background, a few at a time. */
    void checkChannels(List<CatalogItem> visible){
        List<CatalogItem> copy=new ArrayList<>(visible);
        db.execute(()->sync.checkHealth(copy,channel->{
            Map<String,String> headers=new HashMap<>();JSONObject h=channel.headers;
            if(h!=null)for(Iterator<String> k=h.keys();k.hasNext();){String name=k.next();headers.put(name,h.optString(name));}
            StreamProbe.inspect(new ProviderRepository.Stream(channel.name,channel.url,headers));
        },12));
    }
    Set<String> favorites(){return new HashSet<>(ui.getStringSet("live.favorites",Collections.emptySet()));}
    boolean favorite(String key){return ui.getStringSet("live.favorites",Collections.emptySet()).contains(key);}
    boolean toggleFavorite(String key){
        Set<String> f=favorites();boolean added=f.add(key);if(!added)f.remove(key);
        ui.edit().putStringSet("live.favorites",f).apply();touched(ROW_FAVORITES,HOME_LIVE,LiveTvSource.ROW);return added;
    }

    // ---- personal ---------------------------------------------------------------------------
    private List<CatalogItem> continueWatching(int limit){
        Map<String,?> all=prefs.getAll();List<CatalogItem> out=new ArrayList<>();Map<String,Long> watched=new HashMap<>();
        for(Map.Entry<String,?> e:all.entrySet()){
            if(!e.getKey().startsWith("position.")||!(e.getValue() instanceof Integer)||(Integer)e.getValue()<=0)continue;
            String key=e.getKey().substring(9);Object json=all.get("title."+key);if(json==null)continue;
            try{Library.Title t=Library.Title.from(new JSONObject(json.toString()));CatalogItem c=CatalogItem.of(t);out.add(c);Object at=all.get("watched_at."+key);watched.put(c.key(),at instanceof Long?(Long)at:0L);}catch(Exception ignored){/* skip */}
        }
        out.sort(Comparator.comparingLong((CatalogItem c)->watched.getOrDefault(c.key(),0L)).reversed());
        return out.size()>limit?new ArrayList<>(out.subList(0,limit)):out;
    }
    void watchlist(Consumer<List<CatalogItem>> callback){
        db.execute(()->{
            Map<String,?> all=prefs.getAll();List<CatalogItem> out=new ArrayList<>();
            for(Map.Entry<String,?> e:all.entrySet()){
                if(!e.getKey().startsWith("saved.")||!Boolean.TRUE.equals(e.getValue()))continue;
                Object json=all.get("title."+e.getKey().substring(6));if(json==null)continue;
                try{out.add(CatalogItem.of(Library.Title.from(new JSONObject(json.toString()))));}catch(Exception ignored){/* skip */}
            }
            List<CatalogItem> r=DiscoverQuery.order(out,"newest");main.post(()->callback.accept(r));
        });
    }
    /** Runs any blocking work (legacy migration, prefs scans) off the main thread. */
    void background(Runnable work){db.execute(work);}
    void network(Runnable work){net.execute(work);}

    private synchronized List<LicensedProvider> licensed(){
        if(!prefs.getBoolean("addon."+EinthusanSimulatedProvider.ID,false))return Collections.emptyList();
        if(simulation==null){
            try(InputStream in=app.getAssets().open(EinthusanSimulatedProvider.FIXTURE);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>512*1024)throw new IOException("fixture too large");out.write(b,0,n);}
                simulation=new EinthusanSimulatedProvider(new SimulatedPartnerServer(out.toString(StandardCharsets.UTF_8.name())),EinthusanSimulatedProvider.SIM_TOKEN);
            }catch(Exception e){return Collections.emptyList();}
        }
        return Collections.singletonList(simulation);
    }
}
