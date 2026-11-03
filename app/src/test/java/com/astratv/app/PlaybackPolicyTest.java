package com.astratv.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackPolicyTest {
    @Test public void vpnRequirementBlocksPlaybackUntilATunnelExists(){
        assertTrue(PlaybackPolicy.allow(false,false));
        assertFalse(PlaybackPolicy.allow(true,false));
        assertTrue(PlaybackPolicy.allow(true,true));
    }
    @Test public void qualityIsLabeledOnlyWhenTheSourceNameStatesIt(){
        assertEquals("1080p",PlaybackPolicy.qualityLabel("Source 1080p"));
        assertEquals("720p",PlaybackPolicy.qualityLabel("HD 720"));
        assertEquals("4K",PlaybackPolicy.qualityLabel("2160p"));
        assertEquals("",PlaybackPolicy.qualityLabel("Source 1"));
        assertEquals("Source 1",PlaybackPolicy.sourceChoice("Source 1"));
        assertEquals("1080p · HLS",PlaybackPolicy.sourceChoice("1080p · HLS"));
        assertEquals("Mirror 720 · 720p",PlaybackPolicy.sourceChoice("Mirror 720"));
    }
    @Test public void retriesOnlyTransientErrorsWithBoundedBackoff(){
        assertEquals(2000,PlaybackPolicy.retryDelayMs(0,2001));
        assertEquals(4000,PlaybackPolicy.retryDelayMs(1,2002));
        assertEquals(8000,PlaybackPolicy.retryDelayMs(2,1003));
        assertEquals(-1,PlaybackPolicy.retryDelayMs(3,2001));
        assertEquals(0,PlaybackPolicy.retryDelayMs(0,1002));
        assertEquals(-1,PlaybackPolicy.retryDelayMs(0,2005));
        assertEquals(-1,PlaybackPolicy.retryDelayMs(0,6001));
        assertEquals(-1,PlaybackPolicy.retryDelayMs(0,3001));
    }
    @Test public void heightLabelsNeverRoundUp(){
        assertEquals("4K",PlaybackPolicy.heightLabel(2160));
        assertEquals("1080p",PlaybackPolicy.heightLabel(1080));
        assertEquals("576p",PlaybackPolicy.heightLabel(576));
        assertEquals("720p",PlaybackPolicy.heightLabel(800));
        assertEquals("",PlaybackPolicy.heightLabel(0));
    }
}
