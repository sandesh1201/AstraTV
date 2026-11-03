package com.astratv.app;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Movies tab: titles from MovieRulz only.
 *
 * MovieRulz (5movierulz.* mirrors) publishes no catalog API, feed, licence or partner programme;
 * its pages list unlicensed copies of films. The playlist the old add-on read
 * (thirud4a4.github.io/movies_m3u) routes every entry through a third-party Cloudflare Worker that
 * re-streams those copies. Neither is an authorized source, so nothing is fetched, the tab stays
 * empty with this explanation, and no other catalog is substituted. A provider of origin
 * {@code movierulz} that grants playback through an official API would be picked up unchanged.
 */
final class MovieRulzCatalogProvider extends SourceMovieCatalog {
    static final String ID="movierulz.catalog",ORIGIN="movierulz";
    static final String ROW="movierulz.playable",ROW_ALL="movierulz.all";
    static final String REASON="Not available. MovieRulz has no authorized catalog or playback API: its sites and the playlists built from them "
        +"list unlicensed copies of films. Saandio does not read those pages or play their links, and it does not fill this tab with titles from other catalogs.";
    MovieRulzCatalogProvider(Supplier<Long> clock){this(Collections::emptyList,clock);}
    MovieRulzCatalogProvider(Supplier<List<LicensedProvider>> providers,Supplier<Long> clock){super(providers,clock);}

    @Override public String id(){return ID;}
    @Override public String label(){return "MovieRulz · Movies";}
    @Override public boolean checkOnForeground(){return ready();}
    @Override String origin(){return ORIGIN;}
    @Override String playableRow(){return ROW;}
    @Override String allRow(){return ROW_ALL;}
    @Override String unauthorizedReason(){return REASON;}
}
