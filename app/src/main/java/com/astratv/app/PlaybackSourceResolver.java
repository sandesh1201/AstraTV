package com.astratv.app;

import java.util.*;

/**
 * Decides whether a catalog card can play inside Saandio. Metadata-only titles (TMDB, schedules)
 * play only when an authorized provider lists the same work with a verified stream; otherwise the
 * viewer is shown where the title is legally available instead of a fake play button.
 */
final class PlaybackSourceResolver {
    enum Kind{DIRECT,LICENSED_MATCH,CHANNEL,NOT_AVAILABLE}
    static final class Resolution{
        final Kind kind;final Library.Title title;final String reason;
        Resolution(Kind kind,Library.Title title,String reason){this.kind=kind;this.title=title;this.reason=reason;}
        boolean playable(){return kind!=Kind.NOT_AVAILABLE;}
    }
    private static final Set<String> DIRECT=new HashSet<>(Arrays.asList("library","samples",EinthusanSimulatedProvider.ID));
    /** Catalog metadata sources: they describe titles but never supply a stream. */
    static final Set<String> METADATA=Collections.unmodifiableSet(new HashSet<>(Arrays.asList("tmdb",WikidataFilmSource.PROVIDER,TvMazeSource.PROVIDER,"thesportsdb")));
    static boolean metadataOnly(Library.Title t){return t!=null&&METADATA.contains(t.provider);}
    private final CatalogStorage store;
    PlaybackSourceResolver(CatalogStorage store){this.store=store;}

    /** Blocking (database read); call off the main thread. */
    Resolution resolve(CatalogItem c){
        Library.Title t=c.title;
        if(CatalogItem.EVENT.equals(c.kind))return new Resolution(Kind.NOT_AVAILABLE,null,"Saandio has no broadcast rights for this event. Check the official broadcaster.");
        if(CatalogItem.CHANNEL.equals(c.kind)){
            if("failed".equals(c.health))return new Resolution(Kind.CHANNEL,t,"Last playback check failed. The channel may be offline or region-locked.");
            return new Resolution(Kind.CHANNEL,t,"ok".equals(c.health)?"Stream answered the last playback check.":"Stream has not been checked yet.");
        }
        if(DIRECT.contains(t.provider))return new Resolution(Kind.DIRECT,t,"");
        if(!METADATA.contains(t.provider))return new Resolution(Kind.DIRECT,t,"");
        List<CatalogItem> matches=store.playable(MetadataMatcher.normalize(CatalogDeduplication.baseName(t.name)),c.year(),c.kind);
        CatalogItem best=null;
        for(CatalogItem m:matches)if(best==null||CatalogDeduplication.better(m,best))best=m;
        if(best!=null)return new Resolution(Kind.LICENSED_MATCH,best.title,"Available from "+best.title.providerName+(best.verifiedHeight>0?" · "+PlaybackPolicy.heightLabel(best.verifiedHeight):""));
        return new Resolution(Kind.NOT_AVAILABLE,null,"tmdb".equals(t.provider)?"Not available to stream in Saandio. See where to watch below.":"Not available to stream in Saandio. No authorized provider lists this title.");
    }
}
