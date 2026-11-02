package com.astratv.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class GenreTaxonomyTest {
    @Test public void mapsTmdbAndKidsNewsRealitySoapTalk(){
        assertEquals("Drama",GenreTaxonomy.canonical("Drama"));
        assertEquals("Science Fiction",GenreTaxonomy.canonical("Sci-Fi"));
        assertEquals("Kids",GenreTaxonomy.canonical("Children"));
        assertEquals("News",GenreTaxonomy.canonical("news"));
        assertEquals("Reality",GenreTaxonomy.canonical("Reality TV"));
        assertEquals("Soap",GenreTaxonomy.canonical("Soap opera"));
        assertEquals("Talk",GenreTaxonomy.canonical("Talk show"));
        assertEquals("Action & Adventure",GenreTaxonomy.canonical("Action and Adventure"));
    }
    @Test public void unknownTokensBecomeOtherAndAreNeverDropped(){
        assertEquals(Arrays.asList("Other"),GenreTaxonomy.normalize(""));
        assertEquals(Arrays.asList("Other"),GenreTaxonomy.normalize("Entertainment"));
        assertEquals(Arrays.asList("Other"),GenreTaxonomy.normalize("FooBarBaz"));
        List<String> mixed=GenreTaxonomy.normalize("Animation · Trailer");
        assertEquals(Arrays.asList("Animation","Other"),mixed);
        assertTrue(mixed.contains(GenreTaxonomy.OTHER));
    }
    @Test public void titleMatchingUsesGroupAndGenre(){
        Library.Title t=new Library.Title("id","News Night","","https://media.test/a.mp4","","LIVE","Telugu · News");
        t.group="News";t.language="Telugu";
        assertTrue(GenreTaxonomy.matches(t,"News"));
        assertFalse(GenreTaxonomy.matches(t,"Kids"));
        assertTrue(GenreTaxonomy.matches(t,"All"));
    }
}
