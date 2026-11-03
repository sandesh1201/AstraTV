package com.astratv.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static com.astratv.app.CatalogTestData.*;

public class PlaybackSourceResolverTest {
    private final InMemoryCatalogStorage store=new InMemoryCatalogStorage();
    private final PlaybackSourceResolver resolver=new PlaybackSourceResolver(store);

    @Test public void metadataOnlyTitleIsNotPlayable(){
        PlaybackSourceResolver.Resolution r=resolver.resolve(tmdb(1,"Devara","2024-09-27"));
        assertFalse(r.playable());assertNull(r.title);
    }
    @Test public void licensedProviderWithSameWorkMakesItPlayable(){
        CatalogItem licensed=movie(EinthusanSimulatedProvider.ID,"e1","Devara (Hindi)","2024-09-27");licensed.playable=true;licensed.verifiedHeight=1080;licensed.title.providerName="Partner";
        store.replaceRows("licensed",Collections.singletonList(new CatalogSource.Batch("licensed.playable",Collections.singletonList(licensed),false)),0);
        PlaybackSourceResolver.Resolution r=resolver.resolve(tmdb(1,"Devara","2024-09-27"));
        assertEquals(PlaybackSourceResolver.Kind.LICENSED_MATCH,r.kind);
        assertSame(licensed.title,r.title);
        assertTrue(r.reason.contains("Partner"));
    }
    @Test public void differentYearIsNotAMatch(){
        CatalogItem licensed=movie(EinthusanSimulatedProvider.ID,"e1","Devdas","1955-01-01");licensed.playable=true;
        store.replaceRows("licensed",Collections.singletonList(new CatalogSource.Batch("licensed.playable",Collections.singletonList(licensed),false)),0);
        assertFalse(resolver.resolve(tmdb(9,"Devdas","2002-07-12")).playable());
    }
    @Test public void eventsAreNeverPlayable(){
        CatalogItem e=new CatalogItem(new Library.Title("1","Final","","https://x/stream.m3u8","","",""));e.kind=CatalogItem.EVENT;e.status="live";
        assertEquals(PlaybackSourceResolver.Kind.NOT_AVAILABLE,resolver.resolve(e).kind);
    }
    @Test public void userLibraryPlaysDirectly(){
        CatalogItem c=CatalogItem.of(new Library.Title("m","Home video","","https://x/v.mp4","","2020",""));
        assertEquals(PlaybackSourceResolver.Kind.DIRECT,resolver.resolve(c).kind);
        assertTrue(c.playable);
    }
    @Test public void channelReasonReflectsHealth(){
        CatalogItem ch=channel("c","News");ch.health="failed";
        PlaybackSourceResolver.Resolution r=resolver.resolve(ch);
        assertEquals(PlaybackSourceResolver.Kind.CHANNEL,r.kind);assertTrue(r.reason.contains("failed"));
    }
}
