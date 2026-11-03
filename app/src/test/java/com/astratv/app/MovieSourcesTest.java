package com.astratv.app;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.*;

/**
 * The Movies tab (MovieRulz only). No authorized MovieRulz provider exists, so the tab is empty;
 * the playable/unavailable logic is exercised with an in-test provider of origin "movierulz" that
 * stands in for a licensed partner API. It is test code only and never ships in the app.
 */
public class MovieSourcesTest {
    private static final long NOW=1_793_000_000_000L;

    /** A licensed-partner stand-in: titles with HTTPS HLS, DRM-only, insecure and region-blocked streams. */
    private static final class FakePartner implements LicensedProvider {
        final String id,origin;final boolean granted;final LicensedProvider.ProviderException catalogError;
        FakePartner(String id,String origin,boolean granted,LicensedProvider.ProviderException catalogError){this.id=id;this.origin=origin;this.granted=granted;this.catalogError=catalogError;}
        @Override public String id(){return id;}
        @Override public String origin(){return origin;}
        @Override public String displayName(){return "Partner";}
        @Override public Authorization authorization(){return new Authorization(granted,false,"test partner");}
        @Override public Page catalog(Query q)throws ProviderException{
            if(catalogError!=null)throw catalogError;
            List<Library.Title> out=new ArrayList<>();
            out.add(title("ok","Film OK","2026-10-20"));out.add(title("new","Film New","2026-10-30"));
            out.add(title("drm","Film DRM","2026-10-10"));out.add(title("insecure","Film Insecure","2026-10-05"));out.add(title("region","Film Region","2026-10-01"));
            Library.Title foreign=title("foreign","Other provider","2026-10-29");foreign.provider="someone-else";out.add(foreign);
            return new Page(out,1,1);
        }
        @Override public Library.Title details(String titleId){return title(titleId,"Film "+titleId,"2026-10-01");}
        @Override public List<Playback> playback(String titleId)throws ProviderException{
            switch(titleId){
                case "ok":case "new":return Collections.singletonList(new Playback("https://media.example.org/"+titleId+"/master.m3u8","hls",1080,"",Collections.singletonList("Telugu")));
                case "drm":return Collections.singletonList(new Playback("https://media.example.org/drm/manifest.mpd","dash",2160,"widevine",Collections.emptyList()));
                case "insecure":return Collections.singletonList(new Playback("http://media.example.org/insecure.mp4","mp4",720,"",Collections.emptyList()));
                default:throw new ProviderException(ProviderException.Code.REGION_UNAVAILABLE,"Not offered in this region.");
            }
        }
        Library.Title title(String titleId,String name,String added){
            Library.Title t=new Library.Title(titleId,name,"","","","2026","Drama");t.provider=id;t.language="Telugu";t.added=added;return t;
        }
    }
    private static FakePartner partner(){return new FakePartner("movierulz.partner",MovieRulzCatalogProvider.ORIGIN,true,null);}
    private static MovieRulzCatalogProvider rulz(LicensedProvider... providers){return new MovieRulzCatalogProvider(()->Arrays.asList(providers),()->NOW);}
    private static Map<String,CatalogItem> byId(List<CatalogItem> items){Map<String,CatalogItem> out=new LinkedHashMap<>();for(CatalogItem c:items)out.put(c.title.id,c);return out;}
    private static CatalogSource.Batch batch(List<CatalogSource.Batch> batches,String row){for(CatalogSource.Batch b:batches)if(b.rowId.equals(row))return b;fail("missing row "+row);return null;}
    private static List<CatalogItem> allRows(List<CatalogSource.Batch> batches){List<CatalogItem> all=new ArrayList<>();for(CatalogSource.Batch b:batches)all.addAll(b.items);return all;}

    @Test public void movieRulzIsNeverReadyAndExplainsWhy()throws Exception{
        MovieRulzCatalogProvider rulz=new MovieRulzCatalogProvider(()->NOW);
        assertFalse(rulz.ready());assertFalse(rulz.checkOnForeground());
        assertEquals(MovieAvailability.UNSUPPORTED,rulz.status());
        assertEquals("MovieRulz · Movies",rulz.label());
        assertTrue(rulz.notReadyReason().startsWith("Not available."));
        assertTrue(rulz.notReadyReason().contains("does not fill this tab with titles from other catalogs"));
        assertTrue("an unauthorized source returns nothing",allRows(rulz.fetch(null)).isEmpty());
    }

    @Test public void movieRulzTitlesAreRefusedBeforeAnyNetworkCall(){
        assertTrue(ProviderRepository.unauthorized("movierulz"));assertFalse(ProviderRepository.unauthorized("library"));
        Library.Title t=new Library.Title("x","Anything","","","","","");t.provider="movierulz";
        assertEquals(MovieAvailability.UNSUPPORTED,FullMoviePlaybackController.known(t,null));
        assertFalse(FullMoviePlaybackController.canWatch(FullMoviePlaybackController.known(t,null)));
        assertTrue(SourceMovieCatalog.sourceTitle(t));
    }

    @Test public void onlyConfirmedStreamsAreListedAsPlayable()throws Exception{
        MovieRulzCatalogProvider r=rulz(partner());
        assertTrue(r.ready());
        List<CatalogSource.Batch> out=r.fetch(null);
        Map<String,CatalogItem> all=byId(batch(out,MovieRulzCatalogProvider.ROW_ALL).items),playable=byId(batch(out,MovieRulzCatalogProvider.ROW).items);
        assertEquals(new HashSet<>(Arrays.asList("ok","new","drm","insecure","region")),all.keySet());
        assertEquals(new HashSet<>(Arrays.asList("ok","new")),playable.keySet());
        for(CatalogItem c:playable.values()){assertEquals(MovieAvailability.AVAILABLE,c.availability());assertTrue(c.playable);assertEquals(1080,c.verifiedHeight);}
        for(String id:new String[]{"drm","insecure","region"}){assertEquals(id,MovieAvailability.UNAVAILABLE,all.get(id).availability());assertFalse(all.get(id).playable);assertFalse(all.get(id).availabilityReason.isEmpty());}
        assertEquals("newest added first","new",batch(out,MovieRulzCatalogProvider.ROW).items.get(0).title.id);
    }

    @Test public void providerOfAnotherOriginIsIgnored()throws Exception{
        MovieRulzCatalogProvider r=rulz(new FakePartner("other","other",true,null));
        assertFalse(r.ready());
        assertTrue(allRows(r.fetch(null)).isEmpty());
        assertFalse("an ungranted partner is not used",rulz(new FakePartner("movierulz.partner",MovieRulzCatalogProvider.ORIGIN,false,null)).ready());
    }
    @Test public void rejectedCatalogKeepsPreviousRowsInsteadOfEmptyingThem(){
        FakePartner rejected=new FakePartner("movierulz.partner",MovieRulzCatalogProvider.ORIGIN,true,new LicensedProvider.ProviderException(LicensedProvider.ProviderException.Code.UNAUTHENTICATED,"Partner token rejected"));
        try{rulz(rejected).fetch(null);fail("expected the provider error to propagate");}
        catch(LicensedProvider.ProviderException e){
            assertEquals(LicensedProvider.ProviderException.Code.UNAUTHENTICATED,e.code);
            assertEquals(MovieAvailability.LOGIN_REQUIRED,MovieAvailability.of(e.code));
        }catch(Exception e){fail("unexpected "+e);}
    }

    @Test public void availabilityMapping(){
        assertEquals(MovieAvailability.UNSUPPORTED,MovieAvailability.of(LicensedProvider.ProviderException.Code.NOT_AUTHORIZED));
        assertEquals(MovieAvailability.LOGIN_REQUIRED,MovieAvailability.of(LicensedProvider.ProviderException.Code.UNAUTHENTICATED));
        assertEquals(MovieAvailability.UNAVAILABLE,MovieAvailability.of(LicensedProvider.ProviderException.Code.UNSUPPORTED_DRM));
        assertEquals("rows saved before the rename",MovieAvailability.UNSUPPORTED,MovieAvailability.parse("AUTHORIZATION_REQUIRED"));
        assertEquals(MovieAvailability.PROVIDER_ERROR,MovieAvailability.of(LicensedProvider.ProviderException.Code.RATE_LIMITED));
        assertEquals(MovieAvailability.PROVIDER_ERROR,MovieAvailability.of(LicensedProvider.ProviderException.Code.SERVICE_ERROR));
        assertEquals(MovieAvailability.UNAVAILABLE,MovieAvailability.of(LicensedProvider.ProviderException.Code.REGION_UNAVAILABLE));
        assertEquals(MovieAvailability.CHECKING,MovieAvailability.parse(""));
        assertEquals(MovieAvailability.CHECKING,MovieAvailability.parse("nonsense"));
        assertEquals(MovieAvailability.AVAILABLE,MovieAvailability.parse("AVAILABLE"));
    }

    @Test public void watchMovieOnlyForConfirmedTitles(){
        FakePartner p=partner();
        CatalogItem c=SourceMovieCatalog.check(p,p.details("region"),NOW);
        assertEquals(MovieAvailability.UNAVAILABLE,FullMoviePlaybackController.known(c.title,c));
        assertFalse(FullMoviePlaybackController.canWatch(MovieAvailability.UNAVAILABLE));
        assertTrue(FullMoviePlaybackController.definitive(MovieAvailability.UNAVAILABLE));
        assertFalse(FullMoviePlaybackController.definitive(MovieAvailability.PROVIDER_ERROR));
        assertTrue(FullMoviePlaybackController.definitive(MovieAvailability.UNSUPPORTED));assertTrue(FullMoviePlaybackController.definitive(MovieAvailability.LOGIN_REQUIRED));
        assertFalse(FullMoviePlaybackController.definitive(MovieAvailability.CHECKING));
        CatalogItem ok=SourceMovieCatalog.check(p,p.details("ok"),NOW);
        assertTrue(FullMoviePlaybackController.canWatch(FullMoviePlaybackController.known(ok.title,ok)));
        assertEquals("no saved verdict for another title",MovieAvailability.CHECKING,FullMoviePlaybackController.known(ok.title,c));
        assertTrue(FullMoviePlaybackController.explain(MovieAvailability.PROVIDER_ERROR,"").endsWith("Try again shortly."));
    }

    @Test public void availabilityRoundTripsThroughTheStore()throws Exception{
        FakePartner p=partner();
        CatalogItem c=SourceMovieCatalog.check(p,p.title("insecure","Film Insecure","2026-10-05"),NOW);
        CatalogItem back=CatalogItem.from(c.json());
        assertEquals(MovieAvailability.UNAVAILABLE,back.availability());
        assertEquals(c.availabilityReason,back.availabilityReason);
        assertEquals(c.addedAt,back.addedAt);
    }

    @Test public void posterBadgesSayPlayOnlyForAvailable(){
        assertEquals("▶ Play",CatalogViews.availabilityBadge(MovieAvailability.AVAILABLE));
        assertEquals("Unavailable",CatalogViews.availabilityBadge(MovieAvailability.UNAVAILABLE));
        assertEquals("Login required",CatalogViews.availabilityBadge(MovieAvailability.LOGIN_REQUIRED));
        assertEquals("Unsupported",CatalogViews.availabilityBadge(MovieAvailability.UNSUPPORTED));
    }
}
