package com.astratv.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static com.astratv.app.CatalogTestData.*;

public class CatalogDeduplicationTest {
    @Test public void stripsDubAndEditionMarkers(){
        assertEquals("Pushpa 2",CatalogDeduplication.baseName("Pushpa 2 (Hindi)"));
        assertEquals("Leo",CatalogDeduplication.baseName("Leo - Telugu Dubbed"));
        assertEquals("RRR",CatalogDeduplication.baseName("RRR [Tamil]"));
        assertEquals("Hindi Medium",CatalogDeduplication.baseName("Hindi Medium"));
        assertEquals("Hindi",CatalogDeduplication.dubLanguage("Pushpa 2 (Hindi Dubbed)"));
        assertEquals("",CatalogDeduplication.dubLanguage("Hindi Medium"));
    }
    @Test public void sameTmdbIdMergesAcrossSources(){
        CatalogItem a=tmdb(10,"Devara","2024-09-27"),b=tmdb(10,"Devara Part 1","2024-09-27");b.title.provider="other";b.title.providerName="Other";
        List<CatalogItem> out=CatalogDeduplication.merge(Arrays.asList(a,b));
        assertEquals(1,out.size());
    }
    @Test public void sameNameAndYearWithDifferentTmdbIdsStaySeparate(){
        List<CatalogItem> out=CatalogDeduplication.merge(Arrays.asList(tmdb(1,"Drishyam","2015-07-31"),tmdb(2,"Drishyam","2015-07-17")));
        assertEquals(2,out.size());
    }
    @Test public void dubbedCopyFoldsIntoOriginalAndKeepsAudio(){
        CatalogItem original=movie("p1","a","Kalki 2898 AD","2024-06-27");original.title.language="Telugu";
        CatalogItem dub=movie("p2","b","Kalki 2898 AD (Hindi Dubbed)","2024-06-27");
        dub.playable=true;dub.title.providerName="Licensed";original.firstSeen=500;dub.firstSeen=100;
        List<CatalogItem> out=CatalogDeduplication.merge(Arrays.asList(original,dub));
        assertEquals(1,out.size());
        CatalogItem rep=out.get(0);
        assertTrue("playable copy represents the group",rep.playable);
        assertTrue(rep.audio.containsAll(Arrays.asList("Telugu","Hindi")));
        assertEquals(100,rep.firstSeen);
    }
    @Test public void differentYearsAreDifferentWorks(){
        assertEquals(2,CatalogDeduplication.merge(Arrays.asList(movie("p","a","Devdas","2002-07-12"),movie("p","b","Devdas","1955-01-01"))).size());
    }
    @Test public void channelsAreNeverMergedByName(){
        assertEquals(2,CatalogDeduplication.merge(Arrays.asList(channel("a","News 24"),channel("b","News 24"))).size());
    }
    @Test public void duplicateKeysCollapse(){
        assertEquals(1,CatalogDeduplication.merge(Arrays.asList(movie("p","a","X","2020-01-01"),movie("p","a","X","2020-01-01"))).size());
    }
}
