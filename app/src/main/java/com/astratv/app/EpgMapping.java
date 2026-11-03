package com.astratv.app;

import java.util.*;

/**
 * Maps Live TV channels to XMLTV channel ids. An exact iptv-org tvg-id match always wins. A
 * display-name match is accepted only when the name is unique on both sides, so a guide never
 * lands on the wrong channel; anything ambiguous stays unmapped. A mapping says nothing about
 * whether the stream works.
 */
final class EpgMapping {
    private EpgMapping(){}

    /** Catalog key to XMLTV channel id. */
    static Map<String,String> map(List<CatalogItem> channels,Map<String,List<String>> xmltvChannels){
        Map<String,String> byId=new HashMap<>();Map<String,Set<String>> byName=new HashMap<>();
        for(Map.Entry<String,List<String>> e:xmltvChannels.entrySet()){
            byId.put(e.getKey().toLowerCase(Locale.ROOT),e.getKey());
            for(String n:e.getValue())byName.computeIfAbsent(norm(n),k->new HashSet<>()).add(e.getKey());
        }
        Map<String,Integer> ourNames=new HashMap<>();for(CatalogItem c:channels)ourNames.merge(norm(c.title.name),1,Integer::sum);
        Map<String,String> out=new HashMap<>();
        for(CatalogItem c:channels){
            String tvg=ChannelDeduplicationService.tvgId(c);
            String exact=tvg.isEmpty()?null:byId.get(tvg.toLowerCase(Locale.ROOT));
            if(exact!=null){out.put(c.key(),exact);continue;}
            String n=norm(c.title.name);Set<String> ids=byName.get(n);
            if(!n.isEmpty()&&ids!=null&&ids.size()==1&&ourNames.getOrDefault(n,0)==1)out.put(c.key(),ids.iterator().next());
        }
        return out;
    }
    static String norm(String name){return (name==null?"":name).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+","");}
}
