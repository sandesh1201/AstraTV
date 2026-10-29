package com.astratv.app;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ProviderParserTest {
    private final Library.Addon movie=new Library.Addon("movierulz","Movies","Movie source","https://catalog.test/");
    @Test public void newsKeepsOnlyHttpsNewsAndGroupsAlternateStreams()throws Exception{
        String data="#EXTM3U\n#EXTINF:-1 tvg-id=\"news.in\" group-title=\"News\",Telugu News (720p)\nhttps://live.test/a.m3u8\n#EXTINF:-1 tvg-id=\"news.in\" group-title=\"News\",Telugu News (1080p)\nhttps://live.test/b.m3u8\n#EXTINF:-1 group-title=\"Entertainment\",Other\nhttps://live.test/c.m3u8\n#EXTINF:-1 group-title=\"News\",Unsafe\nhttp://live.test/d.m3u8";
        List<Library.Title> items=ProviderParser.newsPlaylist(data);assertEquals(1,items.size());assertEquals(2,items.get(0).videos.length());assertEquals("tv",items.get(0).type);
    }

    @Test public void movieCardsAreNativeDataAndDeduplicate(){
        String card="<div class='boxed film'><a title='A Film (2026)' href='/film'><img src='/poster.jpg'></a></div>";
        List<Library.Title> items=ProviderParser.catalog(movie,card+card,"https://catalog.test/");
        assertEquals(1,items.size());assertEquals("https://catalog.test/film",items.get(0).pageUrl);
        assertEquals("https://catalog.test/poster.jpg",items.get(0).poster);assertEquals("2026",items.get(0).year);
        assertEquals("",items.get(0).url); // A web page is never misrepresented as playable media.
    }
    @Test public void catchUpUsesHeadingAndRetainsEpisodeUrl(){
        String html="<article><a href='/show/episode-2'><img alt='Poster' src='/image.webp'></a><h4>Show · Episode 2</h4></article>";
        Library.Addon addon=new Library.Addon("tamil","Tamil","Tamil catch-up","https://source.test/");
        Library.Title t=ProviderParser.catalog(addon,html,addon.url).get(0);
        assertEquals("Show · Episode 2",t.name);assertEquals("series",t.type);assertEquals("tamil",t.provider);
    }
    @Test public void rejectsHttpJavascriptAndTrackingImagesAsVideos(){
        String html="<video src='http://insecure.test/a.mp4'></video><script>src='https://ads.test/banner.jpg'</script><a href='javascript:alert(1)'>Play</a>";
        assertTrue(ProviderParser.mediaUrls(html,"https://source.test/").isEmpty());
        assertFalse(Library.https("https://user:password@source.test/a"));
    }
    @Test public void extractsVideoAndPlainPlayerConfiguration(){
        String html="<video><source src='/video.mp4'></video><script>file: 'https:\\/\\/cdn.test\\/main.m3u8?token=abc'</script>";
        List<String> urls=ProviderParser.mediaUrls(html,"https://source.test/");
        assertEquals(Arrays.asList("https://source.test/video.mp4","https://cdn.test/main.m3u8?token=abc"),urls);
    }
    @Test public void ignoresRadioSourcesAndPopUpAdLinks(){
        String html="<audio><source src='https://radio.test/audio.mp4'></audio><a target='_blank' href='https://ad.test/popup'>Ad</a>";
        assertTrue(ProviderParser.mediaUrls(html,"https://source.test/").isEmpty());
        assertTrue(ProviderParser.embeds(html,"https://source.test/").isEmpty());
    }
    @Test public void followsOnlyExplicitPlayerEmbedsWithoutExecutingScripts(){
        String html="<a class='st-video-poster' href='https://host.test/v/12'>Watch</a><script>var locations = [\"https:\\/\\/other.test\\/v\\/33\"]; window.open('https://ad.test');</script>";
        assertEquals(Arrays.asList("https://host.test/v/12","https://other.test/v/33"),ProviderParser.embeds(html,"https://source.test/"));
    }
    @Test public void metadataIncludesSynopsisCastAndGenre(){
        Library.Title t=new Library.Title("id","Film","","","","","");
        ProviderParser.details(t,"<div class='synopsis-section'><p>A story.</p></div><div class='movie-info-block'><a href='/actor/person'>A Person</a><a href='/genre/drama'>Drama</a></div>","https://source.test/");
        assertEquals("A story.",t.description);assertEquals("A Person",t.cast);assertEquals("Drama",t.genre);
    }
    @Test public void validatesImportsWithoutReplacingProviderIds()throws Exception{
        List<Library.Title> items=Library.parse("[{\"id\":\"one\",\"title\":\"Mine\",\"url\":\"https://media.test/film.mp4\",\"provider\":\"spoof\"}]");
        assertEquals("library:one",items.get(0).key());
        try{Library.parse("[{\"id\":\"one\",\"title\":\"Mine\",\"url\":\"https://media.test/film.mp4\"},{\"id\":\"one\",\"title\":\"Other\",\"url\":\"https://media.test/b.mp4\"}]");fail("Duplicate IDs must fail");}catch(JSONException expected){}
    }
    @Test public void manifestMetadataRetainsEpisodesAndNamespacedKeys()throws Exception{
        JSONObject json=new JSONObject("{\"id\":\"series1\",\"name\":\"A Series\",\"genres\":[\"Drama\"],\"cast\":[\"Actor One\"],\"videos\":[{\"id\":\"episode1\",\"title\":\"Pilot\"}]}");
        Library.Title title=ProviderParser.meta(json,"custom","My provider");
        assertEquals("custom:series1",title.key());assertEquals("Actor One",title.cast);assertEquals("Drama",title.genre);assertEquals(1,title.videos.length());
        assertEquals("custom:series1",Library.Title.from(title.json()).key());
    }
}
