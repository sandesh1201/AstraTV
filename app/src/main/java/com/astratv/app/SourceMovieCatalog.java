package com.astratv.app;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Supplier;

/**
 * A movie tab fed by exactly one content provider. Titles come only from that provider's own
 * catalog API, and each carries the availability its playback API reported. Metadata services
 * (TMDB, Wikidata, IMDb, OMDb) never add titles here, and a failing provider is never replaced
 * by another catalog: the tab keeps its last confirmed titles, or stays empty with the reason.
 */
abstract class SourceMovieCatalog implements CatalogSource {
    static final int MAX_TITLES=200,MAX_PAGES=8,PAGE_SIZE=50;
    private final Supplier<List<LicensedProvider>> providers;
    private final Supplier<Long> clock;
    SourceMovieCatalog(Supplier<List<LicensedProvider>> providers,Supplier<Long> clock){this.providers=providers;this.clock=clock;}

    /** Provider family this tab accepts, e.g. {@code movierulz}. Providers of any other origin are ignored. */
    abstract String origin();
    /** Row of titles whose playback the provider confirmed. This is what the tab shows by default. */
    abstract String playableRow();
    /** Every title the provider listed, each with its availability, for the "show unavailable" view. */
    abstract String allRow();
    /** Why the tab is empty when no authorized provider of this origin exists. */
    abstract String unauthorizedReason();

    @Override public long defaultTtlMs(){return 60L*60_000L;}
    @Override public boolean checkOnForeground(){return true;}
    @Override public boolean ready(){return !authorized().isEmpty();}
    @Override public String notReadyReason(){return unauthorizedReason();}
    /** UNSUPPORTED until a provider of this origin grants playback through an official API. */
    MovieAvailability status(){return ready()?MovieAvailability.AVAILABLE:MovieAvailability.UNSUPPORTED;}
    boolean simulated(){for(LicensedProvider p:authorized())if(p.authorization().simulated)return true;return false;}

    List<LicensedProvider> authorized(){
        List<LicensedProvider> out=new ArrayList<>();
        for(LicensedProvider p:providers.get())if(origin().equals(p.origin())&&p.authorization().granted)out.add(p);
        return out;
    }

    @Override public List<Batch> fetch(CatalogHttp http)throws Exception{
        List<CatalogItem> all=new ArrayList<>();Exception first=null;long now=clock.get();
        for(LicensedProvider p:authorized()){
            try{all.addAll(items(p,now));}catch(LicensedProvider.ProviderException e){if(first==null)first=e;}
        }
        // A provider that refused the whole catalog keeps the previous confirmed rows (the store only replaces on success).
        if(all.isEmpty()&&first!=null)throw first;
        all=DiscoverQuery.order(all,"added");
        List<CatalogItem> playable=new ArrayList<>();
        for(CatalogItem c:all)if(c.availability()==MovieAvailability.AVAILABLE)playable.add(c);
        return Arrays.asList(new Batch(playableRow(),playable,false),new Batch(allRow(),all,false));
    }

    /** Pages through the provider's catalog and asks its playback API about each listed title. */
    static List<CatalogItem> items(LicensedProvider p,long now)throws LicensedProvider.ProviderException{
        List<CatalogItem> out=new ArrayList<>();Set<String> seen=new HashSet<>();int page=1;LicensedProvider.Page result;
        do{
            result=p.catalog(new LicensedProvider.Query("","",page,PAGE_SIZE));
            for(Library.Title t:result.titles){
                if(out.size()>=MAX_TITLES)break;
                if(!p.id().equals(t.provider)||!seen.add(t.id))continue;
                out.add(check(p,t,now));
            }
            page++;
        }while(result.hasMore()&&out.size()<MAX_TITLES&&page<=MAX_PAGES);
        return out;
    }

    /**
     * AVAILABLE only when the provider returns at least one stream that passes the same checks the
     * player applies (HTTPS, a real media type, not expired, no unsupported DRM).
     */
    static CatalogItem check(LicensedProvider p,Library.Title t,long now){
        CatalogItem c=verdict(p,t,now);
        Diagnostics.Problem problem=Diagnostics.ofAvailability(c.availability());
        if(problem!=null)Diagnostics.get().provider(problem,p.id(),t.name+" · "+c.availabilityReason);
        return c;
    }
    private static CatalogItem verdict(LicensedProvider p,Library.Title t,long now){
        CatalogItem c=CatalogItem.of(t);c.kind=CatalogItem.MOVIE;c.playable=false;c.sources.add(p.displayName());
        c.addedAt=epochMs(t.added);
        List<LicensedProvider.Playback> streams;
        try{streams=p.playback(t.id);}
        catch(LicensedProvider.ProviderException e){c.availability=MovieAvailability.of(e.code).name();c.availabilityReason=e.getMessage();return c;}
        String problem="No stream was offered.";
        for(LicensedProvider.Playback s:streams){
            String why=PlaybackCandidate.of(p.id(),ProviderRepository.stream(s)).problem(now,"",false);
            if(why!=null){problem=why;continue;}
            c.playable=true;c.verifiedHeight=Math.max(c.verifiedHeight,s.maxHeight);
            for(String a:s.audioLanguages){String lang=LanguageTaxonomy.canonical(a);if(!lang.equals(LanguageTaxonomy.OTHER)&&!c.audio.contains(lang))c.audio.add(lang);}
        }
        c.availability=(c.playable?MovieAvailability.AVAILABLE:MovieAvailability.UNAVAILABLE).name();
        c.availabilityReason=c.playable?"":problem;
        return c;
    }
    static long epochMs(String isoDate){
        if(isoDate==null||isoDate.length()<10)return 0;
        try{return LocalDate.parse(isoDate.substring(0,10)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();}catch(Exception e){return 0;}
    }
    /** True for titles of the Movies tab provider, where Watch Movie needs a confirmed AVAILABLE. */
    static boolean sourceTitle(Library.Title t){
        if(t==null)return false;String p=t.provider==null?"":t.provider;
        return p.equals(MovieRulzCatalogProvider.ORIGIN)||p.startsWith(MovieRulzCatalogProvider.ORIGIN+".");
    }
}
