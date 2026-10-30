package com.astratv.app;

import org.json.*;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import static org.junit.Assert.*;

public class ResearchEditionTest {
    @Test public void playlistRespectsQuotedCommasGroupsVariantsAndLanguages()throws Exception{
        String list="#EXTM3U\n#EXTINF:-1 tvg-logo=\"https://images.test/a.jpg\" group-title=\"Movies / Telugu / 2026\",Hello, World\nhttps://cdn.test/one.mp4\n#EXTINF:-1 group-title=\"Movies / Telugu / 2026\",Hello, World\nhttps://cdn.test/two.mp4\n#EXTINF:-1 group-title=\"Movies / Hindi / 2026\",Hello, World\nhttps://cdn.test/three.mp4";
        List<Library.Title> titles=PlaylistParser.movies(list);assertEquals(2,titles.size());assertEquals("Hello, World",titles.get(0).name);assertEquals("Telugu",titles.get(0).language);assertEquals(2,titles.get(0).videos.length());assertNotEquals(titles.get(0).id,titles.get(1).id);
    }
    @Test public void playlistRejectsDrmNonHttpsAndUnexpectedHeaderDirectives(){
        String list="#EXTM3U\n#EXTINF:-1,Protected\n#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha\nhttps://cdn.test/a.mpd\n#EXTINF:-1,Unsafe\nhttp://cdn.test/b.mp4\n#EXTINF:-1,Safe\n#EXTVLCOPT:http-referrer=https://origin.test/\n#EXTVLCOPT:http-cookie=secret\nhttps://cdn.test/c.mp4";
        List<PlaylistParser.Entry> entries=PlaylistParser.entries(list);assertEquals(1,entries.size());assertEquals("https://origin.test/",entries.get(0).headers.get("Referer"));assertFalse(entries.get(0).headers.containsKey("Cookie"));
    }
    @Test public void channelsRequireIndianCountryMembershipAndPreserveGenres()throws Exception{
        String list="#EXTM3U\n#EXTINF:-1 tvg-id=\"Indian.in@HD\" group-title=\"News\",Indian News\nhttps://live.test/a.m3u8\n#EXTINF:-1 tvg-id=\"Foreign.us@HD\" group-title=\"News\",Foreign News\nhttps://live.test/b.m3u8";
        List<Library.Title> channels=PlaylistParser.channels(PlaylistParser.entries(list),"Hindi",new HashSet<>(Arrays.asList("Indian.in")));
        assertEquals(1,channels.size());assertEquals("Hindi",channels.get(0).language);assertEquals("News",channels.get(0).group);
    }
    @Test public void seriesGroupSeasonsAndKeepEpisodeUrlsDistinct()throws Exception{
        Library.Addon a=new Library.Addon("tamil","Shows","Shows","https://shows.test/");
        String html="<article><a href='/a'><img alt=''></a><h4>Example Season 2 - 29-10-2026 | Episode 54</h4></article><article><a href='/b'><img alt=''></a><h4>Example Season 1 - 28-10-2026 | Episode 20</h4></article>";
        List<Library.Title> shows=SeriesIndex.group(ProviderParser.catalog(a,html,a.url));assertEquals(1,shows.size());Library.Title series=shows.get(0);assertEquals("Example",series.name);assertEquals(2,series.seasons.length());assertEquals("https://shows.test/a",series.videos.getJSONObject(0).getString("pageUrl"));assertEquals("https://shows.test/b",series.videos.getJSONObject(1).getString("pageUrl"));assertEquals("2026-10-29",series.videos.getJSONObject(0).getString("released"));
    }
    @Test public void paginationAndLanguageGenreFiltersAreBounded(){
        List<Library.Title> titles=new ArrayList<>();for(int i=0;i<101;i++){Library.Title t=new Library.Title("id"+i,"Title "+i,"","https://media.test/a.mp4","","","");t.language=i%2==0?"Telugu":"Hindi";t.group="News";titles.add(t);}
        assertEquals(51,CatalogQuery.filter(titles,"","Telugu","News").size());ProviderRepository.Catalog second=CatalogQuery.page(titles,2,"items");assertEquals(48,second.titles.size());assertEquals("id48",second.titles.get(0).id);assertTrue(second.hasMore);assertEquals(5,CatalogQuery.page(titles,3,"items").titles.size());
    }
    @Test public void probeRecognizesHlsBehindMisleadingMp4ContentType(){
        assertEquals("application/x-mpegURL",StreamProbe.classify("video/mp4","#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\npart.m3u8".getBytes(StandardCharsets.UTF_8)));
        assertEquals("blocked",StreamProbe.classify("text/html","<html>Access denied</html>".getBytes(StandardCharsets.UTF_8)));
    }
    @Test public void encryptedSessionsAreOriginBoundAndUseUniqueNonces()throws Exception{
        SecretKeySpec key=new SecretKeySpec(new byte[32],"AES");String a=SessionVault.seal(key,"https://one.test:443","sensitive-token"),b=SessionVault.seal(key,"https://one.test:443","sensitive-token");assertNotEquals(a,b);assertFalse(a.contains("sensitive-token"));assertEquals("sensitive-token",SessionVault.open(key,"https://one.test:443",a));
        try{SessionVault.open(key,"https://other.test:443",a);fail("A different origin must not decrypt the session");}catch(javax.crypto.AEADBadTagException expected){}
    }
    @Test public void researchExportUsesAnAllowlistInsteadOfSanitizingSecrets()throws Exception{
        JSONObject event=ResearchLog.event("https://evil.test?token=secret-token","Cookie: private-session",false);assertEquals("unknown",event.getString("provider"));assertFalse(event.toString().contains("secret-token"));assertFalse(event.toString().contains("private-session"));assertEquals(4,event.length());
    }
    @Test public void headersDropCredentialsAndCrLfInjection()throws Exception{
        Map<String,String> safe=PlaylistParser.safeHeaders(new JSONObject().put("Cookie","session=secret").put("Authorization","Bearer secret").put("User-Agent","safe\r\nCookie: stolen").put("Referer","https://origin.test/"));assertEquals(1,safe.size());assertEquals("https://origin.test/",safe.get("Referer"));
    }
}
