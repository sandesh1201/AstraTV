package com.astratv.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static com.astratv.app.CatalogTestData.*;

public class DiscoverQueryTest {
    @Test public void defaultMovieQueryIsNewestIndianReleasesUpToToday(){
        String p=new DiscoverQuery().path(1,"2026-11-02");
        assertTrue(p.startsWith("/discover/movie?"));
        assertTrue(p.contains("sort_by=primary_release_date.desc"));
        assertTrue(p.contains("primary_release_date.lte=2026-11-02"));
        assertTrue(p.contains("region=IN"));
        assertFalse(p.contains("api_key"));
    }
    @Test public void filtersMapToTmdbParameters(){
        DiscoverQuery q=new DiscoverQuery();q.languageCode="te";q.year=2025;q.genreId=28;q.minRating=7;q.sort="rating";
        String p=q.path(3,"2026-11-02");
        assertTrue(p.contains("page=3"));
        assertTrue(p.contains("with_original_language=te"));
        assertFalse(p.contains("region=IN"));
        assertTrue(p.contains("primary_release_year=2025"));
        assertTrue(p.contains("with_genres=28"));
        assertTrue(p.contains("vote_average.gte=7"));
        assertTrue(p.contains("sort_by=vote_average.desc"));
    }
    @Test public void tvQueryUsesAirDates(){
        DiscoverQuery q=new DiscoverQuery();q.kind="tv";
        String p=q.path(1,"2026-11-02");
        assertTrue(p.startsWith("/discover/tv?"));
        assertTrue(p.contains("first_air_date.lte=2026-11-02"));
        assertTrue(p.contains("with_origin_country=IN"));
    }
    @Test public void recentlyAddedAndVerifiedQualityAreLocal(){
        DiscoverQuery q=new DiscoverQuery();assertTrue(q.remote());
        q.sort="added";assertFalse(q.remote());
        q.sort="newest";q.minHeight=1080;assertFalse(q.remote());
    }
    @Test public void localMatchingRespectsEveryFilter(){
        CatalogItem c=movie("p","a","Film","2024-05-01");c.title.language="Telugu";c.verifiedHeight=720;c.rating=7.5;
        DiscoverQuery q=new DiscoverQuery();assertTrue(q.matches(c));
        q.languageCode="te";assertTrue(q.matches(c));
        q.languageCode="ta";assertFalse(q.matches(c));q.languageCode="";
        q.year=2023;assertFalse(q.matches(c));q.year=2024;
        q.minHeight=1080;assertFalse("720p is not 1080p",q.matches(c));q.minHeight=720;assertTrue(q.matches(c));
        q.minRating=8;assertFalse(q.matches(c));q.minRating=0;
        q.kind="tv";assertFalse(q.matches(c));
    }
    @Test public void newestUsesReleaseDateAndAddedUsesFirstSeen(){
        CatalogItem older=movie("p","a","A","2023-01-01"),newer=movie("p","b","B","2025-03-01");
        older.firstSeen=2000;newer.firstSeen=1000;
        assertEquals("b",DiscoverQuery.order(Arrays.asList(older,newer),"newest").get(0).title.id);
        assertEquals("a",DiscoverQuery.order(Arrays.asList(older,newer),"added").get(0).title.id);
    }
    @Test public void persistedFiltersRoundTrip(){
        DiscoverQuery q=new DiscoverQuery();q.languageCode="hi";q.year=2022;q.genreName="Action";q.genreId=28;q.sort="trending";q.minRating=6.5f;q.minHeight=720;
        DiscoverQuery back=DiscoverQuery.from(q.json().toString(),"movie");
        assertEquals(q.key(),back.key());
        assertEquals("newest",DiscoverQuery.from("{\"sort\":\"bogus\"}","movie").sort);
        assertEquals("newest",DiscoverQuery.from("not json","tv").sort);
    }
}
