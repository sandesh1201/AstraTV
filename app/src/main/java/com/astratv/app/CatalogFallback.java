package com.astratv.app;

import java.util.*;

/**
 * Open-data rows (Wikidata, TVMaze) that stand in for a TMDB row with the same meaning when the TMDB
 * row has nothing saved yet, e.g. a first launch while the catalog service is down. Rows whose title
 * would become untrue (trending, Hollywood) have no stand-in.
 */
final class CatalogFallback {
    private CatalogFallback(){}
    private static final List<String> TMDB_ROWS;
    static{
        List<String> rows=new ArrayList<>();
        for(String[] l:TmdbCatalogSource.MOVIE_LANGUAGES)rows.add(TmdbCatalogSource.latestRow(l[0]));
        for(String[] l:TmdbCatalogSource.SERIAL_LANGUAGES)rows.add(TmdbCatalogSource.serialRow(l[0]));
        rows.add(TmdbCatalogSource.ROW_RECENT_EPISODES);rows.add(TmdbCatalogSource.ROW_TV_NEW);
        TMDB_ROWS=Collections.unmodifiableList(rows);
    }

    /** Stand-in row for a TMDB row, or null. */
    static String row(String rowId){
        String latest=TmdbCatalogSource.latestRow("");
        if(rowId.startsWith(latest)){String code=rowId.substring(latest.length());for(String[] l:WikidataFilmSource.LANGUAGES)if(l[0].equals(code))return WikidataFilmSource.row(code);return null;}
        if(rowId.startsWith(TmdbCatalogSource.serialRow(""))||rowId.equals(TmdbCatalogSource.ROW_TV_NEW))return TvMazeSource.ROW_INDIA;
        if(rowId.equals(TmdbCatalogSource.ROW_RECENT_EPISODES))return TvMazeSource.ROW_WORLD;
        return null;
    }
    /** Language a stand-in must be narrowed to (serial rows are per language); empty for none. */
    static String language(String rowId){
        String serial=TmdbCatalogSource.serialRow("");
        return rowId.startsWith(serial)?rowId.substring(serial.length()):"";
    }
    static List<CatalogItem> narrow(String rowId,List<CatalogItem> items){
        String code=language(rowId);if(code.isEmpty())return items;
        List<CatalogItem> out=new ArrayList<>();for(CatalogItem c:items)if(code.equals(c.languageCode))out.add(c);return out;
    }
    /** TMDB rows to repaint when an open-data row changes. */
    static List<String> dependents(String openRow){
        List<String> out=new ArrayList<>();for(String r:TMDB_ROWS)if(openRow.equals(row(r)))out.add(r);return out;
    }
    /** Open-data source answering for a row or grid when TMDB is unavailable. */
    static String source(String rowId){
        if(rowId.startsWith("movies.")||rowId.equals(CatalogRepository.HOME_RECENT))return WikidataFilmSource.ID;
        if(rowId.startsWith("tv."))return TvMazeSource.ID;
        return null;
    }
}
