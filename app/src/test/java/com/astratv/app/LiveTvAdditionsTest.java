package com.astratv.app;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import org.junit.Test;

/** Sun Network entries, channel states, official handoff, feature flags and the programme guide. */
public class LiveTvAdditionsTest {
    private static CatalogItem directoryChannel(String tvgId,String name,String language){
        Library.Title t=new Library.Title(tvgId+"~"+language,name,"","https://example.org/"+tvgId+".m3u8","","LIVE","Entertainment");
        t.provider="news";t.type="tv";t.language=language;t.group="Entertainment";
        CatalogItem c=new CatalogItem(t);c.kind=CatalogItem.CHANNEL;return c;
    }

    // ---- Sun Network ---------------------------------------------------------------------------
    @Test public void sunNetworkListsAllThirtySevenOfficialChannelsWithoutStreams(){
        List<CatalogItem> sun=new SunNetworkChannels().channels();
        assertEquals(37,sun.size());
        Set<String> keys=new HashSet<>();
        for(CatalogItem c:sun){
            assertTrue(keys.add(c.key()));
            assertEquals(CatalogItem.CHANNEL,c.kind);assertFalse(c.playable);
            assertEquals("no stream URL is ever attached","",c.title.url);
            assertTrue(c.handoff());
            if(c.title.name.equals("Sun News")){assertEquals(CatalogItem.YOUTUBE_HANDOFF,c.playbackType);assertEquals(ChannelState.UNKNOWN,ChannelState.of(c,false,0));}
            else{assertEquals(OfficialAppHandoff.SUN_NXT,c.playbackReference);assertEquals(ChannelState.SUBSCRIPTION_REQUIRED,ChannelState.of(c,false,0));}
        }
        Map<String,Integer> perLanguage=new TreeMap<>();for(CatalogItem c:sun)perLanguage.merge(c.title.language,1,Integer::sum);
        assertEquals("{Bengali=2, Hindi=2, Kannada=6, Malayalam=6, Marathi=2, Tamil=10, Telugu=9}",perLanguage.toString());
    }
    @Test public void dedupMatchesRenamedIptvIdsAndNamesButNeverReplacesExisting(){
        CatalogItem udaya=directoryChannel("SunUdaya.in","Sun Udaya","Kannada"),gemini=directoryChannel("GeminiTV.in","Gemini TV","Telugu"),kids=directoryChannel("Chintu.in","Chintu TV","Kannada");
        List<CatalogItem> existing=Arrays.asList(udaya,gemini,kids);
        ChannelDeduplicationService.Result r=ChannelDeduplicationService.merge(existing,new SunNetworkChannels().channels());
        assertSame(udaya,r.items.get(0));assertSame(gemini,r.items.get(1));assertSame(kids,r.items.get(2));
        assertEquals("Udaya TV and Udaya TV HD both map to the renamed Sun Udaya id",2,r.officialRoutes.get(udaya.key()).size());
        assertEquals(2,r.officialRoutes.get(gemini.key()).size());
        assertNotNull("same name and language also counts",r.officialRoutes.get(kids.key()));
        assertEquals(37-5,r.items.size()-3);
    }
    @Test public void nameKeyIgnoresCaseHdAndPunctuationButNotLanguage(){
        assertEquals(ChannelDeduplicationService.nameKey("Sun TV HD","Tamil"),ChannelDeduplicationService.nameKey("sun-tv","Tamil"));
        assertNotEquals(ChannelDeduplicationService.nameKey("Sun TV","Tamil"),ChannelDeduplicationService.nameKey("Sun TV","Telugu"));
    }
    @Test public void aFailingProviderNeverAffectsTheExistingListing(){
        List<CatalogItem> existing=Collections.singletonList(directoryChannel("A.in","A","Tamil"));
        AdditionalChannelProvider broken=new AdditionalChannelProvider(){
            @Override public String id(){return "broken";}
            @Override public String label(){return "broken";}
            @Override public List<CatalogItem> channels(){throw new IllegalStateException("down");}
        };
        assertEquals(existing,UnifiedLiveTv.merge(existing,Collections.singletonList(broken)).items);
    }
    @Test public void sourceFilterSeparatesDirectoryAndOfficialEntries(){
        CatalogItem dir=directoryChannel("A.in","A","Tamil"),sun=new SunNetworkChannels().channels().get(0);
        assertTrue(UnifiedLiveTv.matchesSource(dir,UnifiedLiveTv.SOURCE_DIRECTORY));assertFalse(UnifiedLiveTv.matchesSource(sun,UnifiedLiveTv.SOURCE_DIRECTORY));
        assertTrue(UnifiedLiveTv.matchesSource(sun,UnifiedLiveTv.SOURCE_OFFICIAL));assertTrue(UnifiedLiveTv.matchesSource(dir,UnifiedLiveTv.SOURCE_ALL));
    }

    // ---- channel states ------------------------------------------------------------------------
    @Test public void liveNeedsARecentPlaybackValidationNotJustAProbe(){
        long now=10L*ChannelState.PLAYBACK_VALID_MS;CatalogItem c=directoryChannel("A.in","A","Tamil");
        c.health="ok";c.healthCheckedAt=now-1000;
        assertEquals("probe ok alone is not LIVE",ChannelState.UNKNOWN,ChannelState.of(c,false,now));
        c.playbackVerifiedAt=now-60_000;assertEquals(ChannelState.LIVE,ChannelState.of(c,false,now));
        c.playbackVerifiedAt=now-ChannelState.PLAYBACK_VALID_MS-1;assertEquals("stale validation",ChannelState.UNKNOWN,ChannelState.of(c,false,now));
        c.playbackVerifiedAt=now-60_000;c.health="failed";c.healthCheckedAt=now-1000;
        assertEquals("a failed check after the validation wins",ChannelState.TEMPORARILY_UNAVAILABLE,ChannelState.of(c,false,now));
        assertEquals(ChannelState.CHECKING,ChannelState.of(directoryChannel("B.in","B","Tamil"),true,now));
        assertEquals(ChannelState.UNKNOWN,ChannelState.parse("nonsense"));
    }
    @Test public void continuityRequiresPlaybackToAdvance(){
        assertTrue(ChannelValidation.continuous(0,9_000,10_500,true));
        assertFalse("frozen stream",ChannelValidation.continuous(5_000,5_000,10_500,true));
        assertFalse("paused",ChannelValidation.continuous(0,9_000,10_500,false));
        assertFalse("window too short",ChannelValidation.continuous(0,4_000,5_000,true));
    }
    @Test public void existingChannelSignatureIsUnchangedByNewFields()throws Exception{
        CatalogItem c=directoryChannel("A.in","A","Tamil");String sig=c.signature();
        CatalogItem back=CatalogItem.from(c.json());
        assertEquals(sig,back.signature());assertFalse(sig.contains("SUBSCRIPTION"));
        CatalogItem legacy=CatalogItem.from(new JSONObject().put("t",c.title.json()).put("kind","channel"));
        assertEquals("",legacy.playbackType);assertFalse(legacy.handoff());assertNull(legacy.episode);
    }

    // ---- handoff and flags ---------------------------------------------------------------------
    @Test public void youtubeHandoffOnlyAcceptsVideoAndChannelIds(){
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk",OfficialAppHandoff.youtubeUrl("abcdefghijk"));
        assertEquals("https://www.youtube.com/channel/UCYlh4lH762HvHt6mmiecyWQ/live",OfficialAppHandoff.youtubeUrl(SunNetworkChannels.SUN_NEWS_YOUTUBE));
        assertNull(OfficialAppHandoff.youtubeUrl("https://evil.example/stream.m3u8"));
        assertNull(OfficialAppHandoff.youtubeUrl(null));
        assertTrue(OfficialAppHandoff.ALLOWED.contains(OfficialAppHandoff.SUN_NXT));assertFalse(OfficialAppHandoff.ALLOWED.contains("com.example.any"));
    }
    @Test public void sunNetworkFlagDependsOnNewSourcesFlag(){
        Map<FeatureFlags.Flag,Boolean> raw=new EnumMap<>(FeatureFlags.Flag.class);
        raw.put(FeatureFlags.Flag.NEW_LIVE_SOURCES,false);raw.put(FeatureFlags.Flag.SUN_NETWORK,true);
        assertFalse(FeatureFlags.effective(FeatureFlags.Flag.SUN_NETWORK,raw::get));
        raw.put(FeatureFlags.Flag.NEW_LIVE_SOURCES,true);assertTrue(FeatureFlags.effective(FeatureFlags.Flag.SUN_NETWORK,raw::get));
        assertFalse("unset means off",FeatureFlags.effective(FeatureFlags.Flag.EXTENDED_EPG,raw::get));
        assertFalse("EPG ships off until a permitted source is configured",BuildConfig.ENABLE_EXTENDED_EPG);
        assertTrue(UnifiedLiveTv.providers(false,true).isEmpty());assertTrue(UnifiedLiveTv.providers(true,false).isEmpty());
    }

    // ---- programme guide -----------------------------------------------------------------------
    private static final String XMLTV="<?xml version=\"1.0\"?><!DOCTYPE tv SYSTEM \"xmltv.dtd\"><tv>"
        +"<channel id=\"SunTV.in\"><display-name>Sun TV</display-name><icon src=\"https://img.example/sun.png\"/></channel>"
        +"<channel id=\"dd.national\"><display-name>DD National</display-name></channel>"
        +"<channel id=\"dup1\"><display-name>News 24</display-name></channel><channel id=\"dup2\"><display-name>News 24</display-name></channel>"
        +"<programme start=\"20261103180000 +0530\" stop=\"20261103190000 +0530\" channel=\"SunTV.in\"><title>Serial A</title><desc>Episode 12</desc></programme>"
        +"<programme start=\"20261103190000 +0530\" stop=\"20261103200000 +0530\" channel=\"SunTV.in\"><title>Serial B</title></programme>"
        +"<programme start=\"20261101000000 +0530\" stop=\"20261101010000 +0530\" channel=\"SunTV.in\"><title>Old</title></programme>"
        +"<programme start=\"20261103123000\" stop=\"20261103133000\" channel=\"dd.national\"><title>Krishi Darshan</title></programme>"
        +"</tv>";
    private static Xmltv.Guide guide(long now)throws Exception{
        return Xmltv.parse(new ByteArrayInputStream(XMLTV.getBytes(StandardCharsets.UTF_8)),now-EpgRepository.BEFORE_MS,now+EpgRepository.AHEAD_MS,null);
    }
    @Test public void xmltvTimesHonourTheirOffset(){
        assertEquals(java.time.Instant.parse("2026-11-03T12:30:00Z").toEpochMilli(),Xmltv.time("20261103180000 +0530"));
        assertEquals("no offset is UTC",java.time.Instant.parse("2026-11-03T12:30:00Z").toEpochMilli(),Xmltv.time("20261103123000"));
        assertEquals(0,Xmltv.time("garbage"));
    }
    @Test public void nowNextAndProgressComeFromTheWindowedGuide()throws Exception{
        long now=java.time.Instant.parse("2026-11-03T13:00:00Z").toEpochMilli();
        Xmltv.Guide g=guide(now);
        assertEquals("programmes outside the window are dropped",1,g.programmes.get("SunTV.in").stream().filter(p->p.title.equals("Serial A")).count());
        assertTrue(g.programmes.get("SunTV.in").stream().noneMatch(p->p.title.equals("Old")));
        Xmltv.Programme[] nn=g.nowNext("SunTV.in",now);
        assertEquals("Serial A",nn[0].title);assertEquals("Serial B",nn[1].title);assertEquals(50,nn[0].progress(now));
        assertEquals("Episode 12",nn[0].description);
        assertEquals("https://img.example/sun.png",g.icons.get("SunTV.in"));
        Xmltv.Programme[] dd=g.nowNext("dd.national",now);assertEquals("Krishi Darshan",dd[0].title);
    }
    @Test public void epgMappingPrefersExactIdsAndRefusesAmbiguousNames()throws Exception{
        Xmltv.Guide g=guide(java.time.Instant.parse("2026-11-03T13:00:00Z").toEpochMilli());
        CatalogItem sun=directoryChannel("SunTV.in","Sun TV HD","Tamil"),dd=directoryChannel("DDNational.in","DD National","Hindi"),news=directoryChannel("News24.in","News 24","Hindi"),none=directoryChannel("X.in","Unlisted","Hindi");
        Map<String,String> m=EpgMapping.map(Arrays.asList(sun,dd,news,none),g.channels);
        assertEquals("SunTV.in",m.get(sun.key()));
        assertEquals("unique display name",  "dd.national",m.get(dd.key()));
        assertNull("two guide channels share the name",m.get(news.key()));
        assertNull(m.get(none.key()));
    }
    @Test public void xmltvRejectsExternalEntities(){
        String evil="<?xml version=\"1.0\"?><!DOCTYPE tv [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><tv><channel id=\"a\"><display-name>&x;</display-name></channel></tv>";
        try{
            Xmltv.Guide g=Xmltv.parse(new ByteArrayInputStream(evil.getBytes(StandardCharsets.UTF_8)),0,Long.MAX_VALUE,null);
            for(String n:g.channels.getOrDefault("a",Collections.emptyList()))assertFalse(n.contains("root:"));
        }catch(Exception rejected){/* refusing the document is also safe */}
    }
}
