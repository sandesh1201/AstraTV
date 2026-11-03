package com.astratv.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class TmdbRouteTest {
    @Test public void noServiceAndNoKeyMeansNoRoute(){
        assertNull(TmdbRoute.of("","",""));
        assertNull(TmdbRoute.of(null,null,null));
        assertNull(TmdbRoute.of("http://insecure.example","",""));
        assertNull(TmdbRoute.of("not a url","",""));
    }
    @Test public void proxyRouteKeepsTheCredentialOffTheDevice(){
        TmdbRoute r=TmdbRoute.of("https://catalog.example.app/","tv-1","");
        assertNotNull(r);assertTrue(r.proxied());
        String url=r.url("/discover/movie?include_adult=false&page=2&with_original_language=te");
        assertEquals("https://catalog.example.app/api/tmdb?path=%2Fdiscover%2Fmovie&include_adult=false&page=2&with_original_language=te",url);
        assertFalse(url.contains("api_key"));
        assertEquals("tv-1",r.headers().get(TmdbRoute.CLIENT_HEADER));
        assertEquals("https://catalog.example.app/api/tmdb?path=%2Fgenre%2Fmovie%2Flist",r.url("/genre/movie/list"));
    }
    @Test public void proxyWithoutClientTokenSendsNoHeader(){
        assertTrue(TmdbRoute.of("https://catalog.example.app","","").headers().isEmpty());
    }
    @Test public void developerKeyGoesDirectAndWinsOverService(){
        TmdbRoute r=TmdbRoute.of("https://catalog.example.app","tv-1"," devkey ");
        assertFalse(r.proxied());
        assertEquals("https://api.themoviedb.org/3/tv/5?api_key=devkey",r.url("/tv/5"));
        assertEquals("https://api.themoviedb.org/3/search/multi?query=a&api_key=devkey",r.url("/search/multi?query=a"));
        assertTrue(r.headers().isEmpty());
    }
}
