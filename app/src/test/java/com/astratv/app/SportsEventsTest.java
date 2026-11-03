package com.astratv.app;

import org.json.*;
import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class SportsEventsTest {
    private static final long NOW=Instant.parse("2026-11-02T12:00:00Z").toEpochMilli(),HOUR=3_600_000L;

    @Test public void statusIsConservative(){
        assertEquals(SportsSource.LIVE,SportsSource.status("1H","",NOW-HOUR,NOW));
        assertEquals(SportsSource.FINISHED,SportsSource.status("Match Finished","",NOW-3*HOUR,NOW));
        assertEquals(SportsSource.UPCOMING,SportsSource.status("NS","",NOW+HOUR,NOW));
        assertEquals("not started but past kickoff is not claimed live",SportsSource.UNCONFIRMED,SportsSource.status("NS","",NOW-HOUR,NOW));
        assertEquals(SportsSource.UNCONFIRMED,SportsSource.status("Weird code","",NOW-HOUR,NOW));
        assertEquals(SportsSource.POSTPONED,SportsSource.status("","yes",NOW+HOUR,NOW));
        assertEquals(SportsSource.CANCELLED,SportsSource.status("Cancelled","",NOW,NOW));
    }
    @Test public void startTimesAreUtc()throws Exception{
        assertEquals(Instant.parse("2026-11-02T14:00:00Z").toEpochMilli(),SportsSource.start(new JSONObject("{\"strTimestamp\":\"2026-11-02T14:00:00\"}")));
        assertEquals(Instant.parse("2026-11-02T14:00:00Z").toEpochMilli(),SportsSource.start(new JSONObject("{\"strTimestamp\":\"2026-11-02T14:00:00+00:00\"}")));
        assertEquals(Instant.parse("2026-11-02T09:30:00Z").toEpochMilli(),SportsSource.start(new JSONObject("{\"dateEvent\":\"2026-11-02\",\"strTime\":\"09:30:00\"}")));
        assertEquals(0,SportsSource.start(new JSONObject("{}")));
    }
    @Test public void eventsAreScheduleOnlyAndNeverPlayable()throws Exception{
        JSONObject body=new JSONObject("{\"events\":[{\"idEvent\":\"9\",\"strEvent\":\"India vs Australia\",\"strSport\":\"Cricket\",\"strLeague\":\"ODI\",\"strTimestamp\":\"2026-11-02T10:00:00\",\"strStatus\":\"Innings Break\",\"strThumb\":\"https://img/t.jpg\"},{\"strEvent\":\"No id\"}]}");
        List<CatalogItem> out=SportsSource.parse(body,NOW);
        assertEquals(1,out.size());
        CatalogItem e=out.get(0);
        assertEquals(CatalogItem.EVENT,e.kind);
        assertFalse(e.playable);
        assertTrue(e.live());
        assertTrue(e.title.description.contains("not playable"));
        assertTrue(e.title.description.contains("Broadcaster not listed"));
        assertEquals("https://img/t.jpg",e.title.poster);
        assertEquals("Live now (per schedule)",SportsSource.statusLabel(e));
    }
    @Test public void missingEventsListIsEmpty()throws Exception{
        assertTrue(SportsSource.parse(new JSONObject("{\"events\":null}"),NOW).isEmpty());
    }
    @Test public void orderPutsLiveFirstAndDropsOldResults(){
        CatalogItem live=event("live",SportsSource.LIVE,NOW-HOUR),soon=event("soon",SportsSource.UPCOMING,NOW+HOUR),later=event("later",SportsSource.UPCOMING,NOW+5*HOUR);
        CatalogItem recent=event("recent",SportsSource.FINISHED,NOW-3*HOUR),old=event("old",SportsSource.FINISHED,NOW-20*HOUR);
        List<CatalogItem> out=SportsSource.order(Arrays.asList(later,recent,old,soon,live),NOW);
        List<String> ids=new ArrayList<>();for(CatalogItem c:out)ids.add(c.title.id);
        assertEquals(Arrays.asList("live","soon","later","recent"),ids);
    }
    @Test public void liveIsOnlyClaimedFromARecentSync(){
        CatalogItem live=event("live",SportsSource.LIVE,NOW-HOUR),started=event("started",SportsSource.UPCOMING,NOW-60_000),soon=event("soon",SportsSource.UPCOMING,NOW+HOUR);
        SportsSource.reconcile(new ArrayList<>(Arrays.asList(live,started,soon)),NOW-5*60_000,NOW);
        assertEquals(SportsSource.LIVE,live.status);
        assertEquals("kick-off passed since the last sync",SportsSource.UNCONFIRMED,started.status);
        assertEquals(SportsSource.UPCOMING,soon.status);
        SportsSource.reconcile(new ArrayList<>(Collections.singletonList(live)),NOW-2*HOUR,NOW);
        assertEquals("stale schedule never claims live",SportsSource.UNCONFIRMED,live.status);
        assertFalse(live.live());
    }
    @Test public void freeKeySkipsYesterdayToStayUnderTheRateLimit(){
        LocalDate d=LocalDate.of(2026,11,2);
        assertEquals(Arrays.asList(d,d.plusDays(1)),SportsSource.days(d,ApiKeys.SPORTSDB_FREE));
        assertEquals(3,SportsSource.days(d,"own-key").size());
    }
    @Test public void rateLimitedRequestsFallBackToTheirLastGoodResponse()throws Exception{
        InMemoryCatalogStorage cache=new InMemoryCatalogStorage();
        Clock clock=Clock.fixed(Instant.ofEpochMilli(NOW),ZoneOffset.UTC);
        SportsSource source=new SportsSource(()->ApiKeys.SPORTSDB_FREE,()->clock);
        boolean[] limited={false};
        CatalogHttp http=new CatalogHttp((url,h)->{
            if(limited[0]&&!url.contains("s=Cricket"))return new CatalogHttp.Reply(429,"","","",0);
            String sport=url.substring(url.indexOf("&s=")+3),day=url.substring(url.indexOf("d=")+2,url.indexOf("&s="));
            return new CatalogHttp.Reply(200,"{\"events\":[{\"idEvent\":\""+sport+day+"\",\"strEvent\":\""+sport+"\",\"strSport\":\""+sport+"\",\"strTimestamp\":\""+day+"T18:00:00\",\"strStatus\":\"NS\"}]}","","",0);
        },cache,ms->{});
        int full=source.fetch(http).get(0).items.size();
        assertEquals(SportsSource.SPORTS.length*2,full);
        limited[0]=true;
        assertEquals("cached responses fill in for rate-limited requests",full,source.fetch(http).get(0).items.size());
    }
    private static CatalogItem event(String id,String status,long start){
        CatalogItem c=new CatalogItem(new Library.Title(id,id,"","","","",""));c.kind=CatalogItem.EVENT;c.status=status;c.startsAt=start;return c;
    }
}
