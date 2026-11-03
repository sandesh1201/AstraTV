package com.astratv.app;

import static org.junit.Assert.*;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;

/**
 * Existing Live TV must not change. The fixture is a real, trimmed subset of the iptv-org feeds the
 * app reads (tools/make-live-fixture.js); golden.tsv holds the values the independent audit
 * (tools/live-tv-audit.js) recorded for the same channels. Ids, names, languages, categories,
 * stream hosts and the HTTPS filter must match, and the additive layer must leave the list as is.
 */
public class LiveTvRegressionTest {
    private static String resource(String name)throws IOException{
        try(InputStream in=LiveTvRegressionTest.class.getResourceAsStream("/live/"+name)){
            if(in==null)throw new FileNotFoundException(name);
            return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private static IndianTvProvider.Directory directory()throws Exception{
        return IndianTvProvider.load(url->{
            assertTrue(url.startsWith(IndianTvProvider.DIRECTORY));
            return resource(url.substring(IndianTvProvider.DIRECTORY.length()).replace(".m3u","").replace('/','_')+".m3u");
        });
    }
    private static List<String[]> golden()throws IOException{
        List<String[]> out=new ArrayList<>();
        for(String line:resource("golden.tsv").split("\n"))if(!line.startsWith("#")&&!line.trim().isEmpty())out.add(line.split("\t",-1));
        return out;
    }
    private static String host(String url){try{return new URI(url).getHost();}catch(Exception e){return "";}}

    @Test public void existingChannelsKeepTheirIdsNamesLanguagesCategoriesAndStreams()throws Exception{
        IndianTvProvider.Directory d=directory();
        Map<String,Library.Title> all=new HashMap<>();for(Library.Title t:d.channels)all.put(t.id,t);
        Set<String> shown=new HashSet<>();for(CatalogItem c:LiveTvSource.items(d.channels))shown.add(c.title.id);
        List<String[]> golden=golden();assertTrue("golden snapshot is not empty",golden.size()>=10);
        int listed=0;
        for(String[] g:golden){
            Library.Title t=all.get(g[0]);
            Library.Title probe=new Library.Title(g[0],g[1],"","","","","");probe.provider="news";
            if(SunNetworkChannels.rightsAsserted(probe)){
                assertNull("Sun restreams are removed (owner decision, Turn 9): "+g[0],t);
                assertFalse(shown.contains(g[0]));continue;
            }
            if(!Boolean.parseBoolean(g[4])){
                assertNull("HTTP-only streams are dropped at parse time and never listed: "+g[0],t);
                assertFalse(shown.contains(g[0]));continue;
            }
            listed++;
            assertNotNull("channel still listed: "+g[0],t);
            assertTrue(shown.contains(g[0]));
            assertEquals(g[0]+" name",g[1],t.name);
            assertEquals(g[0]+" language",g[2],t.language);
            assertEquals(g[0]+" category",g[3],t.group);
            assertEquals(g[0]+" stream host",g[5],host(t.url));
            assertEquals("news",t.provider);assertEquals("tv",t.type);
        }
        assertTrue(listed>=7);
    }

    @Test public void additiveLayerLeavesExistingChannelsUntouched()throws Exception{
        List<CatalogItem> existing=LiveTvSource.items(directory().channels);
        List<String> before=new ArrayList<>();for(CatalogItem c:existing)before.add(c.key()+"|"+c.signature()+"|"+c.title.url);

        ChannelDeduplicationService.Result off=UnifiedLiveTv.merge(existing,UnifiedLiveTv.providers(false,true));
        assertSame("flags off returns the existing list itself",existing,off.items);

        ChannelDeduplicationService.Result on=UnifiedLiveTv.merge(existing,UnifiedLiveTv.providers(true,true));
        for(int i=0;i<existing.size();i++)assertSame("existing channel "+i+" kept in place",existing.get(i),on.items.get(i));
        List<String> after=new ArrayList<>();for(CatalogItem c:existing)after.add(c.key()+"|"+c.signature()+"|"+c.title.url);
        assertEquals("ids, displayed fields and stream URLs unchanged",before,after);
        for(CatalogItem c:on.items.subList(existing.size(),on.items.size())){assertTrue(c.handoff());assertEquals("",c.title.url);}
    }

    @Test public void sunRestreamsAreReplacedByTheirOfficialRoute()throws Exception{
        List<CatalogItem> existing=LiveTvSource.items(directory().channels);
        for(CatalogItem c:existing)assertFalse("no Sun restream left in the directory: "+c.title.id,SunNetworkChannels.rightsAsserted(c.title));
        ChannelDeduplicationService.Result r=UnifiedLiveTv.merge(existing,UnifiedLiveTv.providers(true,true));
        Set<String> names=new HashSet<>();for(CatalogItem c:r.items.subList(existing.size(),r.items.size())){names.add(c.title.name);assertTrue(c.handoff());}
        for(String sun:new String[]{"Sun TV","Sun TV HD","Surya TV","Surya TV HD","Gemini TV"})assertTrue(sun+" is offered through Sun NXT",names.contains(sun));
        assertTrue(r.officialRoutes.isEmpty());
        assertEquals(37,names.size());
    }
    @Test public void onlySunOwnedDirectoryIdsAreRemoved(){
        Library.Title sun=new Library.Title("SunTV.in~Tamil","Sun TV HD","","","","","");sun.provider="news";
        Library.Title hd=new Library.Title("SunSurya.in@HD~Malayalam","Sun Surya HD","","","","","");hd.provider="news";
        Library.Title other=new Library.Title("DDNational.in~Hindi","DD National","","","","","");other.provider="news";
        Library.Title official=new Library.Title("sun.sun-tv","Sun TV","","","","","");official.provider=SunNetworkChannels.PROVIDER;
        assertTrue(SunNetworkChannels.rightsAsserted(sun));assertTrue(SunNetworkChannels.rightsAsserted(hd));
        assertFalse(SunNetworkChannels.rightsAsserted(other));assertFalse("the Sun NXT entry itself stays",SunNetworkChannels.rightsAsserted(official));
    }
}
