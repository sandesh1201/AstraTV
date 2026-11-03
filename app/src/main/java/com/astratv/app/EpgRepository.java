package com.astratv.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/**
 * Programme guide for Live TV, used only when {@link FeatureFlags.Flag#EXTENDED_EPG} is on and the
 * developer configured an XMLTV source the app is permitted to use ({@code epg.xmltv.url} in
 * local.properties, HTTPS). Nothing is fetched otherwise. The guide is kept in memory for a few
 * hours and covers the next twelve hours.
 */
final class EpgRepository {
    static final long TTL_MS=3L*60*60_000L,BEFORE_MS=2L*60*60_000L,AHEAD_MS=12L*60*60_000L;
    private static final int MAX_BYTES=80*1024*1024;
    private static EpgRepository instance;
    private final Context app;private final Handler main=new Handler(Looper.getMainLooper());
    private Xmltv.Guide guide;private long loadedAt;private boolean loading;private String lastError="";
    private final List<Consumer<Xmltv.Guide>> waiting=new ArrayList<>();

    static synchronized EpgRepository get(Context c){if(instance==null)instance=new EpgRepository(c.getApplicationContext());return instance;}
    private EpgRepository(Context app){this.app=app;}

    static String url(){String u=BuildConfig.EPG_XMLTV_URL;return Library.https(u)?u:"";}
    boolean enabled(){return FeatureFlags.on(app,FeatureFlags.Flag.EXTENDED_EPG)&&!url().isEmpty();}
    String status(){
        if(!FeatureFlags.on(app,FeatureFlags.Flag.EXTENDED_EPG))return "Programme guide is turned off (ENABLE_EXTENDED_EPG).";
        if(url().isEmpty())return "No permitted programme guide (XMLTV) source is configured.";
        if(guide!=null)return "Programme guide: "+guide.channels.size()+" channels, "+guide.programmeCount()+" programmes.";
        return lastError.isEmpty()?"Programme guide loading…":lastError;
    }
    /** Delivers the guide on the main thread, or null when it is disabled or could not be loaded. */
    synchronized void guide(Consumer<Xmltv.Guide> callback){
        if(!enabled()){main.post(()->callback.accept(null));return;}
        long now=System.currentTimeMillis();
        if(guide!=null&&now-loadedAt<TTL_MS){Xmltv.Guide g=guide;main.post(()->callback.accept(g));return;}
        waiting.add(callback);if(loading)return;loading=true;
        Thread t=new Thread(()->{
            Xmltv.Guide g=null;String error="";
            try{g=download(url(),System.currentTimeMillis());}catch(Exception e){error="Programme guide unavailable: "+CatalogSyncCoordinator.describe(e);}
            List<Consumer<Xmltv.Guide>> done;
            synchronized(EpgRepository.this){loading=false;if(g!=null){guide=g;loadedAt=System.currentTimeMillis();lastError="";}else lastError=error;done=new ArrayList<>(waiting);waiting.clear();}
            Xmltv.Guide result=g!=null?g:guide;
            main.post(()->{for(Consumer<Xmltv.Guide> c:done)c.accept(result);});
        },"epg-load");
        t.setPriority(Thread.NORM_PRIORITY-1);t.start();
    }
    private static Xmltv.Guide download(String address,long now)throws Exception{
        if(!Library.https(address))throw new IOException("Only HTTPS sources are supported");
        if(ProviderRiskScanner.privateHost(new URL(address).getHost()))throw new IOException("Source pointed at a local network address, which is blocked");
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setInstanceFollowRedirects(true);c.setConnectTimeout(8000);c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent","SaandioTV/0.4 (Android TV programme guide)");c.setRequestProperty("Accept-Encoding","gzip");
        try{
            int status=c.getResponseCode();if(status<200||status>=300)throw new CatalogHttp.HttpStatusException(status);
            if(!Library.https(c.getURL().toString()))throw new IOException("Only HTTPS sources are supported");
            InputStream in=new LimitedInputStream(new BufferedInputStream(c.getInputStream()),MAX_BYTES);
            if("gzip".equalsIgnoreCase(c.getContentEncoding())||address.endsWith(".gz"))in=new GZIPInputStream(in);
            try(InputStream body=in){return Xmltv.parse(body,now-BEFORE_MS,now+AHEAD_MS,null);}
        }finally{c.disconnect();}
    }
    private static final class LimitedInputStream extends FilterInputStream{
        private long left;LimitedInputStream(InputStream in,long limit){super(in);left=limit;}
        @Override public int read()throws IOException{if(left<=0)throw new IOException("Programme guide is too large");int b=super.read();if(b>=0)left--;return b;}
        @Override public int read(byte[] b,int off,int len)throws IOException{if(left<=0)throw new IOException("Programme guide is too large");int n=super.read(b,off,(int)Math.min(len,left));if(n>0)left-=n;return n;}
    }
}
