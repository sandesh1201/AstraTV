package com.astratv.app;

import java.util.*;
import java.util.function.Supplier;

/**
 * Titles from authorized playback partners ({@link LicensedProvider}). Quality comes from the
 * partner's playback API (rendition height), never from words like "HDRip" in a title or page.
 */
final class HdCatalogSource implements CatalogSource {
    static final String ID="licensed.partners",ROW_HD="hd.latest",ROW_PLAYABLE="licensed.playable";
    private static final int MAX_TITLES=60;
    private final Supplier<List<LicensedProvider>> providers;
    HdCatalogSource(Supplier<List<LicensedProvider>> providers){this.providers=providers;}

    @Override public String id(){return ID;}
    @Override public String label(){return "Licensed partners · verified quality";}
    @Override public long defaultTtlMs(){return 60L*60_000L;}
    @Override public boolean checkOnForeground(){return true;}
    @Override public boolean ready(){return !providers.get().isEmpty();}
    @Override public String notReadyReason(){return "No authorized playback partner is enabled. Real partners need a signed agreement and API credentials; the Einthusan simulation (Creative Commons samples) can be turned on in Settings → Add-ons.";}

    @Override public List<Batch> fetch(CatalogHttp http)throws Exception{
        List<CatalogItem> all=new ArrayList<>();Exception first=null;
        for(LicensedProvider p:providers.get()){
            if(!p.authorization().granted)continue;
            try{all.addAll(items(p));}catch(Exception e){if(first==null)first=e;}
        }
        if(all.isEmpty()&&first!=null)throw first;
        List<CatalogItem> playable=DiscoverQuery.order(all,"newest"),hd=new ArrayList<>();
        for(CatalogItem c:playable)if(c.hd())hd.add(c);
        return Arrays.asList(new Batch(ROW_HD,hd,false),new Batch(ROW_PLAYABLE,playable,false));
    }
    static List<CatalogItem> items(LicensedProvider p)throws LicensedProvider.ProviderException{
        List<CatalogItem> out=new ArrayList<>();int page=1;LicensedProvider.Page result;
        do{
            result=p.catalog(new LicensedProvider.Query("","",page,50));
            for(Library.Title t:result.titles){
                if(out.size()>=MAX_TITLES)break;
                CatalogItem c=verified(p,t);if(c!=null)out.add(c);
            }
            page++;
        }while(result.hasMore()&&out.size()<MAX_TITLES&&page<=5);
        return out;
    }
    /** A title is listed only when the partner returns at least one stream Saandio can actually play. */
    static CatalogItem verified(LicensedProvider p,Library.Title t){
        List<LicensedProvider.Playback> streams;
        try{streams=p.playback(t.id);}catch(LicensedProvider.ProviderException e){return null;}
        if(streams.isEmpty())return null;
        CatalogItem c=CatalogItem.of(t);c.kind=CatalogItem.MOVIE;c.playable=true;c.sources.add(p.displayName());
        for(LicensedProvider.Playback s:streams){
            c.verifiedHeight=Math.max(c.verifiedHeight,s.maxHeight);
            for(String a:s.audioLanguages){String lang=LanguageTaxonomy.canonical(a);if(!lang.equals(LanguageTaxonomy.OTHER)&&!c.audio.contains(lang))c.audio.add(lang);}
        }
        return c;
    }
}
