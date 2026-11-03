package com.astratv.app;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.*;

/** Catch-Up TV from the TamilTVSerial / TeluguStream RSS feeds: metadata only, official-app viewing, six-tab navigation. */
public class CatchupFeedTest {
    private static final long NOW=ZonedDateTime.of(2026,11,3,15,10,0,0,ZoneOffset.UTC).toInstant().toEpochMilli();
    private static final long DAY=24*3_600_000L;
    private static String page1;

    @BeforeClass public static void load()throws Exception{
        page1=new String(Files.readAllBytes(Paths.get("src/test/resources/catchup/tamiltvserial_page1.xml")),StandardCharsets.UTF_8);
    }
    private static List<CatchupEpisode> parsed(){return SerialFeedParser.parse(page1,TamilTvSerialProvider.PROVIDER,"TamilTVSerial","Tamil");}
    private static CatchupEpisode find(List<CatchupEpisode> all,String series,int number){
        for(CatchupEpisode e:all)if(e.seriesTitle.equals(series)&&e.episodeNumber==number)return e;
        fail("missing "+series+" "+number);return null;
    }
    private static long istMidnight(int day){return LocalDate.of(2026,11,day).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();}
    private static CatalogSource.Batch row(List<CatalogSource.Batch> batches,String id){for(CatalogSource.Batch b:batches)if(b.rowId.equals(id))return b;fail("missing "+id);return null;}
    private static List<String> labels(List<CatalogItem> items){List<String> out=new ArrayList<>();for(CatalogItem c:items)out.add(c.episode.seriesTitle+" "+c.episode.episodeNumber);return out;}

    // ---- navigation ------------------------------------------------------------------------------
    @Test public void navigationHasExactlyTheSixTabs(){
        assertArrayEquals(new String[]{"Home","Movies","TV Shows","Catch-Up TV","Live TV","Sports Live"},Navigation.TABS);
        assertFalse("HD Movies is removed",Navigation.isTab("HD Movies"));
        assertFalse("All Movies is renamed",Navigation.isTab("All Movies"));
        assertTrue(Navigation.isTab("Movies"));
        assertFalse("the show screens are not tabs",Navigation.isTab("Catch-Up Show"));
    }

    // ---- parsing -----------------------------------------------------------------------------------
    @Test public void titlesAndCategoriesBecomeShowEpisodeDateAndBroadcaster(){
        List<CatchupEpisode> all=parsed();
        assertEquals("the undated announcement is skipped",9,all.size());
        CatchupEpisode ayali=find(all,"Ayali",445);
        assertEquals(istMidnight(3),ayali.airDate);assertEquals("Zee Tamil",ayali.channelTitle);assertEquals("Tamil",ayali.language);
        assertEquals(CatalogItem.APP_HANDOFF,ayali.playbackType);assertEquals(OfficialAppHandoff.ZEE5,ayali.playbackReference);assertEquals(CatalogItem.SUBSCRIPTION,ayali.accessType);
        assertEquals("tamiltvserial/ayali",ayali.seriesId);assertEquals("tamiltvserial/ayali/e445",ayali.episodeId);
        CatchupEpisode ethir=find(all,"Ethir Neechal",668);
        assertEquals("show name comes from the title, not the differently spelled category","Sun TV",ethir.channelTitle);assertEquals(OfficialAppHandoff.SUN_NXT,ethir.playbackReference);
        CatchupEpisode chef=find(all,"Celebrity Master Chef – Tamil",12);
        assertEquals("TV show",chef.genre);assertEquals(OfficialAppHandoff.SONY_LIV,chef.playbackReference);
        CatchupEpisode awards=find(all,"Vijay Awards Special",2);
        assertEquals("no date in the title: the post date is used",SerialFeedParser.pubDate("Mon, 02 Nov 2026 12:30:00 +0000"),awards.airDate);
        assertEquals(OfficialAppHandoff.JIOHOTSTAR,awards.playbackReference);
    }
    @Test public void languageFollowsTheBroadcasterNotTheSite(){
        CatchupEpisode jayam=find(parsed(),"Jayam",418);
        assertEquals("a Zee Telugu serial on the Tamil site is Telugu","Telugu",jayam.language);
        assertEquals(OfficialAppHandoff.ZEE5,jayam.playbackReference);
    }
    @Test public void unknownOrAppLessBroadcasterGetsNoWatchAction(){
        CatchupEpisode kanmani=find(parsed(),"Kanmani",33);
        assertEquals("Polimer TV",kanmani.channelTitle);assertEquals("Tamil",kanmani.language);
        assertEquals("",kanmani.playbackType);assertEquals("",kanmani.playbackReference);assertEquals(CatalogItem.UNAVAILABLE,kanmani.accessType);
        CatalogItem c=new TamilTvSerialProvider(null).item(kanmani);
        assertFalse("no Play or Watch button without an official app",c.handoff());assertFalse(c.playable);
        assertTrue(kanmani.description.contains("no official streaming app"));
    }
    @Test public void noPageVideoOrImageLinkIsKept()throws Exception{
        for(CatchupEpisode e:parsed()){
            String json=e.json().toString();
            assertFalse(e.seriesTitle+" stores no URL: "+json,json.contains("http")||json.contains("videohost")||json.contains("img.example"));
            assertEquals("",e.posterUrl);assertEquals("",e.backdropUrl);
            assertTrue("viewing is an allowlisted app or nothing",e.playbackReference.isEmpty()||OfficialAppHandoff.ALLOWED.contains(e.playbackReference));
            assertTrue(e.description.contains("does not play its videos"));
        }
    }
    @Test public void htmlEntitiesAndCdataAreDecoded(){
        assertEquals("A | B – C & D",SerialFeedParser.text("<![CDATA[A &#124; B &#8211; C &amp; D]]>"));
        assertEquals("x",SerialFeedParser.text("<b>x</b>"));
        assertEquals(0,SerialFeedParser.pubDate("not a date"));
        assertNull(SerialFeedParser.episode("",Collections.emptyList(),NOW,"p","P","Tamil"));
    }

    // ---- broadcasters ---------------------------------------------------------------------------------
    @Test public void broadcastersMapToTheirOfficialApps(){
        Object[][] cases={
            {"Sun Tv Serial","Sun TV","Tamil",OfficialAppHandoff.SUN_NXT},{"Gemini Tv Serial","Gemini TV","Telugu",OfficialAppHandoff.SUN_NXT},
            {"Udaya TV","Udaya TV","Kannada",OfficialAppHandoff.SUN_NXT},{"Surya TV Serials","Surya TV","Malayalam",OfficialAppHandoff.SUN_NXT},
            {"Vijay Tv Serial","Star Vijay","Tamil",OfficialAppHandoff.JIOHOTSTAR},{"Star Maa Serials","Star Maa","Telugu",OfficialAppHandoff.JIOHOTSTAR},
            {"Zee Telugu Serials","Zee Telugu","Telugu",OfficialAppHandoff.ZEE5},{"ETV Telugu serial","ETV","Telugu",OfficialAppHandoff.ETV_WIN},
            {"Sony Vizha TV Shows","Sony Vizha","Tamil",OfficialAppHandoff.SONY_LIV},{"Kalaignar TV Serials","Kalaignar TV","Tamil",""},
            {"Colors Kannada TV Shows","Colors Kannada","Kannada",OfficialAppHandoff.JIOHOTSTAR}};
        for(Object[] c:cases){
            CatchupBroadcasters.Broadcaster b=CatchupBroadcasters.find((String)c[0]);
            assertNotNull((String)c[0],b);assertEquals(c[1],b.name);assertEquals(c[2],b.language);assertEquals(c[3],b.app);
            if(b.watchable())assertTrue(OfficialAppHandoff.ALLOWED.contains(b.app));
        }
        assertNull("a show name that starts with Sun is not Sun TV",CatchupBroadcasters.find("Sundari"));
        assertNull(CatchupBroadcasters.find(""));assertNull(CatchupBroadcasters.find(null));
        assertEquals("JioHotstar",OfficialAppHandoff.appName(OfficialAppHandoff.JIOHOTSTAR));assertEquals("ETV Win",OfficialAppHandoff.appName(OfficialAppHandoff.ETV_WIN));
    }

    @Test public void airDatesAreIndianDatesWithoutAClockTime(){
        long aired=istMidnight(3),hour=3_600_000L;
        assertEquals("Tue 3 Nov",SerialCatchupProvider.day(aired));
        assertEquals("Today",SerialCatchupProvider.aired(aired,istMidnight(3)+20*hour));
        assertEquals("Yesterday",SerialCatchupProvider.aired(aired,istMidnight(4)+hour));
        assertEquals("Tue 3 Nov",SerialCatchupProvider.aired(aired,istMidnight(9)));
        assertFalse(row(new TamilTvSerialProvider(null).batches(parsed(),NOW),new TamilTvSerialProvider(null).latestRow()).items.get(0).episodeLabel.contains(":"));
    }

    // ---- rows, shows and episodes ----------------------------------------------------------------------
    @Test public void episodesAreNewestFirstWithoutDuplicates(){
        TamilTvSerialProvider p=new TamilTvSerialProvider(null);
        List<CatalogItem> latest=row(p.batches(parsed(),NOW),p.latestRow()).items;
        assertEquals(Arrays.asList("Ethir Neechal 668","Ayali 445","Jayam 418","Enna Solla Pogirai 70","Enna Solla Pogirai 69","Celebrity Master Chef – Tamil 12","Vijay Awards Special 2","Kanmani 33"),labels(latest));
        Set<String> keys=new HashSet<>();for(CatalogItem c:latest)assertTrue("stable unique key "+c.key(),keys.add(c.key()));
        for(CatalogItem c:latest){assertEquals(CatalogItem.CATCHUP,c.kind);assertFalse(SerialCatchupProvider.showTile(c));}
    }
    @Test public void showTilesOpenTheirEpisodeLists()throws Exception{
        TamilTvSerialProvider p=new TamilTvSerialProvider(null);
        List<CatalogSource.Batch> out=p.batches(parsed(),NOW);
        List<CatalogItem> shows=row(out,p.serialsRow()).items,latest=row(out,p.latestRow()).items;
        assertEquals(7,shows.size());
        CatalogItem enna=null;for(CatalogItem s:shows){assertTrue(SerialCatchupProvider.showTile(s));if(s.title.name.equals("Enna Solla Pogirai"))enna=s;}
        assertNotNull(enna);
        assertEquals("tamiltvserial/enna-solla-pogirai",enna.title.id);assertEquals("Tamil",enna.title.language);
        assertEquals(70,enna.episode.episodeNumber);assertTrue(enna.episodeLabel.startsWith("Latest: Episode 70"));assertTrue(enna.episodeLabel.contains("Zee Tamil"));
        assertTrue(enna.title.description.contains("2 episodes"));
        assertEquals(OfficialAppHandoff.ZEE5,enna.playbackReference);
        List<CatalogItem> eps=SerialCatchupProvider.episodesOf(enna,latest);
        assertEquals(Arrays.asList("Enna Solla Pogirai 70","Enna Solla Pogirai 69"),labels(eps));
        assertNotEquals("show and episode keys differ",enna.key(),eps.get(0).key());
        CatalogItem back=CatalogItem.from(enna.json());
        assertTrue("show tiles survive the store round trip",SerialCatchupProvider.showTile(back));assertEquals(70,back.episode.episodeNumber);
    }
    @Test public void showTilesAreNotContinueWatchingEntries(){
        TamilTvSerialProvider p=new TamilTvSerialProvider(null);
        List<CatalogSource.Batch> out=p.batches(parsed(),NOW);
        CatalogItem show=row(out,p.serialsRow()).items.get(0),episode=row(out,p.latestRow()).items.get(0);
        assertEquals(Collections.singletonList(episode),SerialCatchupProvider.mine(Arrays.asList(show,episode),10));
    }
    @Test public void sunNetworkChannelsNeverEnterCatchUp(){
        TamilTvSerialProvider p=new TamilTvSerialProvider(null);
        for(CatalogSource.Batch b:p.batches(parsed(),NOW))for(CatalogItem c:b.items){
            assertEquals("Catch-Up rows hold episodes and shows, never channels",CatalogItem.CATCHUP,c.kind);
            assertNotEquals(SunNetworkChannels.PROVIDER,c.title.provider);
        }
        assertEquals("catch-up rows belong to the catch-up source, not Live TV",TamilTvSerialProvider.ID,CatalogRepository.sourceFor(p.latestRow()));
    }
    @Test public void storedEpisodesAreKeptForTwoWeeks()throws Exception{
        long now=System.currentTimeMillis();
        CatchupEpisode fresh=find(parsed(),"Ayali",445);fresh.airDate=now-DAY;
        CatchupEpisode recent=find(parsed(),"Jayam",418);recent.airDate=now-3*DAY;
        CatchupEpisode stale=find(parsed(),"Kanmani",33);stale.airDate=now-20*DAY;
        TamilTvSerialProvider p=new TamilTvSerialProvider((http,known)->{
            assertTrue("stored episodes are passed as known",known.contains(recent.episodeId));
            return Collections.singletonList(fresh);
        });
        p.retain(row->row.equals(p.latestRow())?Arrays.asList(p.item(recent),p.item(stale)):Collections.emptyList());
        List<String> got=labels(row(p.fetch(null),p.latestRow()).items);
        assertEquals(Arrays.asList("Ayali 445","Jayam 418"),got);
    }

    // ---- feed requests -----------------------------------------------------------------------------------
    private static final class Script implements CatalogHttp.Transport {
        final Map<String,CatalogHttp.Reply> replies=new HashMap<>();final List<String> requested=new ArrayList<>();
        @Override public CatalogHttp.Reply get(String url,Map<String,String> headers){requested.add(url);CatalogHttp.Reply r=replies.get(url);return r!=null?r:new CatalogHttp.Reply(404,"","","",0);}
    }
    @Test public void firstSyncPagesThroughTheFeedOnly()throws Exception{
        Script t=new Script();
        t.replies.put(TamilTvSerialProvider.FEED,new CatalogHttp.Reply(200,page1,"\"e1\"","",0));
        t.replies.put(TamilTvSerialProvider.FEED+"?paged=2",new CatalogHttp.Reply(200,page1.replace("03-11-2026","01-11-2026"),"\"e2\"","",0));
        WordPressSerialFeed feed=new WordPressSerialFeed(TamilTvSerialProvider.FEED,TamilTvSerialProvider.PROVIDER,"TamilTVSerial","Tamil",5);
        List<CatchupEpisode> got=feed.episodes(new CatalogHttp(t,null,ms->{}),Collections.emptySet());
        assertEquals("page 3 is past the end (404), so paging stops",Arrays.asList(TamilTvSerialProvider.FEED,TamilTvSerialProvider.FEED+"?paged=2",TamilTvSerialProvider.FEED+"?paged=3"),t.requested);
        assertEquals(18,got.size());
        for(String url:t.requested)assertTrue("only the RSS feed is requested: "+url,url.startsWith("https://www.tamiltvserial.com/feed/"));
    }
    @Test public void routineRefreshStopsAtTheFirstPageWithNothingNew()throws Exception{
        Script t=new Script();t.replies.put(TamilTvSerialProvider.FEED,new CatalogHttp.Reply(200,page1,"","",0));
        Set<String> known=new HashSet<>();for(CatchupEpisode e:parsed())known.add(e.episodeId);
        new WordPressSerialFeed(TamilTvSerialProvider.FEED,TamilTvSerialProvider.PROVIDER,"TamilTVSerial","Tamil",12).episodes(new CatalogHttp(t,null,ms->{}),known);
        assertEquals(Collections.singletonList(TamilTvSerialProvider.FEED),t.requested);
    }
    @Test public void unreachableFeedFailsTheSyncSoSavedRowsStay(){
        Script t=new Script();
        try{new WordPressSerialFeed(TeluguStreamProvider.FEED,TeluguStreamProvider.PROVIDER,"TeluguStream","Telugu",3).episodes(new CatalogHttp(t,null,ms->{}),Collections.emptySet());fail("expected failure");}
        catch(IOException expected){/* the coordinator keeps the previous rows */}
        catch(Exception e){fail("unexpected "+e);}
        try{new WordPressSerialFeed("http://telugustream.com/feed/","x","X","Telugu",1);fail("plain HTTP feeds are refused");}catch(IllegalArgumentException expected){/* ok */}
    }
    @Test public void bothSitesAreConfiguredOverHttps(){
        assertTrue(Library.https(TamilTvSerialProvider.FEED));assertTrue(Library.https(TeluguStreamProvider.FEED));
        assertTrue(TamilTvSerialProvider.live().ready());assertTrue(TeluguStreamProvider.live().ready());
    }
}
