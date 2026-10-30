package com.astratv.app;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.io.IOException;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Tests the observed unofficial API contract; these are not live-server claims. */
public class NetMirrorProviderTest {
    private SharedPreferences preferences(){
        return (SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
            if(method.getName().equals("getString")){if(args[0].equals("netmirror.api"))return "https://provider.test/mobile";if(args[0].equals("netmirror.session"))return "user-issued-session";return args[1];}throw new UnsupportedOperationException(method.getName());
        });
    }
    private NetMirrorProvider client(NetMirrorProvider.Transport transport){return new NetMirrorProvider(preferences(),transport,origin -> "user-issued-session");}
    @Test public void separatePlatformsKeepTheirRoutesCookiesAndIdentity()throws Exception{
        List<String> calls=new ArrayList<>();NetMirrorProvider provider=client((url,h)->{calls.add(url+"|"+h.get("Cookie"));return "{\"searchResult\":[{\"id\":\"same\",\"t\":\"Example\"}]}";});
        Library.Title prime=provider.catalog("film","netmirror_pv").get(0),hotstar=provider.catalog("film","netmirror_hs").get(0);
        assertEquals("netmirror_pv:pv~same",prime.key());assertEquals("netmirror_hs:hs~same",hotstar.key());assertTrue(calls.get(0).contains("/pv/search.php"));assertTrue(calls.get(0).contains("ott=pv"));assertTrue(calls.get(1).contains("/hs/search.php"));assertTrue(calls.get(1).contains("ott=hs"));
    }
    @Test public void readsNativeCatalogAndSearchUsingSourceProtocol()throws Exception{
        List<String> requested=new ArrayList<>();NetMirrorProvider provider=client((url,h)->{
            requested.add(url);assertTrue(h.get("Cookie").contains("user-issued-session"));
            if(url.contains("search.php"))return "{\"searchResult\":[{\"id\":\"123\",\"t\":\"Example\",\"y\":\"2026\"}]}";
            return "<div class='tray-container'><a data-post='123'><img alt='Example'></a></div>";
        });
        assertEquals("netmirror:nf~123",provider.catalog("").get(0).key());
        assertEquals("Example",provider.catalog("two words").get(0).name);
        assertTrue(requested.get(1).endsWith("/search.php?s=two%20words"));
    }
    @Test public void readsSeriesDetailsAndEpisodes()throws Exception{
        NetMirrorProvider provider=client((url,h)->url.contains("episodes.php")?
            "{\"episodes\":[{\"id\":\"456\",\"t\":\"Pilot\",\"s\":\"1\",\"ep\":\"1\"}]}":
            "{\"title\":\"A series\",\"type\":\"s\",\"cast\":\"Actor\",\"genre\":\"Drama\",\"season\":[{\"s\":\"1\",\"id\":\"s1\"}],\"episodes\":[]}");
        Library.Title title=new Library.Title("nf~123","","","","","","");title.provider="netmirror";
        Library.Title detailed=provider.details(title);assertEquals("series",detailed.type);assertEquals("Actor",detailed.cast);assertEquals(1,detailed.seasons.length());
        Library.Title episode=provider.episodes(title,1).get(0);assertEquals("nf~456",episode.id);assertEquals("episode",episode.type);
    }
    @Test public void readsDirectPlaylistAndSubtitlesWithoutLeakingSessionToCdn()throws Exception{
        NetMirrorProvider provider=client((url,h)->"[{\"sources\":[{\"file\":\"https://cdn.test/master.m3u8\",\"label\":\"Auto\"}],\"tracks\":[{\"kind\":\"captions\",\"label\":\"English\",\"file\":\"//cdn.test/subs.vtt\"}]}]");
        Library.Title title=new Library.Title("nf~123","","","","","","");
        ProviderRepository.Stream stream=provider.streams(title).get(0);assertEquals("https://cdn.test/master.m3u8",stream.url);assertEquals(1,stream.subtitles.length());assertFalse(stream.headers.containsKey("Cookie"));
    }
    @Test public void rejectsGatePagesInsteadOfTreatingThemAsCatalogs()throws Exception{
        NetMirrorProvider provider=client((url,h)->"<html><title>Redirecting...</title><script>window.location='/gate'</script></html>");
        try{provider.catalog("");fail("A gate page is not a catalog");}catch(IOException e){assertTrue(e.getMessage().contains("no catalog"));}
    }
}
