package com.astratv.app;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import android.widget.ImageView;
import android.view.ViewTreeObserver;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Artwork loading sized for the view it lands in. Newest requests run first (the cards the viewer
 * is looking at), identical requests share one download, recycled cards cancel their request, and
 * memory and disk caches are bounded.
 */
final class Artwork {
    interface Callback{void ready(Bitmap bitmap);}
    /** Decode targets in pixels. Posters on cards never need more than this; backdrops only on hero/detail screens. */
    static final int POSTER_W=342,POSTER_H=513,BACKDROP_W=1280,BACKDROP_H=720;
    private static final int MAX_QUEUE=48;
    private static final long DISK_LIMIT=80L*1024*1024;
    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(3,3,30,TimeUnit.SECONDS,new LifoQueue(),r->{Thread t=new Thread(r,"artwork");t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>((int)Math.min(32L*1024*1024,Runtime.getRuntime().maxMemory()/8)){@Override protected int sizeOf(String key,Bitmap b){return b.getAllocationByteCount();}};
    private static final Map<String,Request> INFLIGHT=new HashMap<>();
    private static final AtomicBoolean TRIMMED=new AtomicBoolean(false);
    static{IO.allowCoreThreadTimeOut(true);}

    private static final class Request implements Runnable{
        final String url,key;final int w,h;final boolean opaque;final Context context;
        final List<WeakReference<ImageView>> views=new ArrayList<>();final List<Callback> callbacks=new ArrayList<>();
        Request(Context context,String url,String key,int w,int h,boolean opaque){this.context=context;this.url=url;this.key=key;this.w=w;this.h=h;this.opaque=opaque;}
        boolean wanted(){if(!callbacks.isEmpty())return true;for(WeakReference<ImageView> r:views){ImageView v=r.get();if(v!=null&&key.equals(v.getTag(R.id.artwork_key)))return true;}return false;}
        @Override public void run(){
            boolean wanted;synchronized(INFLIGHT){wanted=wanted();if(!wanted)INFLIGHT.remove(key);}
            if(!wanted)return;
            Bitmap bitmap=null;
            try{byte[] data=read(context,url);bitmap=decode(data,w,h,opaque);}catch(Exception ignored){/* card keeps its text fallback */}
            if(bitmap!=null)CACHE.put(key,bitmap);
            Bitmap result=bitmap;List<WeakReference<ImageView>> targets;List<Callback> done;
            synchronized(INFLIGHT){INFLIGHT.remove(key);targets=new ArrayList<>(views);done=new ArrayList<>(callbacks);}
            if(result==null)return;
            MAIN.post(()->{
                for(WeakReference<ImageView> r:targets){ImageView v=r.get();if(v!=null&&key.equals(v.getTag(R.id.artwork_key)))show(v,result,true);}
                for(Callback c:done)c.ready(result);
            });
        }
    }
    /** Executor queue that hands out the newest task first and drops the oldest when full. */
    private static final class LifoQueue extends LinkedBlockingDeque<Runnable>{
        @Override public boolean offer(Runnable r){
            while(size()>=MAX_QUEUE){Runnable dropped=pollLast();if(dropped instanceof Request)synchronized(INFLIGHT){INFLIGHT.remove(((Request)dropped).key);}}
            return offerFirst(r);
        }
        @Override public boolean add(Runnable r){return offer(r);}
        @Override public void put(Runnable r){offer(r);}
    }

    static void load(ImageView view,String url){load(view,url,null);}
    static void load(ImageView view,String url,Callback callback){
        boolean large=view.getLayoutParams()!=null&&view.getLayoutParams().width==-1&&view.getLayoutParams().height>TvTheme_dp(view,300);
        load(view,url,large?BACKDROP_W:POSTER_W,large?BACKDROP_H:POSTER_H,callback);
    }
    static void load(ImageView view,String url,int w,int h,Callback callback){
        String key=key(url,w,h);
        if(key.equals(view.getTag(R.id.artwork_key))&&view.getDrawable()!=null){if(callback!=null){Bitmap b=CACHE.get(key);if(b!=null)callback.ready(b);}return;}
        view.setTag(R.id.artwork_key,key);view.setTag(url);view.setImageDrawable(null);view.setBackgroundColor(Color.TRANSPARENT);
        if(!Library.https(url))return;
        Bitmap cached=CACHE.get(key);if(cached!=null){show(view,cached,false);if(callback!=null)callback.ready(cached);return;}
        trimDiskOnce(view.getContext());
        if(view.isLaidOut()&&view.isShown()){enqueue(view,url,key,w,h,callback);return;}
        ViewTreeObserver.OnPreDrawListener listener=new ViewTreeObserver.OnPreDrawListener(){
            final Rect visible=new Rect();
            @Override public boolean onPreDraw(){
                if(!key.equals(view.getTag(R.id.artwork_key))){if(view.getViewTreeObserver().isAlive())view.getViewTreeObserver().removeOnPreDrawListener(this);return true;}
                if(view.isShown()&&view.getGlobalVisibleRect(visible)&&visible.width()>0&&visible.height()>0){
                    view.getViewTreeObserver().removeOnPreDrawListener(this);enqueue(view,url,key,w,h,callback);
                }return true;
            }
        };view.getViewTreeObserver().addOnPreDrawListener(listener);
    }
    /** Called when a card is recycled; queued work for it is skipped unless another view wants the same image. */
    static void cancel(ImageView view){view.setTag(R.id.artwork_key,null);view.setTag(null);view.setImageDrawable(null);}
    /** Warms the cache for the next card or two only; callers keep prefetch shallow. */
    static void prefetch(Context context,String url){
        if(!Library.https(url))return;String key=key(url,POSTER_W,POSTER_H);if(CACHE.get(key)!=null)return;
        synchronized(INFLIGHT){if(INFLIGHT.containsKey(key))return;Request r=new Request(context.getApplicationContext(),url,key,POSTER_W,POSTER_H,opaque(url));r.callbacks.add(b->{});INFLIGHT.put(key,r);IO.execute(r);}
    }
    static Bitmap cached(String url,int w,int h){return CACHE.get(key(url,w,h));}
    static void trimMemory(int level){if(level>=android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)CACHE.trimToSize(CACHE.maxSize()/2);if(level>=android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE)CACHE.evictAll();}

    private static void enqueue(ImageView view,String url,String key,int w,int h,Callback callback){
        boolean opaque=w<=POSTER_W*2&&opaque(url);
        synchronized(INFLIGHT){
            Request r=INFLIGHT.get(key);boolean fresh=r==null;
            if(fresh){r=new Request(view.getContext().getApplicationContext(),url,key,w,h,opaque);INFLIGHT.put(key,r);}
            r.views.add(new WeakReference<>(view));if(callback!=null)r.callbacks.add(callback);
            if(fresh)IO.execute(r);
        }
    }
    private static void show(ImageView v,Bitmap b,boolean fade){
        v.setBackgroundColor(0xff1c2431);v.setImageBitmap(b);
        if(fade&&!com.astratv.app.coreui.TvTheme.reduced(v.getContext())){v.setAlpha(0f);v.animate().alpha(1f).setDuration(180).start();}else v.setAlpha(1f);
    }
    private static String key(String url,int w,int h){return w+"x"+h+"|"+url;}
    /** Photographic art can drop the alpha channel; logos (often transparent PNG) keep it. */
    static boolean opaque(String url){String u=url.toLowerCase(Locale.ROOT);return u.contains("image.tmdb.org/")||u.endsWith(".jpg")||u.endsWith(".jpeg");}
    private static int TvTheme_dp(ImageView v,int dp){return (int)(dp*v.getResources().getDisplayMetrics().density);}
    /** Samples down to the smallest power of two still covering the target, then RGB_565 for opaque card art. */
    static Bitmap decode(byte[] data,int w,int h,boolean opaque){
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0)return null;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sampleSize(bounds.outWidth,bounds.outHeight,w,h);
        if(opaque)options.inPreferredConfig=Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(data,0,data.length,options);
    }
    static int sampleSize(int srcW,int srcH,int w,int h){int s=1;while(srcW/(s*2)>=w&&srcH/(s*2)>=h)s*=2;return s;}
    private static byte[] read(Context context,String url)throws IOException{
        File file=disk(context,url);if(file.exists()&&file.length()>0&&file.length()<=5*1024*1024){file.setLastModified(System.currentTimeMillis());return bytes(file);}
        byte[] data=download(url);try{file.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(file)){out.write(data);}}catch(Exception ignored){}
        return data;
    }
    private static void trimDiskOnce(Context context){
        if(!TRIMMED.compareAndSet(false,true))return;Context app=context.getApplicationContext();
        IO.execute(()->{try{
            File[] files=new File(app.getCacheDir(),"artwork-v1").listFiles();if(files==null)return;
            long total=0;for(File f:files)total+=f.length();if(total<=DISK_LIMIT)return;
            Arrays.sort(files,Comparator.comparingLong(File::lastModified));
            for(File f:files){if(total<=DISK_LIMIT*3/4)break;long n=f.length();if(f.delete())total-=n;}
        }catch(Exception ignored){/* best effort */}});
    }
    private static File disk(Context context,String url){return new File(new File(context.getCacheDir(),"artwork-v1"),PlaylistParser.stable(url)+".img");}
    private static byte[] bytes(File file)throws IOException{
        try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return out.toByteArray();}
    }
    private static byte[] download(String url)throws IOException{
        String address=url;for(int hop=0;hop<4;hop++){
            if(!Library.https(address))throw new IOException("Unsupported artwork URL");HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(6000);c.setReadTimeout(6000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","SaandioTV/0.4");
            try{int status=c.getResponseCode();if(status>=300&&status<400){String location=c.getHeaderField("Location");if(location==null)throw new IOException("Invalid artwork redirect");address=ProviderParser.absolute(address,location);continue;}if(status!=200||c.getContentLengthLong()>5*1024*1024)throw new IOException("Artwork unavailable");
                try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>5*1024*1024)throw new IOException("Artwork too large");out.write(buf,0,n);}return out.toByteArray();}
            }finally{c.disconnect();}
        }throw new IOException("Artwork redirect limit");
    }
}
