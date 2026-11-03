package com.astratv.app;

import org.junit.Test;
import static org.junit.Assert.*;

/** Artwork failure memory, diagnostics redaction, channel retirement and regional row checks. */
public class CatalogReliabilityTest {
    private static final long NOW=1_800_000_000_000L;

    @Test public void artworkUrlsBuiltFromMissingValuesAreRejected(){
        assertTrue(ArtworkFailures.validUrl("https://image.tmdb.org/t/p/w342/abc.jpg"));
        assertTrue(ArtworkFailures.validUrl("https://commons.wikimedia.org/wiki/Special:FilePath/Poster.jpg?width=342"));
        assertFalse(ArtworkFailures.validUrl("https://image.tmdb.org/t/p/w342null"));
        assertFalse(ArtworkFailures.validUrl("https://image.tmdb.org/t/p/w342"));
        assertFalse(ArtworkFailures.validUrl("https://image.tmdb.org/t/p/w342/"));
        assertFalse(ArtworkFailures.validUrl("http://example.org/a.jpg"));
        assertFalse(ArtworkFailures.validUrl(""));
        assertFalse(ArtworkFailures.validUrl(null));
    }
    @Test public void brokenArtworkIsSkippedForAWhileThenRetried(){
        ArtworkFailures f=new ArtworkFailures();
        f.failed("https://a/x.jpg",true,NOW);f.failed("https://a/y.jpg",false,NOW);
        assertTrue(f.known("https://a/x.jpg",NOW+ArtworkFailures.TRANSIENT_MS+1));
        assertFalse("transient failures expire sooner",f.known("https://a/y.jpg",NOW+ArtworkFailures.TRANSIENT_MS+1));
        assertFalse(f.known("https://a/x.jpg",NOW+ArtworkFailures.DEFINITIVE_MS+1));
        assertTrue(ArtworkFailures.definitive(404));assertFalse(ArtworkFailures.definitive(429));assertFalse(ArtworkFailures.definitive(503));assertFalse(ArtworkFailures.definitive(408));
        assertTrue(ArtworkFailures.retryDelayMs(0)>0);assertTrue(ArtworkFailures.retryDelayMs(1)>ArtworkFailures.retryDelayMs(0));assertEquals(-1,ArtworkFailures.retryDelayMs(2));
    }
    @Test public void brokenArtworkMemoryIsBounded(){
        ArtworkFailures f=new ArtworkFailures();
        for(int i=0;i<ArtworkFailures.MAX_ENTRIES+50;i++)f.failed("https://a/"+i+".jpg",true,NOW);
        assertEquals(ArtworkFailures.MAX_ENTRIES,f.size());
    }
    @Test public void diagnosticsNeverKeepSecretsOrSignedLinks(){
        String r=Diagnostics.redact("GET https://cdn.example/path/master.m3u8?token=abc123&Expires=1&Signature=zz failed; api_key=SECRETKEY Authorization: Bearer eyJhbGciOi.payload.sig");
        assertFalse(r,r.contains("abc123"));assertFalse(r,r.contains("SECRETKEY"));assertFalse(r,r.contains("eyJhbGciOi"));assertFalse(r,r.contains("master.m3u8"));
        assertTrue(r,r.contains("https://cdn.example/…"));
        Diagnostics d=new Diagnostics();
        for(int i=0;i<Diagnostics.HISTORY+5;i++)d.metadataFailure("tmdb.movies","Source returned HTTP 503",i%2==0);
        assertEquals(Diagnostics.HISTORY,d.metadataEvents().size());
        assertEquals(Diagnostics.HISTORY+5,d.metadataFailures.get());
        d.playbackError("partner",PlaybackErrors.Category.EXPIRED_URL,"ERROR_CODE_IO_BAD_HTTP_STATUS",403);
        assertTrue(d.playbackEvents().get(0).detail.contains("HTTP 403"));
    }
    @Test public void channelsLeaveTheListingOnlyAfterRepeatedFailures(){
        long day=24L*60*60_000L;
        assertFalse(CatalogSyncCoordinator.retired(NOW-day,5,NOW));
        assertFalse(CatalogSyncCoordinator.retired(0,2,NOW));
        assertTrue(CatalogSyncCoordinator.retired(0,3,NOW));
        assertTrue(CatalogSyncCoordinator.retired(NOW-4*day,3,NOW));
        assertFalse("retired channels are re-checked daily, not hourly",CatalogSyncCoordinator.healthExpired(new long[]{0,NOW-2*60*60_000L,4},NOW));
        assertTrue(CatalogSyncCoordinator.healthExpired(new long[]{0,NOW-25*60*60_000L,4},NOW));
        assertTrue(CatalogSyncCoordinator.healthExpired(new long[]{0,NOW-2*60*60_000L,1},NOW));
    }
    @Test public void regionalRowsKeepOnlyTitlesInTheirLanguage(){
        assertEquals("te",TmdbCatalogSource.expectedLanguage(TmdbCatalogSource.latestRow("te")));
        assertEquals("hi",TmdbCatalogSource.expectedLanguage(TmdbCatalogSource.serialRow("hi")));
        assertEquals("",TmdbCatalogSource.expectedLanguage(TmdbCatalogSource.ROW_TRENDING));
        CatalogItem c=new CatalogItem(new Library.Title("1","X","","","","",""));c.languageCode="ta";
        assertFalse(TmdbCatalogSource.languageMatches("te",c));
        assertTrue(TmdbCatalogSource.languageMatches("ta",c));
        assertTrue(TmdbCatalogSource.languageMatches("",c));
    }
}
