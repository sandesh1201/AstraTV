package com.astratv.app;

import org.junit.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.Assert.*;

public class CatalogHttpTest {
    private static final String URL="https://api.example.org/list?api_key=secret123";

    private static final class Script implements CatalogHttp.Transport{
        final Deque<CatalogHttp.Reply> replies=new ArrayDeque<>();final List<Map<String,String>> headers=new ArrayList<>();
        @Override public CatalogHttp.Reply get(String url,Map<String,String> h)throws IOException{headers.add(new HashMap<>(h));CatalogHttp.Reply r=replies.poll();if(r==null)throw new IOException("no reply");return r;}
    }
    private final List<Long> sleeps=new ArrayList<>();

    @Test public void revalidatesWithEtagAndServesCachedBodyOn304()throws Exception{
        Script t=new Script();InMemoryCatalogStorage cache=new InMemoryCatalogStorage();
        CatalogHttp http=new CatalogHttp(t,cache,sleeps::add);
        t.replies.add(new CatalogHttp.Reply(200,"{\"v\":1}","\"e1\"","Mon, 02 Nov 2026 10:00:00 GMT",0));
        CatalogHttp.Result first=http.get(URL);
        assertEquals("{\"v\":1}",first.body);assertFalse(first.notModified);
        assertTrue(t.headers.get(0).isEmpty());

        t.replies.add(new CatalogHttp.Reply(304,"","","",0));
        CatalogHttp.Result second=http.get(URL);
        assertTrue(second.notModified);assertEquals("{\"v\":1}",second.body);
        assertEquals("\"e1\"",t.headers.get(1).get("If-None-Match"));
        assertEquals("Mon, 02 Nov 2026 10:00:00 GMT",t.headers.get(1).get("If-Modified-Since"));
    }
    @Test public void cacheKeysNeverContainTheApiKey()throws Exception{
        Script t=new Script();InMemoryCatalogStorage cache=new InMemoryCatalogStorage();
        t.replies.add(new CatalogHttp.Reply(200,"x","","",0));
        new CatalogHttp(t,cache,sleeps::add).get(URL);
        for(String k:cache.http.keySet())assertFalse(k.contains("secret123"));
        assertNotNull(new CatalogHttp(t,cache,sleeps::add).cached(URL));
    }
    @Test public void retriesTransientServerErrorsWithBackoff()throws Exception{
        Script t=new Script();
        t.replies.add(new CatalogHttp.Reply(503,"","","",0));
        t.replies.add(new CatalogHttp.Reply(429,"","","",2000));
        t.replies.add(new CatalogHttp.Reply(200,"ok","","",0));
        assertEquals("ok",new CatalogHttp(t,null,sleeps::add).get(URL).body);
        assertEquals(Arrays.asList(500L,2000L),sleeps);
    }
    @Test public void clientErrorsAreNotRetried(){
        Script t=new Script();t.replies.add(new CatalogHttp.Reply(401,"","","",0));t.replies.add(new CatalogHttp.Reply(200,"late","","",0));
        try{new CatalogHttp(t,null,sleeps::add).get(URL);fail();}
        catch(CatalogHttp.HttpStatusException e){assertEquals(401,e.status);}
        catch(IOException e){fail(e.toString());}
        assertEquals(1,t.headers.size());
    }
    @Test public void givesUpAfterMaxAttempts(){
        Script t=new Script();for(int i=0;i<5;i++)t.replies.add(new CatalogHttp.Reply(500,"","","",0));
        try{new CatalogHttp(t,null,sleeps::add).get(URL);fail();}catch(IOException expected){/* ok */}
        assertEquals(CatalogHttp.MAX_ATTEMPTS,t.headers.size());
    }
}
