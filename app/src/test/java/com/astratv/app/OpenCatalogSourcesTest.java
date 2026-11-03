package com.astratv.app;

import org.json.*;
import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class OpenCatalogSourcesTest {
    private static JSONObject lit(String v)throws JSONException{return new JSONObject().put("value",v);}

    @Test public void wikidataRowsMapToMetadataOnlyFilms()throws Exception{
        JSONArray rows=new JSONArray()
            .put(new JSONObject().put("film",lit("http://www.wikidata.org/entity/Q141781287")).put("lang",lit("http://www.wikidata.org/entity/Q1568"))
                .put("name",lit("Udta Teer")).put("released",lit("2026-10-23T00:00:00Z")).put("about",lit("2026 Indian Hindi-language spy comedy film"))
                .put("tmdbId",lit("1375156")).put("director",lit("Aakash Kaushik")).put("genres",lit("action comedy film|comedy drama|spy comedy film|thriller film"))
                .put("image",lit("http://commons.wikimedia.org/wiki/Special:FilePath/Poster%20X.jpg")))
            .put(new JSONObject().put("film",lit("http://www.wikidata.org/entity/Q2")).put("lang",lit("http://www.wikidata.org/entity/Q8097")).put("name",lit("Q2")).put("released",lit("2026-01-01T00:00:00Z")))
            .put(new JSONObject().put("film",lit("http://www.wikidata.org/entity/Q3")).put("lang",lit("http://www.wikidata.org/entity/Q999")).put("name",lit("Other")).put("released",lit("2026-01-01T00:00:00Z")))
            .put(new JSONObject().put("film",lit("http://www.wikidata.org/entity/Q4")).put("lang",lit("http://www.wikidata.org/entity/Q5885")).put("name",lit("No Date")));
        List<CatalogItem> out=WikidataFilmSource.parse(new JSONObject().put("results",new JSONObject().put("bindings",rows)));
        assertEquals(1,out.size());
        CatalogItem c=out.get(0);
        assertEquals("wikidata:film:Q141781287",c.key());
        assertEquals("Udta Teer",c.title.name);assertEquals("hi",c.languageCode);assertEquals("Hindi",c.title.language);
        assertEquals("2026-10-23",c.releaseDate);assertEquals("2026",c.title.year);
        assertEquals(-1,c.firstSeen);
        assertEquals("Action comedy · Comedy drama · Spy comedy",c.title.genre);
        assertEquals("Director · Aakash Kaushik",c.title.cast);
        assertEquals(1375156,c.tmdbId);
        assertEquals("https://commons.wikimedia.org/wiki/Special:FilePath/Poster%20X.jpg?width=342",c.title.poster);
        assertFalse(c.playable);assertEquals("",c.title.url);
    }
    @Test public void wikidataQueryIsBoundedToReleasedFilms(){
        String q=WikidataFilmSource.query(LocalDate.of(2026,11,2));
        assertTrue(q.contains("\"2026-11-02T23:59:59Z\""));
        assertTrue(q.contains("\"2025-05-11T00:00:00Z\""));
        for(String[] l:WikidataFilmSource.LANGUAGES)assertTrue(q.contains("wd:"+l[1]));
    }

    private static JSONObject show(int id,String name,String language,double weight,boolean image,String...genres)throws JSONException{
        JSONObject s=new JSONObject().put("id",id).put("name",name).put("language",language).put("premiered","2020-01-05").put("weight",weight)
            .put("summary","<p>About <b>"+name+"</b> &amp; more.</p>").put("genres",new JSONArray(Arrays.asList(genres)))
            .put("rating",new JSONObject().put("average",7.5)).put("webChannel",new JSONObject().put("name","Netflix"));
        if(image)s.put("image",new JSONObject().put("medium","http://static.tvmaze.com/m/"+id+".jpg").put("original","https://static.tvmaze.com/o/"+id+".jpg"));
        return s;
    }
    private static JSONObject ep(JSONObject show,boolean embedded,int season,int number,String airstamp)throws JSONException{
        JSONObject e=new JSONObject().put("season",season).put("number",number).put("airdate",airstamp.substring(0,10)).put("airstamp",airstamp);
        if(embedded)e.put("_embedded",new JSONObject().put("show",show));else e.put("show",show);
        return e;
    }
    @Test public void tvmazeKeepsOneCardPerShowWithLatestAiredEpisode()throws Exception{
        long now=OffsetDateTime.parse("2026-11-02T12:00:00Z").toInstant().toEpochMilli();
        JSONObject a=show(1,"Bigg Boss","Hindi",90,true,"Reality"),b=show(2,"Late Show","English",50,false),adult=show(3,"X","English",99,true,"Adult");
        List<JSONObject> eps=Arrays.asList(
            ep(a,false,19,40,"2026-11-01T16:30:00+00:00"),ep(a,false,19,41,"2026-11-02T10:00:00+00:00"),
            ep(a,false,19,42,"2026-11-03T16:30:00+00:00"),
            ep(b,true,1,3,"2026-11-02T02:00:00+00:00"),ep(adult,true,1,1,"2026-11-02T01:00:00+00:00"));
        List<CatalogItem> out=TvMazeSource.shows(eps,now);
        assertEquals(2,out.size());
        CatalogItem bb=out.get(0);
        assertEquals("tvmaze:show:1",bb.key());assertEquals(CatalogItem.SERIES,bb.kind);
        assertTrue(bb.episodeLabel,bb.episodeLabel.startsWith("S19 · E41 · 2 Nov"));
        assertEquals("hi",bb.languageCode);assertEquals("2020-01-05",bb.releaseDate);
        assertEquals("About Bigg Boss & more.",bb.title.description);
        assertEquals("On Netflix",bb.title.cast);
        assertEquals("https://static.tvmaze.com/m/1.jpg",bb.title.poster);
        assertEquals(7.5,bb.rating,0.001);assertEquals("TVMaze",bb.ratingSource);
        assertFalse(bb.playable);
        assertEquals("",out.get(1).title.poster);
    }
    @Test public void tvmazeLanguageNamesMapToCodes(){
        assertEquals("hi",TvMazeSource.languageCode("Hindi"));
        assertEquals("ta",TvMazeSource.languageCode("tamil"));
        assertEquals("",TvMazeSource.languageCode("Klingon"));
        assertEquals("",TvMazeSource.languageCode(""));
    }

    @Test public void fallbackOnlyStandsInWhereTheRowTitleStaysTrue(){
        assertEquals(WikidataFilmSource.row("te"),CatalogFallback.row(TmdbCatalogSource.latestRow("te")));
        assertNull(CatalogFallback.row(TmdbCatalogSource.latestRow("en")));
        assertNull(CatalogFallback.row(TmdbCatalogSource.ROW_TRENDING_IN));
        assertEquals(TvMazeSource.ROW_INDIA,CatalogFallback.row(TmdbCatalogSource.serialRow("ta")));
        assertEquals(TvMazeSource.ROW_WORLD,CatalogFallback.row(TmdbCatalogSource.ROW_RECENT_EPISODES));
        assertNull(CatalogFallback.row(CatalogRepository.HOME_LIVE));
        assertTrue(CatalogFallback.dependents(TvMazeSource.ROW_INDIA).containsAll(Arrays.asList(TmdbCatalogSource.serialRow("hi"),TmdbCatalogSource.ROW_TV_NEW)));
        assertEquals(Collections.singletonList(TmdbCatalogSource.latestRow("kn")),CatalogFallback.dependents(WikidataFilmSource.row("kn")));
    }
    @Test public void fallbackNarrowsSerialRowsToTheirLanguage(){
        CatalogItem hi=CatalogTestData.movie(TvMazeSource.PROVIDER,"show:1","A","2020-01-01");hi.languageCode="hi";
        CatalogItem en=CatalogTestData.movie(TvMazeSource.PROVIDER,"show:2","B","2020-01-01");en.languageCode="en";
        assertEquals(Collections.singletonList(hi),CatalogFallback.narrow(TmdbCatalogSource.serialRow("hi"),Arrays.asList(hi,en)));
        assertEquals(2,CatalogFallback.narrow(TmdbCatalogSource.ROW_TV_NEW,Arrays.asList(hi,en)).size());
    }
    @Test public void rowsResolveToTheirOwnSource(){
        assertEquals(WikidataFilmSource.ID,CatalogRepository.sourceFor(WikidataFilmSource.ROW_LATEST));
        assertEquals(TvMazeSource.ID,CatalogRepository.sourceFor(TvMazeSource.ROW_WORLD));
        assertEquals(TmdbCatalogSource.MOVIES,CatalogRepository.sourceFor(TmdbCatalogSource.latestRow("te")));
        assertEquals(WikidataFilmSource.ID,CatalogFallback.source(CatalogRepository.HOME_RECENT));
        assertEquals(TvMazeSource.ID,CatalogFallback.source(TmdbCatalogSource.ROW_TV_NEW));
    }
    @Test public void localGenresComeFromSavedTitlesWithNegativeIds(){
        CatalogItem a=CatalogTestData.movie("wikidata","1","A","2026-01-01");a.title.genre="Action · Drama";
        CatalogItem b=CatalogTestData.movie("wikidata","2","B","2026-01-01");b.title.genre="Drama · Action";
        CatalogItem c=CatalogTestData.movie("wikidata","3","C","2026-01-01");c.title.genre="Musical";
        Map<Integer,String> g=CatalogRepository.localGenres(Arrays.asList(a,b,c));
        assertEquals(Arrays.asList("Action","Drama"),new ArrayList<>(g.values()));
        for(int id:g.keySet())assertTrue(id<0);
        DiscoverQuery q=new DiscoverQuery();q.genreId=-1;q.genreName="Action";
        assertFalse(q.path(1,"2026-11-02").contains("with_genres"));
    }
}
