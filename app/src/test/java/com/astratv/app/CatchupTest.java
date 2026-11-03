package com.astratv.app;

import static org.junit.Assert.*;

import java.util.*;
import org.json.*;
import org.junit.Test;

/** Catch-Up TV: title parsing, programme filtering, newest-first rows and the episode model. */
public class CatchupTest {
    private static final long NOW=java.time.Instant.parse("2026-11-03T12:00:00Z").toEpochMilli();
    private static final CatchupChannels.Channel DD_TAMIL=CatchupChannels.byYoutubeId("UC5-_zkjKabYd7tsLucKsriw");

    private static JSONObject video(String id,String title,String published,String duration,String channelId)throws JSONException{
        return new JSONObject().put("id",id)
            .put("snippet",new JSONObject().put("title",title).put("channelId",channelId).put("channelTitle","DD Tamil").put("publishedAt",published)
                .put("liveBroadcastContent","none").put("thumbnail","https://i.ytimg.com/vi/"+id+"/hqdefault.jpg"))
            .put("contentDetails",new JSONObject().put("duration",duration))
            .put("status",new JSONObject().put("privacyStatus","public").put("embeddable",true));
    }

    @Test public void parsesEpisodeSeasonAndSeriesFromBroadcasterTitles(){
        assertEquals(12,CatchupEpisode.episodeNumber("Vanakkam Tamizha | Episode 12 | DD Tamil"));
        assertEquals(305,CatchupEpisode.episodeNumber("Ramayan - Ep. 305"));
        assertEquals(5,CatchupEpisode.episodeNumber("Serial S2E5"));
        assertEquals(2,CatchupEpisode.seasonNumber("Serial S2E5"));
        assertEquals(3,CatchupEpisode.seasonNumber("Show Season 3 Episode 4"));
        assertEquals(0,CatchupEpisode.episodeNumber("Republic Day Parade 2026"));
        assertEquals("Vanakkam Tamizha",CatchupEpisode.seriesTitle("Vanakkam Tamizha | Episode 12 | DD Tamil","DD Tamil"));
        assertEquals("Ramayan",CatchupEpisode.seriesTitle("Ramayan - Ep. 305","Prasar Bharati Archives"));
        assertEquals("Krishi Darshan",CatchupEpisode.seriesTitle("DD Tamil | Krishi Darshan Episode 8","DD Tamil"));
    }
    @Test public void shortsPromosAndLiveStreamsAreNotProgrammes(){
        assertFalse(CatchupEpisode.programme("Serial | Ep 3 #shorts",50,false));
        assertFalse(CatchupEpisode.programme("Serial Promo | Ep 3",600,false));
        assertFalse(CatchupEpisode.programme("Serial | Ep 3",1200,true));
        assertTrue(CatchupEpisode.programme("Serial | Ep 3",1200,false));
    }
    @Test public void keepsOnlyThisChannelsPublicProgrammesAvailableHere()throws Exception{
        JSONArray items=new JSONArray()
            .put(video("aaaaaaaaaaa","Serial One | Episode 4 | DD Tamil","2026-11-02T14:00:00Z","PT22M","UC5-_zkjKabYd7tsLucKsriw"))
            .put(video("bbbbbbbbbbb","Serial One | Episode 3 | DD Tamil","2026-11-01T14:00:00Z","PT21M","UC5-_zkjKabYd7tsLucKsriw"))
            .put(video("ccccccccccc","Other channel upload","2026-11-02T14:00:00Z","PT30M","UCxxxxxxxxxxxxxxxxxxxxxx"))
            .put(video("ddddddddddd","Short clip","2026-11-02T14:00:00Z","PT40S","UC5-_zkjKabYd7tsLucKsriw"))
            .put(video("eeeeeeeeeee","Pongal special programme","2026-10-20T14:00:00Z","PT45M","UC5-_zkjKabYd7tsLucKsriw"))
            .put(video("fffffffffff","Future premiere","2026-11-09T14:00:00Z","PT45M","UC5-_zkjKabYd7tsLucKsriw"));
        JSONObject blocked=video("ggggggggggg","Blocked | Episode 1","2026-11-02T14:00:00Z","PT20M","UC5-_zkjKabYd7tsLucKsriw");
        blocked.getJSONObject("contentDetails").put("regionRestriction",new JSONObject().put("blocked",new JSONArray().put("IN")));items.put(blocked);
        JSONObject priv=video("hhhhhhhhhhh","Private | Episode 1","2026-11-02T14:00:00Z","PT20M","UC5-_zkjKabYd7tsLucKsriw");
        priv.getJSONObject("status").put("privacyStatus","unlisted");items.put(priv);

        List<CatchupEpisode> eps=CatchupSource.episodes(DD_TAMIL,new JSONObject().put("items",items),"IN",NOW);
        List<String> ids=new ArrayList<>();for(CatchupEpisode e:eps)ids.add(e.playbackReference);
        assertEquals(Arrays.asList("aaaaaaaaaaa","bbbbbbbbbbb","eeeeeeeeeee"),ids);
        CatchupEpisode e=eps.get(0);
        assertEquals("yt:aaaaaaaaaaa",e.episodeId);assertEquals("Serial One",e.seriesTitle);assertEquals(4,e.episodeNumber);assertEquals("Tamil",e.language);
        assertEquals(CatalogItem.FREE,e.accessType);assertEquals(CatalogItem.YOUTUBE_HANDOFF,e.playbackType);assertEquals(22*60,e.durationSeconds);
        assertEquals("episode artwork is the episode's own thumbnail","https://i.ytimg.com/vi/aaaaaaaaaaa/hqdefault.jpg",e.posterUrl);
        assertEquals(e.seriesId,eps.get(1).seriesId);
        assertFalse(eps.get(2).serial());
    }
    @Test public void rowsAreNewestFirstWithOneEntryPerSeriesInLatest()throws Exception{
        JSONArray items=new JSONArray()
            .put(video("bbbbbbbbbbb","Serial One | Episode 3 | DD Tamil","2026-11-01T14:00:00Z","PT21M","UC5-_zkjKabYd7tsLucKsriw"))
            .put(video("aaaaaaaaaaa","Serial One | Episode 4 | DD Tamil","2026-11-02T14:00:00Z","PT22M","UC5-_zkjKabYd7tsLucKsriw"))
            .put(video("eeeeeeeeeee","Pongal special programme","2026-10-20T14:00:00Z","PT45M","UC5-_zkjKabYd7tsLucKsriw"));
        List<CatchupEpisode> eps=new ArrayList<>(CatchupSource.episodes(DD_TAMIL,new JSONObject().put("items",items),"IN",NOW));
        CatchupChannels.Channel archives=CatchupChannels.byYoutubeId("UCWxqSvqiH8PGpSNPSVtFwGw");
        eps.addAll(CatchupSource.episodes(archives,new JSONObject().put("items",new JSONArray().put(video("iiiiiiiiiii","Malgudi Days - Episode 1","2026-10-01T10:00:00Z","PT25M",archives.youtubeId))),"IN",NOW));
        Map<String,List<CatalogItem>> rows=new HashMap<>();for(CatalogSource.Batch b:CatchupSource.batches(eps,NOW))rows.put(b.rowId,b.items);
        assertEquals(Arrays.asList("yt:aaaaaaaaaaa","yt:bbbbbbbbbbb"),ids(rows.get(CatchupSource.ROW_RECENT)));
        assertEquals("one entry per series, its newest episode",Collections.singletonList("yt:aaaaaaaaaaa"),ids(rows.get(CatchupSource.ROW_LATEST)));
        assertEquals(Arrays.asList("yt:aaaaaaaaaaa","yt:bbbbbbbbbbb"),ids(rows.get(CatchupSource.langRow("ta"))));
        assertTrue(rows.get(CatchupSource.langRow("te")).isEmpty());
        assertEquals(Collections.singletonList("yt:iiiiiiiiiii"),ids(rows.get(CatchupSource.ROW_CLASSICS)));
        assertEquals(Collections.singletonList("yt:eeeeeeeeeee"),ids(rows.get(CatchupSource.ROW_FREE)));
        CatalogItem c=rows.get(CatchupSource.ROW_LATEST).get(0);
        assertEquals(CatalogItem.CATCHUP,c.kind);assertEquals("Serial One",c.title.name);assertFalse(c.playable);assertTrue(c.handoff());
        assertTrue(c.episodeLabel.startsWith("Ep 4"));
    }
    private static List<String> ids(List<CatalogItem> items){List<String> out=new ArrayList<>();for(CatalogItem c:items)out.add(c.title.id);return out;}

    @Test public void episodeModelRoundTripsThroughTheCatalogStore()throws Exception{
        CatchupEpisode e=new CatchupEpisode();e.episodeId="yt:aaaaaaaaaaa";e.providerId="yt.UC";e.seriesId="yt.UC/serial-one";e.seriesTitle="Serial One";e.episodeTitle="Serial One | Episode 4";
        e.language="Tamil";e.genre="Serial";e.seasonNumber=1;e.episodeNumber=4;e.airDate=NOW-1000;e.availableFrom=NOW-1000;e.availableUntil=0;e.posterUrl="https://i.ytimg.com/x.jpg";e.backdropUrl=e.posterUrl;
        e.durationSeconds=1320;e.playbackReference="aaaaaaaaaaa";e.lastValidatedAt=NOW;
        CatalogItem back=CatalogItem.from(CatchupSource.item(e).json());
        assertEquals(e.json().toString(),back.episode.json().toString());
        for(String field:new String[]{"episodeId","providerId","seriesId","seriesTitle","episodeTitle","language","genre","seasonNumber","episodeNumber","airDate","availableFrom","availableUntil","posterUrl","backdropUrl","duration","accessType","playbackType","playbackReference","lastValidatedAt"})
            assertTrue(field,back.episode.json().has(field));
    }
    @Test public void continueWatchingKeepsNewestFirstWithoutDuplicates()throws Exception{
        CatchupEpisode a=new CatchupEpisode();a.episodeId="yt:aaaaaaaaaaa";a.episodeTitle="A";a.playbackReference="aaaaaaaaaaa";
        CatchupEpisode b=new CatchupEpisode();b.episodeId="yt:bbbbbbbbbbb";b.episodeTitle="B";b.playbackReference="bbbbbbbbbbb";
        String s=CatchupHistory.add("[]",CatchupSource.item(a),1);s=CatchupHistory.add(s,CatchupSource.item(b),2);s=CatchupHistory.add(s,CatchupSource.item(a),3);
        assertEquals(Arrays.asList("yt:aaaaaaaaaaa","yt:bbbbbbbbbbb"),ids(CatchupHistory.items(s,10)));
        assertTrue(CatchupHistory.items("not json",10).isEmpty());
    }
    @Test public void subscriptionTilesNeverClaimFreePlayback(){
        for(CatalogItem c:CatchupSource.subscriptionTiles()){
            assertEquals(CatalogItem.SUBSCRIPTION,c.accessType);assertEquals(OfficialAppHandoff.SUN_NXT,c.playbackReference);assertFalse(c.playable);
        }
    }
    @Test public void catchupChannelsMatchTheBackendAllowlistFormat(){
        Set<String> ids=new HashSet<>();
        for(CatchupChannels.Channel c:CatchupChannels.CHANNELS){assertTrue(c.youtubeId.matches("UC[A-Za-z0-9_-]{22}"));assertTrue(ids.add(c.youtubeId));}
        assertEquals("https://api.example/api/channel-uploads?channel=UC5-_zkjKabYd7tsLucKsriw",CatchupSource.backendUrl("https://api.example/","UC5-_zkjKabYd7tsLucKsriw"));
        assertNull("no plain-HTTP backends",CatchupSource.backendUrl("http://api.example","UC5-_zkjKabYd7tsLucKsriw"));
    }
    @Test public void backendAllowlistServesExactlyTheseChannels()throws Exception{
        java.nio.file.Path js=java.nio.file.Paths.get("..","backend","lib","channel-uploads.js");
        if(!java.nio.file.Files.exists(js))js=java.nio.file.Paths.get("backend","lib","channel-uploads.js");
        String source=new String(java.nio.file.Files.readAllBytes(js),java.nio.charset.StandardCharsets.UTF_8);
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\['(UC[A-Za-z0-9_-]{22})'").matcher(source);Set<String> backend=new HashSet<>();while(m.find())backend.add(m.group(1));
        Set<String> app=new HashSet<>();for(CatchupChannels.Channel c:CatchupChannels.CHANNELS)app.add(c.youtubeId);
        assertEquals(app,backend);
    }
}
