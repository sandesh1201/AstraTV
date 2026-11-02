package com.astratv.app;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import org.json.*;
import org.junit.*;

public class EinthusanContractTest {
    private static String fixture;
    private SimulatedPartnerServer server;
    private EinthusanSimulatedProvider provider;

    @BeforeClass public static void load()throws Exception{
        fixture=new String(Files.readAllBytes(Paths.get("src/main/assets",EinthusanSimulatedProvider.FIXTURE)),StandardCharsets.UTF_8);
    }
    @Before public void setUp()throws Exception{
        server=new SimulatedPartnerServer(fixture);
        provider=new EinthusanSimulatedProvider(server,EinthusanSimulatedProvider.SIM_TOKEN);
    }
    private static LicensedProvider.ProviderException.Code code(ThrowingRunnable r){
        try{r.run();fail("expected ProviderException");return null;}catch(LicensedProvider.ProviderException e){return e.code;}
    }
    interface ThrowingRunnable{void run()throws LicensedProvider.ProviderException;}

    @Test public void simulationIsLabelledAndNeverClaimsRealAuthorization(){
        LicensedProvider.Authorization a=provider.authorization();
        assertTrue(a.simulated);assertTrue(a.basis.contains("No Einthusan content"));
        assertFalse(EinthusanAdapter.status().available);
    }
    @Test public void catalogPaginates()throws Exception{
        LicensedProvider.Page first=provider.catalog(new LicensedProvider.Query("","",1,3));
        assertEquals(3,first.titles.size());assertEquals(3,first.totalPages);assertTrue(first.hasMore());
        LicensedProvider.Page last=provider.catalog(new LicensedProvider.Query("","",3,3));
        assertEquals(1,last.titles.size());assertFalse(last.hasMore());
    }
    @Test public void languageFilterAndMapping()throws Exception{
        List<Library.Title> te=provider.catalog(new LicensedProvider.Query("te","",1,20)).titles;
        assertEquals(1,te.size());assertEquals("Telugu",te.get(0).language);assertEquals(EinthusanSimulatedProvider.ID,te.get(0).provider);
        assertTrue(te.get(0).rating.isEmpty());
        assertEquals("Hindi",provider.catalog(new LicensedProvider.Query("hi","",1,20)).titles.get(0).language);
    }
    @Test public void searchAndDetails()throws Exception{
        List<Library.Title> found=provider.catalog(new LicensedProvider.Query("","sintel",1,20)).titles;
        assertEquals(1,found.size());
        Library.Title d=provider.details("sim-sintel");
        assertTrue(d.cast.contains("Colin Levy"));assertTrue(d.genre.contains("Fantasy"));assertEquals("2010-09-27",d.released);
    }
    @Test public void declaredHeightDrivesQualityLabel()throws Exception{
        List<LicensedProvider.Playback> bbb=provider.playback("sim-bbb");
        assertEquals("1080p",bbb.get(0).quality);assertEquals("hls",bbb.get(0).protocol);
    }
    @Test public void unknownHeightIsNeverLabelledHd()throws Exception{
        Library.Title t=provider.withPlayback(provider.details("sim-sintel"));
        String name=t.videos.getJSONObject(0).getString("name");
        assertFalse(name.contains("HD"));assertFalse(name.matches(".*\\d+p.*"));assertTrue(Library.https(t.url));
    }
    @Test public void drmOnlyTitleIsRefused(){
        assertEquals(LicensedProvider.ProviderException.Code.UNSUPPORTED_DRM,code(()->provider.playback("sim-drm-only")));
    }
    @Test public void regionRestrictionIsReportedNotBypassed(){
        assertEquals(LicensedProvider.ProviderException.Code.REGION_UNAVAILABLE,code(()->provider.playback("sim-region")));
    }
    @Test public void insecureStreamsAreDropped(){
        assertEquals(LicensedProvider.ProviderException.Code.UNAVAILABLE,code(()->provider.playback("sim-insecure")));
    }
    @Test public void authAndRateLimitErrorsAreTyped(){
        EinthusanSimulatedProvider wrong=new EinthusanSimulatedProvider(server,"bad-token");
        assertEquals(LicensedProvider.ProviderException.Code.UNAUTHENTICATED,code(()->wrong.catalog(new LicensedProvider.Query("","",1,5))));
        server.fault("/v1/catalog",429,"rate_limited");
        assertEquals(LicensedProvider.ProviderException.Code.RATE_LIMITED,code(()->provider.catalog(new LicensedProvider.Query("","",1,5))));
        server.fault("/v1/titles/",403,"forbidden");
        assertEquals(LicensedProvider.ProviderException.Code.NOT_AUTHORIZED,code(()->provider.details("sim-bbb")));
    }
    @Test public void malformedAndMissingResponses(){
        EinthusanSimulatedProvider broken=new EinthusanSimulatedProvider((p,t)->new EinthusanSimulatedProvider.Response(200,"<html>"),"x");
        assertEquals(LicensedProvider.ProviderException.Code.MALFORMED,code(()->broken.catalog(new LicensedProvider.Query("","",1,5))));
        EinthusanSimulatedProvider noItems=new EinthusanSimulatedProvider((p,t)->new EinthusanSimulatedProvider.Response(200,"{}"),"x");
        assertEquals(LicensedProvider.ProviderException.Code.MALFORMED,code(()->noItems.catalog(new LicensedProvider.Query("","",1,5))));
        assertEquals(LicensedProvider.ProviderException.Code.NOT_FOUND,code(()->provider.details("missing")));
    }
    @Test public void fixtureUsesNoEinthusanEndpointsAndOnlyOpenLicensedMedia()throws Exception{
        JSONArray titles=new JSONObject(fixture).getJSONArray("titles");
        for(int i=0;i<titles.length();i++){
            JSONObject t=titles.getJSONObject(i);
            assertFalse(t.toString().toLowerCase().contains("einthusan.tv"));
            String license=t.getString("license");
            assertTrue(license.startsWith("CC BY")||license.equals("Fixture only"));
        }
    }
}
