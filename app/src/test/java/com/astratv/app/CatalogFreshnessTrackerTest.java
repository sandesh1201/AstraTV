package com.astratv.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class CatalogFreshnessTrackerTest {
    private static final long MIN=60_000L,TTL=15*MIN,T0=1_000_000_000L;

    @Test public void neverSyncedSourceIsDue(){
        assertTrue(CatalogFreshnessTracker.due(null,TTL,T0,false));
        assertTrue(CatalogFreshnessTracker.due(new CatalogFreshnessTracker.State(),TTL,T0,false));
    }
    @Test public void freshSourceWaitsForTtlUnlessForegroundCheck(){
        CatalogFreshnessTracker.State s=CatalogFreshnessTracker.succeeded(null,T0,10);
        assertFalse(CatalogFreshnessTracker.due(s,TTL,T0+5*MIN,false));
        assertTrue(CatalogFreshnessTracker.due(s,TTL,T0+5*MIN,true));
        assertTrue(CatalogFreshnessTracker.due(s,TTL,T0+TTL,false));
    }
    @Test public void checksCloserThanMinimumGapAreCoalesced(){
        CatalogFreshnessTracker.State s=CatalogFreshnessTracker.succeeded(null,T0,10);
        assertFalse(CatalogFreshnessTracker.due(s,TTL,T0+10_000,true));
    }
    @Test public void failedSourceWaitsForBackoffEvenOnForeground(){
        CatalogFreshnessTracker.State s=CatalogFreshnessTracker.failed(CatalogFreshnessTracker.succeeded(null,T0,3),T0+TTL,TTL,"timeout");
        assertEquals(1,s.failures);
        assertFalse(CatalogFreshnessTracker.due(s,TTL,T0+TTL+10_000,true));
        assertTrue(CatalogFreshnessTracker.due(s,TTL,s.nextAttemptAt+MIN,true));
        assertEquals("previous sync time is kept",T0,s.syncedAt);
    }
    @Test public void backoffDoublesAndIsCapped(){
        assertEquals(30_000L,CatalogFreshnessTracker.backoff(1,TTL));
        assertEquals(60_000L,CatalogFreshnessTracker.backoff(2,TTL));
        assertEquals(120_000L,CatalogFreshnessTracker.backoff(3,TTL));
        assertEquals(TTL,CatalogFreshnessTracker.backoff(9,TTL));
        assertEquals(30*MIN,CatalogFreshnessTracker.backoff(20,24*60*MIN));
        assertEquals(30_000L,CatalogFreshnessTracker.backoff(5,1000));
    }
    @Test public void successClearsFailures(){
        CatalogFreshnessTracker.State failed=CatalogFreshnessTracker.failed(null,T0,TTL,"x");
        CatalogFreshnessTracker.State ok=CatalogFreshnessTracker.succeeded(failed,T0+MIN,7);
        assertEquals(0,ok.failures);assertEquals(0,ok.nextAttemptAt);assertEquals(7,ok.items);assertEquals("",ok.message);
    }
    @Test public void labelsDescribeFreshnessHonestly(){
        assertEquals("Not loaded yet",CatalogFreshnessTracker.label(null,TTL,T0));
        CatalogFreshnessTracker.State s=CatalogFreshnessTracker.succeeded(null,T0,1);
        assertEquals("Updated just now",CatalogFreshnessTracker.label(s,TTL,T0+10_000));
        assertEquals("Updated 5 min ago",CatalogFreshnessTracker.label(s,TTL,T0+5*MIN));
        assertTrue(CatalogFreshnessTracker.label(s,TTL,T0+3*TTL).contains("refresh pending"));
        CatalogFreshnessTracker.State f=CatalogFreshnessTracker.failed(s,T0+20*MIN,TTL,"No internet connection");
        assertTrue(CatalogFreshnessTracker.label(f,TTL,T0+20*MIN).startsWith("Showing saved catalog from 20 min ago"));
        assertTrue(CatalogFreshnessTracker.stale(f,TTL,T0+20*MIN));
    }
}
