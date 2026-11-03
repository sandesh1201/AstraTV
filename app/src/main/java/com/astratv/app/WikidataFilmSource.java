package com.astratv.app;

import org.json.*;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.util.*;

/**
 * Recent Indian-language films from the Wikidata Query Service. Wikidata is CC0, needs no key and is
 * the keyless fallback when no TMDB catalog service is configured. Metadata only, never playable.
 * Dates are Wikidata's publication date (P577), shown as the release date and never as "added".
 */
final class WikidataFilmSource implements CatalogSource {
    static final String ID="wikidata.films",PROVIDER="wikidata";
    static final String ROW_LATEST="movies.open.latest";
    static final String ENDPOINT="https://query.wikidata.org/sparql?format=json&query=";
    /** Spoken-language item ids on Wikidata, keyed by the ISO codes the app uses. */
    static final String[][] LANGUAGES={{"te","Q8097"},{"ta","Q5885"},{"hi","Q1568"},{"ml","Q36236"},{"kn","Q33673"}};
    static String row(String code){return "movies.open."+code;}
    private final TmdbCatalogSource.Today today;
    WikidataFilmSource(TmdbCatalogSource.Today today){this.today=today;}

    @Override public String id(){return ID;}
    @Override public String label(){return "Movies · Wikidata (CC0)";}
    @Override public long defaultTtlMs(){return 6L*60*60_000L;}
    @Override public boolean checkOnForeground(){return false;}
    @Override public boolean ready(){return true;}
    @Override public String notReadyReason(){return "";}

    @Override public List<Batch> fetch(CatalogHttp http)throws Exception{
        CatalogHttp.Result r=http.get(ENDPOINT+URLEncoder.encode(query(today.today()),"UTF-8"));
        List<CatalogItem> all=parse(new JSONObject(r.body));
        List<Batch> out=new ArrayList<>();
        for(String[] l:LANGUAGES){List<CatalogItem> rows=new ArrayList<>();for(CatalogItem c:all)if(c.languageCode.equals(l[0]))rows.add(c);out.add(new Batch(row(l[0]),rows,r.notModified));}
        List<CatalogItem> latest=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(CatalogItem c:all)if(seen.add(c.key())&&latest.size()<80)latest.add(c);
        out.add(new Batch(ROW_LATEST,latest,r.notModified));
        return out;
    }

    static String query(LocalDate today){
        StringBuilder langs=new StringBuilder();for(String[] l:LANGUAGES)langs.append(" wd:").append(l[1]);
        return "SELECT ?film ?lang (SAMPLE(?label) AS ?name) (MIN(?date) AS ?released) (SAMPLE(?desc) AS ?about) (SAMPLE(?poster) AS ?image) (SAMPLE(?tmdb) AS ?tmdbId) (SAMPLE(?directorLabel) AS ?director) (GROUP_CONCAT(DISTINCT ?genreLabel; separator=\"|\") AS ?genres) WHERE {"
            +" VALUES ?lang {"+langs+" } ?film wdt:P31 wd:Q11424; wdt:P364 ?lang; wdt:P577 ?date."
            +" FILTER(?date >= \""+today.minusDays(540)+"T00:00:00Z\"^^xsd:dateTime && ?date <= \""+today+"T23:59:59Z\"^^xsd:dateTime)"
            +" ?film rdfs:label ?label. FILTER(LANG(?label) = \"en\")"
            +" OPTIONAL { ?film schema:description ?desc. FILTER(LANG(?desc) = \"en\") }"
            +" OPTIONAL { ?film wdt:P3383 ?poster }"
            +" OPTIONAL { ?film wdt:P4947 ?tmdb }"
            +" OPTIONAL { ?film wdt:P57 ?d. ?d rdfs:label ?directorLabel. FILTER(LANG(?directorLabel) = \"en\") }"
            +" OPTIONAL { ?film wdt:P136 ?g. ?g rdfs:label ?genreLabel. FILTER(LANG(?genreLabel) = \"en\") }"
            +"} GROUP BY ?film ?lang ORDER BY DESC(?released) LIMIT 600";
    }

    /** Pure mapping of a SPARQL JSON result. Rows without an English title or a usable date are dropped. */
    static List<CatalogItem> parse(JSONObject body){
        List<CatalogItem> out=new ArrayList<>();
        JSONObject results=body.optJSONObject("results");JSONArray rows=results==null?null:results.optJSONArray("bindings");if(rows==null)return out;
        for(int i=0;i<rows.length();i++){
            JSONObject b=rows.optJSONObject(i);if(b==null)continue;
            String qid=tail(value(b,"film")),code=code(tail(value(b,"lang"))),name=value(b,"name"),released=value(b,"released");
            if(!qid.startsWith("Q")||code.isEmpty()||name.isEmpty()||name.equals(qid)||released.length()<10)continue;
            String date=released.substring(0,10);
            List<String> genres=new ArrayList<>();
            for(String g:value(b,"genres").split("\\|")){String n=g.replaceAll("(?i)\\s+film$","").trim();if(!n.isEmpty()&&genres.size()<3)genres.add(Character.toUpperCase(n.charAt(0))+n.substring(1));}
            String director=value(b,"director");
            Library.Title t=new Library.Title("film:"+qid,name,value(b,"about"),"",director.isEmpty()?"":"Director · "+director,date.substring(0,4),String.join(" · ",genres));
            t.provider=PROVIDER;t.providerName="Wikidata";t.type="movie";t.released=date;t.language=TmdbCatalogSource.languageName(code);
            String poster=commons(value(b,"image"));t.poster=poster;t.background=poster;
            CatalogItem c=new CatalogItem(t);c.kind=CatalogItem.MOVIE;c.languageCode=code;c.releaseDate=date;c.playable=false;
            try{c.tmdbId=Integer.parseInt(value(b,"tmdbId"));}catch(NumberFormatException ignored){/* not linked */}
            if(!t.language.isEmpty())c.audio.add(t.language);
            out.add(c);
        }
        return out;
    }
    /** Commons file URL from P3383; rescaled by Wikimedia so cards do not download full-size scans. */
    static String commons(String url){
        if(url.isEmpty())return "";
        int at=url.indexOf("Special:FilePath/");if(at<0)return "";
        String file=url.substring(at+"Special:FilePath/".length());
        return "https://commons.wikimedia.org/wiki/Special:FilePath/"+file+"?width=342";
    }
    private static String code(String qid){for(String[] l:LANGUAGES)if(l[1].equals(qid))return l[0];return "";}
    private static String tail(String uri){int i=uri.lastIndexOf('/');return i<0?uri:uri.substring(i+1);}
    private static String value(JSONObject b,String key){JSONObject o=b.optJSONObject(key);return o==null?"":o.optString("value","").trim();}
}
