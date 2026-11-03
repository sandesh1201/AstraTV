package com.astratv.app;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TmdbParseTest {
    private static final Map<Integer,String> GENRES=new HashMap<>();
    static{GENRES.put(28,"Action");GENRES.put(18,"Drama");}

    @Test public void moviePageMapsFieldsAndDropsEntriesWithoutArtwork()throws Exception{
        JSONObject page=new JSONObject("{\"results\":["
            +"{\"id\":101,\"title\":\"Pushpa 2\",\"poster_path\":\"/p.jpg\",\"backdrop_path\":\"/b.jpg\",\"release_date\":\"2024-12-05\",\"original_language\":\"te\",\"genre_ids\":[28,18],\"vote_average\":7.2,\"vote_count\":900,\"popularity\":88.5,\"overview\":\"o\"},"
            +"{\"id\":102,\"title\":\"No Poster\",\"release_date\":\"2024-01-01\"},"
            +"{\"id\":0,\"title\":\"Bad\",\"poster_path\":\"/x.jpg\"}]}");
        List<CatalogItem> out=TmdbCatalogSource.parse(page,false,GENRES);
        assertEquals(1,out.size());
        CatalogItem c=out.get(0);
        assertEquals("tmdb:movie:101",c.key());
        assertEquals(CatalogItem.MOVIE,c.kind);
        assertEquals(101,c.tmdbId);
        assertEquals("Telugu",c.title.language);
        assertEquals("te",c.languageCode);
        assertEquals("2024-12-05",c.releaseDate);
        assertEquals("Action · Drama",c.title.genre);
        assertEquals("https://image.tmdb.org/t/p/w342/p.jpg",c.title.poster);
        assertEquals("https://image.tmdb.org/t/p/w780/b.jpg",c.title.background);
        assertEquals(7.2,c.rating,0.001);
        assertEquals("TMDB",c.ratingSource);
        assertFalse("metadata is never playable by itself",c.playable);
        assertEquals(0,c.verifiedHeight);
    }
    @Test public void tvPageUsesNameAndAirDate()throws Exception{
        JSONObject page=new JSONObject("{\"results\":[{\"id\":7,\"name\":\"Serial\",\"poster_path\":\"/s.jpg\",\"first_air_date\":\"2025-02-01\",\"original_language\":\"ta\"}]}");
        CatalogItem c=TmdbCatalogSource.parse(page,true,GENRES).get(0);
        assertEquals("tmdb:tv:7",c.key());assertEquals(CatalogItem.SERIES,c.kind);assertEquals("Tamil",c.title.language);
        assertEquals(0,c.rating,0);
        assertArrayEquals(new int[]{1,7},TmdbCatalogSource.ids(c.title));
    }
    @Test public void idsOnlyForTmdbTitles(){
        Library.Title t=new Library.Title("movie:5","x","","","","","");t.provider="tmdb";
        assertArrayEquals(new int[]{0,5},TmdbCatalogSource.ids(t));
        t.provider="library";assertNull(TmdbCatalogSource.ids(t));
    }
    @Test public void seasonEpisodesCarryNumbersAndAirDates()throws Exception{
        Library.Title show=new Library.Title("tv:7","Serial","","","","2025","Drama");show.language="Tamil";show.background="https://img/b.jpg";
        JSONObject season=new JSONObject("{\"episodes\":[{\"season_number\":2,\"episode_number\":4,\"name\":\"Return\",\"air_date\":\"2024-03-09\",\"still_path\":\"/e.jpg\",\"vote_count\":3,\"vote_average\":8}]}");
        CatalogItem e=TmdbCatalogSource.parseSeason(season,7,show).get(0);
        assertEquals("tv:7:s2:e4",e.title.id);
        assertEquals("E4 · Return",e.title.name);
        assertEquals(2,e.title.season);assertEquals(4,e.title.episode);
        assertEquals("2024-03-09",e.releaseDate);
        assertEquals("S2 · E4 · 9 Mar 2024",e.episodeLabel);
        assertEquals("Tamil",e.title.language);
        assertFalse(e.playable);
    }
    @Test public void lastEpisodeUpdatesSeriesCard()throws Exception{
        CatalogItem c=new CatalogItem(new Library.Title("tv:7","Serial","","","","2025",""));c.kind=CatalogItem.SERIES;
        JSONObject show=new JSONObject("{\"last_episode_to_air\":{\"season_number\":1,\"episode_number\":120,\"air_date\":\"2025-06-10\"},\"seasons\":[{\"season_number\":0,\"episode_count\":2},{\"season_number\":1,\"episode_count\":120}]}");
        TmdbCatalogSource.applyLastEpisode(c,show);
        assertTrue(c.episodeLabel.startsWith("S1 · E120"));
        assertEquals(TmdbCatalogSource.epoch("2025-06-10"),c.sourceUpdatedAt);
        assertEquals("specials are skipped",1,c.title.seasons.length());
    }
    @Test public void watchProvidersGroupByOfferType()throws Exception{
        JSONObject body=new JSONObject("{\"results\":{\"IN\":{\"flatrate\":[{\"provider_name\":\"Netflix\"},{\"provider_name\":\"Netflix\"}],\"rent\":[{\"provider_name\":\"Apple TV\"}]},\"US\":{\"buy\":[{\"provider_name\":\"X\"}]}}}");
        assertEquals(Arrays.asList("Stream: Netflix","Rent: Apple TV"),TmdbCatalogSource.parseWatchProviders(body,"IN"));
        assertTrue(TmdbCatalogSource.parseWatchProviders(body,"GB").isEmpty());
    }
    @Test public void languageNames(){
        assertEquals("Telugu",TmdbCatalogSource.languageName("te"));
        assertEquals("Korean",TmdbCatalogSource.languageName("ko"));
        assertEquals("",TmdbCatalogSource.languageName(""));
    }
}
