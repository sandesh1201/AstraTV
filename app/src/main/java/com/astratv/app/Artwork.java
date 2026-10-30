package com.astratv.app;

import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import android.widget.ImageView;
import android.view.ViewTreeObserver;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.util.concurrent.*;

/** Bounded, sampled artwork loading off the UI thread. */
final class Artwork {
    private static final ExecutorService IO=Executors.newFixedThreadPool(3);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>(24*1024*1024){@Override protected int sizeOf(String key,Bitmap b){return b.getAllocationByteCount();}};
    static void load(ImageView view,String url){
        view.setTag(url);view.setImageDrawable(null);view.setBackgroundColor(android.graphics.Color.TRANSPARENT);if(!Library.https(url))return;
        Bitmap cached=CACHE.get(url);if(cached!=null){view.setBackgroundColor(0xff1c2431);view.setImageBitmap(cached);return;}
        // Horizontal shelves can contain many off-screen cards. Defer their network work
        // until they actually enter the viewport, instead of downloading every poster.
        ViewTreeObserver.OnPreDrawListener listener=new ViewTreeObserver.OnPreDrawListener(){
            final Rect visible=new Rect();
            @Override public boolean onPreDraw(){
                if(!url.equals(view.getTag())){if(view.getViewTreeObserver().isAlive())view.getViewTreeObserver().removeOnPreDrawListener(this);return true;}
                if(view.isShown()&&view.getGlobalVisibleRect(visible)&&visible.width()>0&&visible.height()>0){
                    view.getViewTreeObserver().removeOnPreDrawListener(this);fetch(view,url);
                }return true;
            }
        };view.getViewTreeObserver().addOnPreDrawListener(listener);
    }
    private static void fetch(ImageView view,String url){
        WeakReference<ImageView> ref=new WeakReference<>(view);
        IO.execute(()->{try{
            byte[] data=download(url);
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(bounds.outWidth/options.inSampleSize>1600||bounds.outHeight/options.inSampleSize>1000)options.inSampleSize*=2;
            Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,options);if(bitmap==null)return;CACHE.put(url,bitmap);
            MAIN.post(()->{ImageView target=ref.get();if(target!=null&&url.equals(target.getTag())){target.setBackgroundColor(0xff1c2431);target.setImageBitmap(bitmap);target.setAlpha(0f);target.animate().alpha(1f).setDuration(220).start();}});
        }catch(Exception ignored){/* Title stays readable on artwork/network failure. */}});
    }
    private static byte[] download(String url)throws IOException{
        String address=url;for(int hop=0;hop<4;hop++){
            if(!Library.https(address))throw new IOException("Unsupported artwork URL");HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(6000);c.setReadTimeout(6000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","AstraTV/0.3");
            try{int status=c.getResponseCode();if(status>=300&&status<400){String location=c.getHeaderField("Location");if(location==null)throw new IOException("Invalid artwork redirect");address=ProviderParser.absolute(address,location);continue;}if(status!=200||c.getContentLengthLong()>5*1024*1024)throw new IOException("Artwork unavailable");
                try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>5*1024*1024)throw new IOException("Artwork too large");out.write(buf,0,n);}return out.toByteArray();}
            }finally{c.disconnect();}
        }throw new IOException("Artwork redirect limit");
    }
}
