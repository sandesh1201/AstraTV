package com.astratv.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Playback candidates, error classification and retry policy, without a device. */
public class PlaybackReliabilityTest {
    private static final long NOW=1_800_000_000_000L;

    private static PlaybackCandidate candidate(String url){return new PlaybackCandidate("partner","1080p · HLS",url,"",null,null,null,1080,true,0,"","",null);}

    @Test public void streamTypeComesFromMimeThenExtension(){
        assertEquals(PlaybackCandidate.Type.HLS,PlaybackCandidate.typeOf("https://cdn.example/x/master.m3u8?token=a",""));
        assertEquals(PlaybackCandidate.Type.DASH,PlaybackCandidate.typeOf("https://cdn.example/x/manifest.mpd",""));
        assertEquals(PlaybackCandidate.Type.PROGRESSIVE,PlaybackCandidate.typeOf("https://cdn.example/film.mp4",""));
        assertEquals(PlaybackCandidate.Type.HLS,PlaybackCandidate.typeOf("https://cdn.example/play","application/x-mpegURL"));
        assertEquals(PlaybackCandidate.Type.UNKNOWN,PlaybackCandidate.typeOf("https://cdn.example/play",""));
        assertEquals("application/dash+xml",candidate("https://cdn.example/a.mpd").mimeType);
    }
    @Test public void trailersPostersPagesAndMetadataUrlsAreNotStreams(){
        for(String url:new String[]{"https://www.youtube.com/watch?v=abc","https://image.tmdb.org/t/p/w342/a.jpg","https://partner.example/movie/123.html","https://api.themoviedb.org/3/movie/1"})
            assertNotNull(url,candidate(url).problem(NOW,"IN",false));
        assertNull(candidate("https://cdn.example/master.m3u8").problem(NOW,"IN",false));
    }
    @Test public void insecureUnavailableAndRegionLockedCandidatesAreRejected(){
        assertTrue(candidate("http://cdn.example/a.m3u8").problem(NOW,"IN",false).contains("non-HTTPS"));
        PlaybackCandidate off=new PlaybackCandidate("p","s","https://cdn.example/a.m3u8","",null,null,null,0,false,0,"","",null);
        assertTrue(off.problem(NOW,"IN",false).contains("not available"));
        PlaybackCandidate us=new PlaybackCandidate("p","s","https://cdn.example/a.m3u8","",null,null,null,0,true,0,"","",new HashSet<>(Collections.singletonList("US")));
        assertTrue(us.problem(NOW,"IN",false).contains("only in US"));
        assertNull(us.problem(NOW,"us",false));
        assertNull("unknown device country is not treated as ineligible",us.problem(NOW,"",false));
    }
    @Test public void drmNeedsWidevineSupportAndASecureLicenseServer(){
        PlaybackCandidate drm=new PlaybackCandidate("p","s","https://cdn.example/a.mpd","",null,null,null,0,true,0,"widevine","https://license.example/wv",null);
        assertNull(drm.problem(NOW,"IN",true));
        assertTrue(drm.problem(NOW,"IN",false).contains("Widevine"));
        assertNotNull(new PlaybackCandidate("p","s","https://cdn.example/a.mpd","",null,null,null,0,true,0,"widevine","",null).problem(NOW,"IN",true));
        assertTrue(new PlaybackCandidate("p","s","https://cdn.example/a.mpd","",null,null,null,0,true,0,"playready","https://l.example",null).problem(NOW,"IN",true).contains("playready"));
    }
    @Test public void signedUrlExpiryIsReadFromCommonFormats(){
        long exp=NOW/1000+3600;
        assertEquals(exp*1000,PlaybackCandidate.expiryFromUrl("https://d1.cloudfront.net/v.m3u8?Expires="+exp+"&Signature=x&Key-Pair-Id=y"));
        assertEquals(exp*1000,PlaybackCandidate.expiryFromUrl("https://cdn.example/v.m3u8?hdnts=st=1~exp="+exp+"~acl=/*~hmac=ab"));
        assertEquals(java.time.Instant.parse("2027-01-15T10:00:00Z").toEpochMilli()+900_000L,PlaybackCandidate.expiryFromUrl("https://s3.example/v.mp4?X-Amz-Date=20270115T100000Z&X-Amz-Expires=900&X-Amz-Signature=z"));
        assertEquals("no expiry",0,PlaybackCandidate.expiryFromUrl("https://cdn.example/v.m3u8?page=2&exp=abc"));
        assertEquals(0,PlaybackCandidate.expiryFromUrl("https://cdn.example/v.m3u8"));
    }
    @Test public void expiredLinksAreRefusedBeforePlaybackWithAGraceWindow(){
        long exp=NOW/1000+10;String url="https://cdn.example/v.m3u8?expires="+exp;
        PlaybackCandidate c=candidate(url);
        assertTrue(c.signed());assertTrue("inside the 30 s grace window",c.expired(NOW));
        assertTrue(c.problem(NOW,"IN",false).contains("expired"));
        assertFalse(candidate("https://cdn.example/v.m3u8?expires="+(NOW/1000+3600)).expired(NOW));
    }
    @Test public void errorsAreClassifiedIndividually(){
        assertEquals(PlaybackErrors.Category.NETWORK,PlaybackErrors.classify(2001,0,false,true).category);
        assertTrue(PlaybackErrors.classify(2001,0,false,false).message.contains("No network"));
        assertEquals(PlaybackErrors.Category.AUTHORIZATION,PlaybackErrors.classify(2004,403,false,true).category);
        assertEquals(PlaybackErrors.Category.EXPIRED_URL,PlaybackErrors.classify(2004,403,true,true).category);
        assertTrue(PlaybackErrors.classify(2004,410,true,true).refreshUrl());
        assertEquals(PlaybackErrors.Category.NOT_FOUND,PlaybackErrors.classify(2004,404,false,true).category);
        assertEquals(PlaybackErrors.Category.PROVIDER_OUTAGE,PlaybackErrors.classify(2004,503,false,true).category);
        assertEquals(PlaybackErrors.Category.PROVIDER_OUTAGE,PlaybackErrors.classify(2004,429,false,true).category);
        assertEquals(PlaybackErrors.Category.UNSUPPORTED_CODEC,PlaybackErrors.classify(4005,0,false,true).category);
        assertEquals(PlaybackErrors.Category.INVALID_MANIFEST,PlaybackErrors.classify(3002,0,false,true).category);
        assertEquals(PlaybackErrors.Category.DRM,PlaybackErrors.classify(6004,0,false,true).category);
        assertEquals(PlaybackErrors.Category.DECODER,PlaybackErrors.classify(4001,0,false,true).category);
        assertEquals(PlaybackErrors.Category.BEHIND_LIVE,PlaybackErrors.classify(1002,0,false,true).category);
        assertEquals(PlaybackErrors.Category.INSECURE,PlaybackErrors.classify(2007,0,false,true).category);
    }
    @Test public void onlyTransientFailuresRetryWithBoundedBackoff(){
        PlaybackErrors.Verdict net=PlaybackErrors.classify(2002,0,false,true);
        long previous=0;
        for(int i=0;i<PlaybackErrors.MAX_TRANSIENT_RETRIES;i++){long d=PlaybackErrors.retryDelayMs(net,i);assertTrue(d>previous);assertTrue(d<=16000);previous=d;}
        assertEquals(-1,PlaybackErrors.retryDelayMs(net,PlaybackErrors.MAX_TRANSIENT_RETRIES));
        assertEquals(-1,PlaybackErrors.retryDelayMs(PlaybackErrors.classify(2004,404,false,true),0));
        assertEquals(-1,PlaybackErrors.retryDelayMs(PlaybackErrors.classify(2004,403,false,true),0));
        assertEquals(-1,PlaybackErrors.retryDelayMs(PlaybackErrors.classify(4005,0,false,true),0));
        assertEquals("expired links are refreshed, not retried",-1,PlaybackErrors.retryDelayMs(PlaybackErrors.classify(2004,403,true,true),0));
        PlaybackErrors.Verdict decoder=PlaybackErrors.classify(4001,0,false,true);
        assertTrue(PlaybackErrors.retryDelayMs(decoder,0)>0);assertEquals(-1,PlaybackErrors.retryDelayMs(decoder,1));
        assertEquals(0,PlaybackErrors.retryDelayMs(PlaybackErrors.classify(1002,0,false,true),0));
    }
}
