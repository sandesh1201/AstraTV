package com.astratv.app;

import static org.junit.Assert.*;

import java.util.*;
import org.json.JSONObject;
import org.junit.Test;

public class TrailerMatcherTest {
    private static TrailerMatcher.Candidate c(String id,String title,String channel,int seconds){return new TrailerMatcher.Candidate(id,title,channel,"",seconds,true,true,false);}
    private static final TrailerMatcher.Candidate OFFICIAL=c("aaaaaaaaaaa","Pushpa 2 The Rule Official Trailer | Allu Arjun | Telugu","Mythri Movie Makers",151);

    @Test public void queryFollowsTitleYearLanguageOfficialTrailer(){
        assertEquals("Pushpa 2 2024 Telugu official trailer",TrailerMatcher.query("Pushpa 2","2024","Telugu"));
        assertEquals("Dune 2021 official trailer",TrailerMatcher.query("Dune","2021","English"));
        assertEquals("Sintel official trailer",TrailerMatcher.query("Sintel","",""));
    }

    @Test public void picksTheOfficialStudioTrailerOverEverythingElse(){
        List<TrailerMatcher.Candidate> list=Arrays.asList(
            c("bbbbbbbbbbb","Pushpa 2 Trailer REACTION!!","Some Reactor",900),
            c("ccccccccccc","Pushpa 2 Trailer Review & Breakdown","Film Talks",400),
            c("ddddddddddd","Pushpa 2 Fan Made Trailer","Edits Hub",120),
            c("eeeeeeeeeee","Pushpa 2 Full Movie Hindi Dubbed","Movies 4K",9000),
            OFFICIAL);
        assertEquals("aaaaaaaaaaa",TrailerMatcher.best("Pushpa 2","2024","Telugu",list).videoId);
    }

    @Test public void rejectsReactionsReviewsFanEditsAndFullMovies(){
        for(String title:new String[]{"Pushpa 2 Official Trailer Reaction","Pushpa 2 Trailer Review","Pushpa 2 Fan-Made Trailer","Pushpa 2 Full Movie Trailer","Pushpa 2 Trailer #shorts"})
            assertEquals(title,Integer.MIN_VALUE,TrailerMatcher.score("Pushpa 2","2024","Telugu",c("aaaaaaaaaaa",title,"Mythri Movie Makers",120)));
    }

    @Test public void rejectsUnofficialChannelsWrongYearsAndUnembeddableVideos(){
        assertEquals(Integer.MIN_VALUE,TrailerMatcher.score("Pushpa 2","2024","Telugu",c("aaaaaaaaaaa","Pushpa 2 Official Trailer","Random Uploads 123",150)));
        assertEquals(Integer.MIN_VALUE,TrailerMatcher.score("Devara","2024","Telugu",c("aaaaaaaaaaa","Devara 2027 Official Trailer","T-Series Telugu",150)));
        assertEquals(Integer.MIN_VALUE,TrailerMatcher.score("Pushpa 2","2024","Telugu",new TrailerMatcher.Candidate("aaaaaaaaaaa","Pushpa 2 Official Trailer","Mythri Movie Makers","",150,false,true,false)));
        assertEquals(Integer.MIN_VALUE,TrailerMatcher.score("Pushpa 2","2024","Telugu",new TrailerMatcher.Candidate("aaaaaaaaaaa","Pushpa 2 Official Trailer","Mythri Movie Makers","",150,true,false,false)));
        assertEquals(Integer.MIN_VALUE,TrailerMatcher.score("Pushpa 2","2024","Telugu",new TrailerMatcher.Candidate("aaaaaaaaaaa","Pushpa 2 Official Trailer","Mythri Movie Makers","",150,true,true,true)));
        assertEquals("too long for a trailer",Integer.MIN_VALUE,TrailerMatcher.score("Pushpa 2","2024","Telugu",c("aaaaaaaaaaa","Pushpa 2 Official Trailer","Mythri Movie Makers",3600)));
        assertEquals("another movie",Integer.MIN_VALUE,TrailerMatcher.score("Pushpa 2","2024","Telugu",c("aaaaaaaaaaa","Pushpa The Rise Official Trailer","Mythri Movie Makers",150)));
        assertEquals("bad id",Integer.MIN_VALUE,TrailerMatcher.score("Pushpa 2","2024","Telugu",c("short","Pushpa 2 Official Trailer","Mythri Movie Makers",150)));
        assertNull(TrailerMatcher.best("Pushpa 2","2024","Telugu",Collections.singletonList(c("aaaaaaaaaaa","Pushpa 2 Official Trailer","Random Uploads 123",150))));
    }

    @Test public void wordsInsideTheMovieNameAreNotRejected(){
        assertTrue(TrailerMatcher.score("Event Horizon","1997","English",c("aaaaaaaaaaa","Event Horizon (1997) Official Trailer","Paramount Pictures",140))>=TrailerMatcher.MIN_SCORE);
        assertTrue(TrailerMatcher.score("2012","2009","English",c("aaaaaaaaaaa","2012 Official Trailer","Sony Pictures Entertainment",150))>=TrailerMatcher.MIN_SCORE);
        assertTrue("the movie's own official channel",TrailerMatcher.score("Kalki 2898 AD","2024","Telugu",c("aaaaaaaaaaa","Kalki 2898 AD Release Trailer","Kalki 2898 AD Official",150))>=TrailerMatcher.MIN_SCORE);
    }

    @Test public void parsesYoutubeVideosWithDurationAndRegionBlocks()throws Exception{
        JSONObject videos=new JSONObject("{\"items\":[{\"id\":\"aaaaaaaaaaa\",\"snippet\":{\"title\":\"Sintel Official Trailer\",\"channelTitle\":\"Blender Studio\",\"liveBroadcastContent\":\"none\"},"
            +"\"contentDetails\":{\"duration\":\"PT1M52S\",\"regionRestriction\":{\"blocked\":[\"IN\"]}},\"status\":{\"embeddable\":true,\"privacyStatus\":\"public\"}}]}");
        TrailerMatcher.Candidate us=YoutubeTrailerSearch.candidates(videos,"US").get(0);
        assertTrue(us.embeddable);assertEquals(112,us.seconds);assertTrue(us.isPublic);assertFalse(us.live);
        assertFalse("blocked in the viewer's country",YoutubeTrailerSearch.candidates(videos,"in").get(0).embeddable);
        assertEquals(3723,YoutubeTrailerSearch.seconds("PT1H2M3S"));assertEquals(0,YoutubeTrailerSearch.seconds("bogus"));
        assertEquals(Collections.singletonList("aaaaaaaaaaa"),YoutubeTrailerSearch.searchIds(new JSONObject("{\"items\":[{\"id\":{\"videoId\":\"aaaaaaaaaaa\"}},{\"id\":{\"channelId\":\"x\"}}]}")));
    }

    @Test public void searchUsesTheServerRouteOnlyOverHttps(){
        assertEquals("https://api.example.com/api/trailer?q=Sintel%202010%20official%20trailer",YoutubeTrailerSearch.backendUrl("https://api.example.com/","Sintel 2010 official trailer"));
        assertNull(YoutubeTrailerSearch.backendUrl("http://api.example.com","x"));
        assertNull(YoutubeTrailerSearch.backendUrl("","x"));
        assertTrue(YoutubeTrailerSearch.searchUrl("q","k").contains("videoEmbeddable=true"));
    }

    @Test public void cacheLifetimesAndPermanentPlayerErrors(){
        long now=10_000_000_000L;
        assertTrue(TrailerCache.fresh(true,now-TrailerCache.HIT_TTL_MS+1,now));
        assertFalse(TrailerCache.fresh(true,now-TrailerCache.HIT_TTL_MS-1,now));
        assertFalse("misses are retried sooner",TrailerCache.fresh(false,now-TrailerCache.MISS_TTL_MS-1,now));
        assertFalse(TrailerCache.fresh(true,0,now));
        for(int code:new int[]{2,100,101,150})assertTrue(TrailerCache.permanent(code));
        assertFalse("timeouts and page errors may be temporary",TrailerCache.permanent(-1));
    }

    @Test public void embedPageUsesTheOfficialIframeApiMutedWithoutControls(){
        String html=YoutubeTrailerView.html("aaaaaaaaaaa",true,"https://com.astratv.app");
        assertTrue(html.contains("https://www.youtube.com/iframe_api"));
        assertTrue(html.contains("mute:1"));assertTrue(html.contains("controls:0"));assertTrue(html.contains("e.target.mute()"));
        assertTrue(html.contains("origin:'https://com.astratv.app'"));
        assertFalse("no direct media URLs",html.contains("googlevideo"));
        assertTrue(YoutubeTrailerView.validId("aaaaaaaaaaa"));
        assertFalse(YoutubeTrailerView.validId("aaaa'+alert(1)+'"));
    }
}
