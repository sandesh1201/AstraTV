package com.astratv.app;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Developer check: runs the app's own SerialFeedParser and SerialCatchupProvider row building
 * against the live TamilTVSerial and TeluguStream RSS feeds (feed pages only, three pages each,
 * one second apart). Prints metadata only. See tools/catchup-live-check/run.ps1.
 */
public final class CatchupLiveCheck {
    public static void main(String[] args)throws Exception{
        check(TamilTvSerialProvider.FEED,new TamilTvSerialProvider(null),"TamilTVSerial","Tamil");
        check(TeluguStreamProvider.FEED,new TeluguStreamProvider(null),"TeluguStream","Telugu");
    }
    private static void check(String feed,SerialCatchupProvider p,String name,String language)throws Exception{
        List<CatchupEpisode> all=new ArrayList<>();
        for(int page=1;page<=3;page++){
            if(page>1)Thread.sleep(1000);
            String url=page==1?feed:feed+"?paged="+page;
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
            c.setRequestProperty("User-Agent","SaandioCatchupCheck/1.0 (+metadata only)");c.setConnectTimeout(15000);c.setReadTimeout(15000);
            int status=c.getResponseCode();
            if(status!=200){System.out.println(name+" "+url+" -> HTTP "+status);break;}
            String body;try(InputStream in=c.getInputStream()){body=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
            List<CatchupEpisode> parsed=SerialFeedParser.parse(body,p.provider(),name,language);
            System.out.println(name+" "+url+" -> HTTP 200, "+parsed.size()+" episodes parsed");
            all.addAll(parsed);
        }
        long now=System.currentTimeMillis();
        List<CatalogSource.Batch> rows=p.batches(all,now);
        List<CatalogItem> latest=rows.get(0).items,shows=rows.get(1).items;
        Map<String,Integer> apps=new TreeMap<>(),langs=new TreeMap<>();int noApp=0,noEpisode=0,urls=0;
        for(CatalogItem e:latest){
            String app=e.handoff()?OfficialAppHandoff.appName(e.playbackReference):"(no official app)";apps.merge(app,1,Integer::sum);langs.merge(e.title.language,1,Integer::sum);
            if(!e.handoff())noApp++;if(e.episode.episodeNumber<=0)noEpisode++;
            if(e.json().toString().contains("http"))urls++;
        }
        System.out.println("  rows: "+latest.size()+" unique episodes (from "+all.size()+" posts), "+shows.size()+" shows");
        System.out.println("  languages: "+langs+"  official apps: "+apps);
        System.out.println("  episodes without an episode number: "+noEpisode+"; without an official app: "+noApp+"; items containing a URL: "+urls);
        for(int i=0;i<Math.min(6,latest.size());i++){CatalogItem e=latest.get(i);System.out.println("    "+e.title.name+" · "+e.episodeLabel+" · "+e.title.language+" · "+(e.handoff()?"Watch in "+OfficialAppHandoff.appName(e.playbackReference):"no official app"));}
        if(!shows.isEmpty()){
            CatalogItem s=shows.get(0);List<CatalogItem> eps=SerialCatchupProvider.episodesOf(s,latest);
            System.out.println("  first show: "+s.title.name+" · "+s.episodeLabel+" · "+eps.size()+" episodes listed, newest first:");
            for(CatalogItem e:eps.subList(0,Math.min(4,eps.size())))System.out.println("    "+e.episodeLabel);
        }
        for(CatalogItem e:latest)if(!e.handoff()){System.out.println("  example without an app: "+e.title.name+" · broadcaster '"+e.episode.channelTitle+"'");break;}
    }
}
