package com.astratv.app;

import org.junit.Test;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static com.astratv.app.CatalogTestData.*;

public class CatalogSyncCoordinatorTest {
    private static final long MIN=60_000L;
    private long now=1_000_000_000L;
    private final InMemoryCatalogStorage store=new InMemoryCatalogStorage();
    private final List<String> notified=new ArrayList<>();

    private static final class Direct extends AbstractExecutorService{
        boolean down;
        @Override public void shutdown(){down=true;}
        @Override public List<Runnable> shutdownNow(){down=true;return new ArrayList<>();}
        @Override public boolean isShutdown(){return down;}
        @Override public boolean isTerminated(){return down;}
        @Override public boolean awaitTermination(long t,TimeUnit u){return true;}
        @Override public void execute(Runnable r){if(down)throw new RejectedExecutionException();r.run();}
    }
    private static final class FakeSource implements CatalogSource{
        final String id;boolean ready=true,foreground=true;int calls;Exception failure;List<CatalogItem> items=new ArrayList<>();
        FakeSource(String id){this.id=id;}
        @Override public String id(){return id;}
        @Override public String label(){return id;}
        @Override public long defaultTtlMs(){return 15*MIN;}
        @Override public boolean checkOnForeground(){return foreground;}
        @Override public boolean ready(){return ready;}
        @Override public String notReadyReason(){return "not configured";}
        @Override public List<Batch> fetch(CatalogHttp http)throws Exception{calls++;if(failure!=null)throw failure;return Collections.singletonList(new Batch(id+".row",new ArrayList<>(items),false));}
    }
    private CatalogSyncCoordinator coordinator(){
        CatalogSyncCoordinator c=new CatalogSyncCoordinator(store,null,new Direct(),new Direct(),()->now,null);
        c.addListener((source,rows)->notified.add(source+":"+new TreeSet<>(rows)));
        return c;
    }

    @Test public void firstLaunchSyncsReadySourcesAndSkipsUnconfiguredOnes(){
        CatalogSyncCoordinator c=coordinator();
        FakeSource tmdb=new FakeSource("tmdb");tmdb.items.add(tmdb(1,"A","2025-01-01"));
        FakeSource off=new FakeSource("off");off.ready=false;
        c.register(tmdb);c.register(off);
        assertEquals(1,c.syncDue(true));
        assertEquals(1,store.row("tmdb.row",10).size());
        assertEquals(0,off.calls);
        assertEquals(now,store.state("tmdb").syncedAt);
        assertEquals(Collections.singletonList("tmdb:[tmdb.row]"),notified);
        assertEquals("first sync is the baseline, not 'new'",0,store.row("tmdb.row",1).get(0).firstSeen);
    }
    @Test public void respectsTtlAndForegroundRevalidation(){
        CatalogSyncCoordinator c=coordinator();FakeSource s=new FakeSource("s");s.foreground=false;c.register(s);
        c.syncDue(true);
        now+=5*MIN;
        assertEquals("within TTL and no cheap foreground check",0,c.syncDue(true));
        s.foreground=true;
        assertEquals(1,c.syncDue(true));
        now+=5*MIN;s.foreground=false;
        assertEquals(0,c.syncDue(false));
        now+=10*MIN;
        assertEquals("TTL passed",1,c.syncDue(false));
        assertEquals(3,s.calls);
    }
    @Test public void newTitlesAfterBaselineGetFirstSeenTime(){
        CatalogSyncCoordinator c=coordinator();FakeSource s=new FakeSource("s");s.items.add(tmdb(1,"Old","2024-01-01"));c.register(s);
        c.syncNow("s");
        now+=20*MIN;s.items.add(0,tmdb(2,"New","2026-10-30"));
        c.syncNow("s");
        List<CatalogItem> row=store.row("s.row",10);
        assertEquals(now,row.get(0).firstSeen);
        assertEquals(0,row.get(1).firstSeen);
        assertEquals("tmdb:movie:2",store.recent(CatalogItem.MOVIE,1).get(0).key());
    }
    @Test public void failureKeepsPreviousRowsAndBacksOff(){
        CatalogSyncCoordinator c=coordinator();FakeSource s=new FakeSource("s");s.items.add(tmdb(1,"A","2025-01-01"));c.register(s);
        c.syncNow("s");
        now+=20*MIN;s.failure=new IOException("GET https://x/?api_key=abc123 failed");
        c.syncDue(false);
        assertEquals("cached rows survive",1,store.row("s.row",10).size());
        CatalogFreshnessTracker.State st=store.state("s");
        assertEquals(1,st.failures);
        assertFalse("key is redacted",st.message.contains("abc123"));
        assertTrue(st.nextAttemptAt>now);
        assertEquals("backoff blocks immediate retry",0,c.syncDue(true));
        assertEquals("listeners hear about failures too","s:[]",notified.get(notified.size()-1));
    }
    @Test public void unchangedSyncReportsNoChangedRows(){
        CatalogSyncCoordinator c=coordinator();FakeSource s=new FakeSource("s");s.items.add(tmdb(1,"A","2025-01-01"));c.register(s);
        c.syncNow("s");now+=20*MIN;c.syncNow("s");
        assertEquals("s:[]",notified.get(1));
    }
    @Test public void duplicatesInABatchAreMergedBeforeStoring(){
        CatalogSyncCoordinator c=coordinator();FakeSource s=new FakeSource("s");
        s.items.add(tmdb(5,"RRR","2022-03-25"));s.items.add(tmdb(5,"RRR (Hindi)","2022-03-25"));c.register(s);
        c.syncNow("s");
        assertEquals(1,store.row("s.row",10).size());
    }
    @Test public void describeNeverLeaksKeys(){
        assertEquals("No internet connection",CatalogSyncCoordinator.describe(new java.net.UnknownHostException("api.themoviedb.org")));
        assertEquals("Source rejected the API key (HTTP 401)",CatalogSyncCoordinator.describe(new CatalogHttp.HttpStatusException(401)));
        assertFalse(CatalogSyncCoordinator.describe(new IOException("https://www.thesportsdb.com/api/v1/json/KEY99/eventsday.php")).contains("KEY99"));
    }
    @Test public void healthProbesStoreResultsAndRespectTtl(){
        CatalogSyncCoordinator c=coordinator();
        CatalogItem up=channel("up","Up"),down=channel("down","Down");
        assertEquals(2,c.checkHealth(Arrays.asList(up,down),t->{if(t.id.equals("down"))throw new IOException("offline");},10));
        assertTrue(store.health.get(up.key())[0]>0);
        assertTrue(store.health.get(down.key())[1]>0);
        assertEquals("fresh results are not re-probed",0,c.checkHealth(Arrays.asList(up,down),t->{},10));
        now+=CatalogSyncCoordinator.FAILED_HEALTH_TTL_MS+MIN;
        assertEquals("only the failed channel is retried after an hour",1,c.checkHealth(Arrays.asList(up,down),t->{},10));
        assertTrue(notified.contains(CatalogSyncCoordinator.HEALTH_SOURCE+":["+up.key()+"]"));
    }
    @Test public void healthChecksSkipNonChannelsAndHonourLimit(){
        CatalogSyncCoordinator c=coordinator();
        assertEquals(0,c.checkHealth(Collections.singletonList(tmdb(1,"Film","2020-01-01")),t->{},10));
        assertEquals(1,c.checkHealth(Arrays.asList(channel("a","A"),channel("b","B")),t->{},1));
    }
}
