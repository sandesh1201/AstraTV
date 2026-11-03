package com.astratv.app;

import java.util.*;

/** Test double for {@link CatalogStorage}: same contract as the SQLite store, kept in maps. */
final class InMemoryCatalogStorage implements CatalogStorage {
    final Map<String,List<CatalogItem>> rows=new HashMap<>();
    final Map<String,CatalogFreshnessTracker.State> states=new HashMap<>();
    final Map<String,long[]> health=new HashMap<>();
    final Map<String,CatalogHttp.Entry> http=new HashMap<>();
    final Map<String,Long> firstSeen=new HashMap<>();
    int replaceCalls;

    @Override public synchronized Set<String> replaceRows(String source,List<CatalogSource.Batch> batches,long now){
        replaceCalls++;boolean baseline=states.get(source)==null||states.get(source).syncedAt==0;
        Set<String> changed=new HashSet<>();
        for(CatalogSource.Batch b:batches){
            if(b.notModified&&rows.containsKey(b.rowId))continue;
            List<CatalogItem> old=rows.get(b.rowId);
            for(CatalogItem c:b.items){firstSeen.putIfAbsent(c.key(),baseline?0L:now);c.firstSeen=firstSeen.get(c.key());}
            if(old==null||!signatures(old).equals(signatures(b.items)))changed.add(b.rowId);
            rows.put(b.rowId,new ArrayList<>(b.items));
        }
        return changed;
    }
    private static List<String> signatures(List<CatalogItem> items){List<String> out=new ArrayList<>();for(CatalogItem c:items)out.add(c.key()+"|"+c.signature());return out;}
    @Override public synchronized List<CatalogItem> row(String rowId,int limit){List<CatalogItem> r=rows.getOrDefault(rowId,Collections.emptyList());return new ArrayList<>(r.subList(0,Math.min(limit,r.size())));}
    @Override public synchronized List<CatalogItem> recent(String kind,int limit){
        Map<String,CatalogItem> out=new LinkedHashMap<>();for(List<CatalogItem> r:rows.values())for(CatalogItem c:r)if(kind.equals(c.kind))out.putIfAbsent(c.key(),c);
        return DiscoverQuery.order(new ArrayList<>(out.values()),"added");
    }
    @Override public synchronized List<CatalogItem> search(String text,int limit){
        List<CatalogItem> out=new ArrayList<>();for(List<CatalogItem> r:rows.values())for(CatalogItem c:r)if(c.title.name.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT)))out.add(c);return out;
    }
    @Override public synchronized List<CatalogItem> playable(String normalizedName,int year,String kind){
        List<CatalogItem> out=new ArrayList<>();
        for(List<CatalogItem> r:rows.values())for(CatalogItem c:r)
            if(c.playable&&kind.equals(c.kind)&&(year<=0||c.year()==year)&&MetadataMatcher.normalize(CatalogDeduplication.baseName(c.title.name)).equals(normalizedName))out.add(c);
        return out;
    }
    @Override public synchronized CatalogFreshnessTracker.State state(String source){CatalogFreshnessTracker.State s=states.get(source);return s==null?null:s.copy();}
    @Override public synchronized void saveState(String source,CatalogFreshnessTracker.State state){states.put(source,state.copy());}
    @Override public synchronized void saveHealth(String key,boolean ok,long now){long[] h=health.computeIfAbsent(key,k->new long[2]);if(ok)h[0]=now;else h[1]=now;}
    @Override public synchronized Map<String,long[]> health(){return new HashMap<>(health);}
    @Override public synchronized CatalogHttp.Entry get(String key){return http.get(key);}
    @Override public synchronized void put(String key,CatalogHttp.Entry entry){http.put(key,entry);}
}
