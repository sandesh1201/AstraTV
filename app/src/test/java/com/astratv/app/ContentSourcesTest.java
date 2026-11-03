package com.astratv.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Tab-to-source mapping, the Catch-Up serial providers, Movies filters and provider diagnostics. */
public class ContentSourcesTest {
    private static final long NOW=1_793_750_400_000L,HOUR=3_600_000L,DAY=24*HOUR;

    private static CatchupEpisode episode(String provider,String id,String series,int number,long aired){
        CatchupEpisode e=new CatchupEpisode();e.providerId=provider;e.episodeId=id;e.seriesTitle=series;e.seriesId=CatchupEpisode.seriesId(provider,series);
        e.episodeTitle=series+" | Episode "+number;e.episodeNumber=number;e.airDate=aired;e.playbackType=CatalogItem.APP_HANDOFF;e.playbackReference="partner.app";
        return e;
    }
    private static CatalogSource.Batch row(List<CatalogSource.Batch> batches,String id){for(CatalogSource.Batch b:batches)if(b.rowId.equals(id))return b;fail("missing "+id);return null;}

    @Test public void serialProvidersWithoutAFeedListNothing(){
        for(SerialCatchupProvider p:new SerialCatchupProvider[]{new TamilTvSerialProvider(null),new TeluguStreamProvider(null)}){
            assertFalse(p.ready());assertFalse(p.checkOnForeground());
            assertTrue(p.notReadyReason().contains("videos are not played in Saandio"));
            assertTrue(p.notReadyReason().contains("broadcaster's official app"));
            try{p.fetch(null);fail("a not-ready provider must not fetch");}catch(Exception expected){assertEquals(p.notReadyReason(),expected.getMessage());}
        }
        assertEquals(TamilTvSerialProvider.ID,CatalogRepository.sourceFor(new TamilTvSerialProvider(null).latestRow()));
        assertEquals(TeluguStreamProvider.ID,CatalogRepository.sourceFor(new TeluguStreamProvider(null).serialsRow()));
        assertNull(CatalogRepository.sourceFor(SerialCatchupProvider.ROW_RECENT));
        assertNull(CatalogRepository.sourceFor(SerialCatchupProvider.ROW_CONTINUE));
    }

    @Test public void legacyScrapersForTheSameSitesAreBlocked(){
        assertTrue(ProviderRepository.unauthorized("tamil"));assertTrue(ProviderRepository.unauthorized("telugu"));
        assertEquals(TamilTvSerialProvider.REASON,ProviderRepository.unauthorizedReason("tamil"));
        assertEquals(TeluguStreamProvider.REASON,ProviderRepository.unauthorizedReason("telugu"));
        assertFalse(ProviderRepository.unauthorized("news"));assertFalse(ProviderRepository.unauthorized("library"));
        Library.Title t=new Library.Title("x","Serial","","","","","");t.provider="telugu";
        assertEquals(MovieAvailability.UNSUPPORTED,FullMoviePlaybackController.known(t,null));
    }

    @Test public void feedRowsAreNewestFirstWithOneTilePerSeries(){
        TeluguStreamProvider p=new TeluguStreamProvider((http,known)->Collections.emptyList());
        assertTrue(p.ready());
        List<CatchupEpisode> feed=Arrays.asList(
            episode("telugustream","a1","Serial A",10,NOW-3*DAY),
            episode("telugustream","a2","Serial A",11,NOW-DAY),
            episode("telugustream","b1","Serial B",5,NOW-2*DAY),
            episode("telugustream","a2","Serial A",11,NOW-DAY),
            episode("tamiltvserial","t1","Other provider",1,NOW-HOUR),
            episode("telugustream","f1","Future",1,NOW+DAY),
            episode("telugustream","n1","No date",1,0));
        List<CatalogSource.Batch> out=p.batches(feed,NOW);
        List<CatalogItem> latest=row(out,p.latestRow()).items,serials=row(out,p.serialsRow()).items;
        assertEquals(Arrays.asList("a2","b1","a1"),ids(latest));
        assertEquals("one tile per series, carrying its newest episode",Arrays.asList("a2","b1"),ids(serials));
        CatalogItem first=latest.get(0);
        assertEquals("telugustream",first.title.provider);assertEquals("TeluguStream",first.title.providerName);assertEquals("Telugu",first.title.language);
        assertEquals(CatalogItem.CATCHUP,first.kind);assertEquals(11,first.episode.episodeNumber);assertEquals("partner.app",first.playbackReference);
    }
    private static List<String> ids(List<CatalogItem> items){List<String> out=new ArrayList<>();for(CatalogItem c:items)out.add(c.episode.episodeId);return out;}

    @Test public void recentlyAiredMergesBothProvidersAndContinueWatchingKeepsOnlyThem(){
        TamilTvSerialProvider tamil=new TamilTvSerialProvider((http,known)->Collections.emptyList());TeluguStreamProvider telugu=new TeluguStreamProvider((http,known)->Collections.emptyList());
        List<CatalogItem> ta=row(tamil.batches(Arrays.asList(episode("tamiltvserial","t1","Tamil A",3,NOW-2*HOUR),episode("tamiltvserial","t0","Tamil A",2,NOW-9*DAY)),NOW),tamil.latestRow()).items;
        List<CatalogItem> te=row(telugu.batches(Collections.singletonList(episode("telugustream","e1","Telugu A",7,NOW-HOUR)),NOW),telugu.latestRow()).items;
        List<CatalogItem> recent=SerialCatchupProvider.recent(Arrays.asList(ta,te),NOW,10);
        assertEquals("newest first, older than seven days left out",Arrays.asList("e1","t1"),ids(recent));
        CatalogItem other=CatchupSource.item(episode("catchup.dd","d1","DD Serial",1,NOW-HOUR));
        assertEquals(Arrays.asList("e1"),ids(SerialCatchupProvider.mine(Arrays.asList(other,te.get(0)),10)));
    }

    @Test public void officialArchiveRowsAreOffByDefault(){
        assertFalse("Catch-Up TV lists TamilTVSerial and TeluguStream only unless a developer turns archives on",
            FeatureFlags.effective(FeatureFlags.Flag.OFFICIAL_ARCHIVES,f->f.buildDefault));
    }

    @Test public void movieFiltersOfferOnlyValuesTheProviderListed(){
        List<CatalogItem> items=new ArrayList<>();
        items.add(movie("1","Telugu","2026","Action · Drama"));items.add(movie("2","Tamil","2024","Comedy"));
        items.add(movie("3","Gujarati","2025","Drama"));items.add(movie("4","Telugu","","Action"));
        CatalogRepository.Facets f=CatalogRepository.Facets.of(items);
        assertEquals(Arrays.asList(2026,2025,2024),f.years);
        assertTrue(f.genres.containsAll(Arrays.asList("Action","Comedy","Drama")));
        assertEquals("languages beyond the eight Indian defaults",Collections.singletonList("Gujarati"),f.languages);
        DiscoverQuery q=new DiscoverQuery();q.year=2026;
        assertTrue(q.matches(items.get(0)));assertFalse(q.matches(items.get(1)));
        q=new DiscoverQuery();q.genreName="Comedy";
        assertTrue(q.matches(items.get(1)));assertFalse(q.matches(items.get(0)));
        q=new DiscoverQuery();q.languageCode="Telugu";
        assertTrue(q.matches(items.get(3)));assertFalse(q.matches(items.get(2)));
        q=new DiscoverQuery();q.languageCode="Gujarati";
        assertTrue(q.matches(items.get(2)));assertFalse(q.matches(items.get(0)));
        q=new DiscoverQuery();q.minHeight=1080;
        assertFalse("no quality label without a verified rendition",q.matches(items.get(0)));
    }
    private static CatalogItem movie(String id,String lang,String year,String genre){
        Library.Title t=new Library.Title(id,"Film "+id,"","","",year,genre);t.provider="movierulz";t.language=lang;
        CatalogItem c=CatalogItem.of(t);c.kind=CatalogItem.MOVIE;return c;
    }

    @Test public void diagnosticsDistinguishProviderProblems(){
        assertEquals(Diagnostics.Problem.SESSION_EXPIRED,Diagnostics.ofCatalog(new LicensedProvider.ProviderException(LicensedProvider.ProviderException.Code.UNAUTHENTICATED,"expired")));
        assertEquals(Diagnostics.Problem.AUTH_FAILED,Diagnostics.ofCatalog(new LicensedProvider.ProviderException(LicensedProvider.ProviderException.Code.NOT_AUTHORIZED,"no")));
        assertEquals(Diagnostics.Problem.CATALOG_REQUEST_FAILED,Diagnostics.ofCatalog(new LicensedProvider.ProviderException(LicensedProvider.ProviderException.Code.MALFORMED,"bad")));
        assertEquals(Diagnostics.Problem.NETWORK,Diagnostics.ofCatalog(new java.net.SocketTimeoutException("slow")));
        assertEquals(Diagnostics.Problem.CATALOG_REQUEST_FAILED,Diagnostics.ofCatalog(new IllegalStateException("parse")));
        assertEquals(Diagnostics.Problem.PLAYBACK_UNAVAILABLE,Diagnostics.ofAvailability(MovieAvailability.UNAVAILABLE));
        assertEquals(Diagnostics.Problem.UNSUPPORTED_INTEGRATION,Diagnostics.ofAvailability(MovieAvailability.UNSUPPORTED));
        assertEquals(Diagnostics.Problem.SESSION_EXPIRED,Diagnostics.ofAvailability(MovieAvailability.LOGIN_REQUIRED));
        assertNull(Diagnostics.ofAvailability(MovieAvailability.AVAILABLE));
        assertEquals(Diagnostics.Problem.MEDIA3_INIT,Diagnostics.ofPlayback(PlaybackErrors.Category.DECODER,4001));
        assertEquals(Diagnostics.Problem.DECODER,Diagnostics.ofPlayback(PlaybackErrors.Category.DECODER,4003));
        assertEquals(Diagnostics.Problem.NETWORK,Diagnostics.ofPlayback(PlaybackErrors.Category.NETWORK,2001));
        assertEquals(Diagnostics.Problem.SESSION_EXPIRED,Diagnostics.ofPlayback(PlaybackErrors.Category.EXPIRED_URL,2004));
        assertNull(Diagnostics.ofPlayback(PlaybackErrors.Category.BEHIND_LIVE,1002));
        Diagnostics d=Diagnostics.get();int before=d.problemCount(Diagnostics.Problem.UNSUPPORTED_INTEGRATION);
        d.unsupported("test.source","no API");d.unsupported("test.source","no API");
        assertEquals("reported once per session",before+1,d.problemCount(Diagnostics.Problem.UNSUPPORTED_INTEGRATION));
        d.provider(Diagnostics.Problem.AUTH_FAILED,"p","https://cdn.example/x.m3u8?token=secret");
        assertFalse(d.providerEvents().get(0).detail.contains("secret"));
    }
}
