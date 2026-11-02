package com.astratv.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class TrailerResolverTest {
    @Test public void providerUrlWinsOverTmdbAndSearch(){
        TrailerResolver.Source s=TrailerResolver.pick("https://cdn.test/official.mp4","abc","def");
        assertEquals(TrailerResolver.Kind.PROVIDER,s.kind);assertEquals("https://cdn.test/official.mp4",s.value);
    }
    @Test public void tmdbYoutubeKeyBeatsSearch(){
        TrailerResolver.Source s=TrailerResolver.pick("","tmdbKey","searchKey");
        assertEquals(TrailerResolver.Kind.TMDB_YOUTUBE,s.kind);assertEquals("tmdbKey",s.value);assertTrue(s.youtube());
    }
    @Test public void searchIsLastAndHttpProviderIsIgnored(){
        assertEquals(TrailerResolver.Kind.YOUTUBE_SEARCH,TrailerResolver.pick("http://insecure.test/a.mp4","","searchKey").kind);
        assertNull(TrailerResolver.pick("","",""));
        assertNull(TrailerResolver.pick(null,null,null));
    }
    @Test public void youtubeEmbedAcceptsOnlyNocookieHttps(){
        assertTrue(TrailerResolver.youtubeEmbed("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"));
        assertFalse(TrailerResolver.youtubeEmbed("https://evil.test/embed/dQw4w9WgXcQ"));
        assertFalse(TrailerResolver.youtubeEmbed("http://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"));
        assertTrue(TrailerResolver.youtubePlayerResource("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"));
        assertFalse(TrailerResolver.youtubePlayerResource("https://doubleclick.net/ad"));
    }
}
