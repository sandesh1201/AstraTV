package com.astratv.app;

import java.util.*;
import java.util.concurrent.*;

/** Intersects country and language directories; foreign channels in language feeds are excluded. */
final class IndianTvProvider {
    static final String[][] LANGUAGES={{"tel","Telugu"},{"hin","Hindi"},{"tam","Tamil"},{"kan","Kannada"},{"mal","Malayalam"},{"ben","Bengali"},{"mar","Marathi"},{"pan","Punjabi"},{"guj","Gujarati"},{"urd","Urdu"},{"ori","Odia"},{"asm","Assamese"},{"eng","English"},{"bho","Bhojpuri"},{"san","Sanskrit"}};
    static final String DIRECTORY="https://iptv-org.github.io/iptv/";
    interface Fetcher{String text(String url)throws Exception;}
    static final class Directory{
        final List<Library.Title> channels;final int feeds;
        Directory(List<Library.Title> channels,int feeds){this.channels=channels;this.feeds=feeds;}
        String coverage(){return feeds+"/"+LANGUAGES.length+" language feeds loaded; unclassified channels retained";}
    }
    private List<Library.Title> cached=new ArrayList<>();private long loaded=0;private String coverage="";
    synchronized List<Library.Title> all()throws Exception{
        if(!cached.isEmpty()&&System.currentTimeMillis()-loaded<15*60*1000)return new ArrayList<>(cached);
        Directory d=load(ProviderRepository::requestText);
        cached=new ArrayList<>(d.channels);loaded=System.currentTimeMillis();coverage=d.coverage();return new ArrayList<>(cached);
    }
    static Directory load(Fetcher fetch)throws Exception{
        List<PlaylistParser.Entry> india=PlaylistParser.entries(fetch.text(DIRECTORY+"countries/in.m3u"));
        Set<String> ids=new HashSet<>();for(PlaylistParser.Entry e:india)if(!e.id.isEmpty())ids.add(e.id.split("@")[0]);
        ExecutorService workers=Executors.newFixedThreadPool(3);List<Future<List<Library.Title>>> requests=new ArrayList<>();
        for(String[] lang:LANGUAGES)requests.add(workers.submit(()->PlaylistParser.channels(PlaylistParser.entries(fetch.text(DIRECTORY+"languages/"+lang[0]+".m3u")),lang[1],ids)));
        Map<String,Library.Title> result=new LinkedHashMap<>();int successful=0;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(50);
        try{for(Future<List<Library.Title>> task:requests){try{long remaining=deadline-System.nanoTime();if(remaining<=0)break;for(Library.Title t:task.get(remaining,TimeUnit.NANOSECONDS))result.putIfAbsent(t.id+"|"+t.language,t);successful++;}catch(Exception ignored){/* Partial coverage remains explicit. */}}}finally{for(Future<?> task:requests)task.cancel(true);workers.shutdownNow();}
        if(result.isEmpty())throw new java.io.IOException("No supported Indian channels were returned by the public directories");
        Set<String> known=new HashSet<>();for(Library.Title t:result.values())known.add(t.id.split("~")[0]);List<PlaylistParser.Entry> remainder=new ArrayList<>();Set<String> remainderIds=new HashSet<>();
        for(PlaylistParser.Entry e:india){String raw=e.id.split("@")[0];if(!known.contains(raw)){remainder.add(e);remainderIds.add(raw);}}
        for(Library.Title t:PlaylistParser.channels(remainder,"Other Indian languages",remainderIds))result.putIfAbsent(t.id,t);
        return new Directory(new ArrayList<>(result.values()),successful);
    }
    ProviderRepository.Catalog catalog(String search,int page,String language,String genre)throws Exception{
        ProviderRepository.Catalog result=CatalogQuery.page(CatalogSort.newestFirst(CatalogQuery.filter(all(),search,language,genre)),page,"channels");
        return new ProviderRepository.Catalog(result.titles,result.status+" · "+coverage,false,result.total,page,result.hasMore);
    }
}
