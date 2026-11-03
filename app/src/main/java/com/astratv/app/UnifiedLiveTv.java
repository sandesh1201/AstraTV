package com.astratv.app;

import java.util.*;

/**
 * The Live TV listing: the existing iptv-org directory first and unchanged, followed by
 * de-duplicated entries from enabled {@link AdditionalChannelProvider}s. With the new-sources flag
 * off the result is exactly the existing list.
 */
final class UnifiedLiveTv {
    static final String SOURCE_ALL="",SOURCE_DIRECTORY="directory",SOURCE_OFFICIAL="official";
    private UnifiedLiveTv(){}

    static List<AdditionalChannelProvider> providers(boolean newSources,boolean sunNetwork){
        List<AdditionalChannelProvider> out=new ArrayList<>();
        if(newSources&&sunNetwork)out.add(new SunNetworkChannels());
        return out;
    }
    static ChannelDeduplicationService.Result merge(List<CatalogItem> existing,List<AdditionalChannelProvider> providers){
        if(providers.isEmpty())return new ChannelDeduplicationService.Result(existing,Collections.emptyMap(),0);
        List<CatalogItem> additional=new ArrayList<>();
        for(AdditionalChannelProvider p:providers)try{additional.addAll(p.channels());}catch(RuntimeException ignored){/* a failing provider never affects the existing listing */}
        return ChannelDeduplicationService.merge(existing,additional);
    }
    static boolean official(CatalogItem c){return c.handoff();}
    static boolean matchesSource(CatalogItem c,String source){
        if(SOURCE_DIRECTORY.equals(source))return !official(c);
        if(SOURCE_OFFICIAL.equals(source))return official(c);
        return true;
    }
}
