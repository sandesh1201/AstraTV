package com.astratv.app;

import java.util.*;

/**
 * Merges additional channel entries into the existing Live TV listing. The existing list always
 * wins: its items come back as the same objects in the same order, and an additional entry that
 * matches one of them is not shown. Instead the match is reported as an extra official route for
 * the existing channel ("Also on Sun NXT").
 */
final class ChannelDeduplicationService {
    static final class Result{
        final List<CatalogItem> items;
        /** Existing channel key to the official entries that duplicate it. */
        final Map<String,List<CatalogItem>> officialRoutes;
        final int hiddenDuplicates;
        Result(List<CatalogItem> items,Map<String,List<CatalogItem>> officialRoutes,int hiddenDuplicates){this.items=items;this.officialRoutes=officialRoutes;this.hiddenDuplicates=hiddenDuplicates;}
    }
    private ChannelDeduplicationService(){}

    static Result merge(List<CatalogItem> existing,List<CatalogItem> additional){
        Map<String,CatalogItem> byIptv=new HashMap<>(),byName=new HashMap<>();Set<String> keys=new HashSet<>();
        for(CatalogItem c:existing){
            keys.add(c.key());
            String tvg=tvgId(c);if(!tvg.isEmpty())byIptv.putIfAbsent(tvg.toLowerCase(Locale.ROOT),c);
            byName.putIfAbsent(nameKey(c.title.name,c.title.language),c);
        }
        List<CatalogItem> out=new ArrayList<>(existing);Map<String,List<CatalogItem>> routes=new LinkedHashMap<>();int hidden=0;
        Set<String> added=new HashSet<>();
        for(CatalogItem a:additional){
            if(keys.contains(a.key())||!added.add(a.key())){hidden++;continue;}
            CatalogItem match=null;
            SunNetworkChannels.Entry e=SunNetworkChannels.PROVIDER.equals(a.title.provider)?SunNetworkChannels.entry(a.title.id):null;
            if(e!=null)for(String id:e.iptvIds){match=byIptv.get(id.toLowerCase(Locale.ROOT));if(match!=null)break;}
            if(match==null)match=byName.get(nameKey(a.title.name,a.title.language));
            if(match!=null){routes.computeIfAbsent(match.key(),k->new ArrayList<>()).add(a);hidden++;continue;}
            out.add(a);
        }
        return new Result(out,routes,hidden);
    }
    /** iptv-org tvg-id of a directory channel ("SunTV.in" from id "SunTV.in~Tamil"); empty for other providers. */
    static String tvgId(CatalogItem c){
        if(!"news".equals(c.title.provider))return "";
        String id=c.title.id==null?"":c.title.id;int cut=id.indexOf('~');return cut>0?id.substring(0,cut):"";
    }
    /** Same channel name ignoring case, punctuation and an HD suffix, within the same language. */
    static String nameKey(String name,String language){
        String n=(name==null?"":name).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+hd$","").replaceAll("\\s+","");
        return n+"|"+(language==null?"":language.toLowerCase(Locale.ROOT));
    }
}
