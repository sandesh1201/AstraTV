package com.astratv.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.astratv.app.coreui.TvTheme;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/** Delayed muted trailer preview. Cancelled immediately when focus leaves. */
@androidx.media3.common.util.UnstableApi
final class TrailerEngine {
    private final Context context;
    private final MetadataRepository metadata;
    private final ExecutorService io;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<String,TrailerResolver.Source> cache=new ConcurrentHashMap<>();
    private FrameLayout host;
    private Runnable pending;
    private String focused="";
    private ExoPlayer player;
    private View playing;
    TrailerEngine(Context context,MetadataRepository metadata,ExecutorService io){this.context=context;this.metadata=metadata;this.io=io;}
    void attach(FrameLayout host){cancel();this.host=host;}
    void consider(Library.Title title){
        cancel();if(title==null||host==null||"tv".equals(title.type)||!enabled())return;
        focused=title.key();Library.Title target=title;int delay=delayMs();
        pending=()->{if(!focused.equals(target.key()))return;io.execute(()->{TrailerResolver.Source src=resolve(target);main.post(()->{if(focused.equals(target.key())&&src!=null)start(src);});});};
        main.postDelayed(pending,delay);
        Library.Title next=title;io.execute(()->resolve(next));
    }
    void preload(Library.Title title){if(title==null||!enabled())return;io.execute(()->resolve(title));}
    void cancel(){
        focused="";if(pending!=null){main.removeCallbacks(pending);pending=null;}
        clear();
    }
    void release(){cancel();clear();}
    private boolean enabled(){return context.getSharedPreferences("astra_ui_v1",0).getBoolean("trailer.autoplay",true)&&!TvTheme.reduced(context);}
    private boolean muted(){return !context.getSharedPreferences("astra_ui_v1",0).getBoolean("trailer.sound",false);}
    private int delayMs(){return context.getSharedPreferences("astra_ui_v1",0).getInt("trailer.delay",1200);}
    private TrailerResolver.Source resolve(Library.Title title){
        TrailerResolver.Source hit=cache.get(title.key());if(hit!=null)return hit;
        String provider=TrailerResolver.providerUrl(title);
        String tmdbKey="";
        try{JSONObject rec=metadata.cached(title);if(rec!=null)tmdbKey=rec.optString("trailerKey");
            if(tmdbKey.isEmpty()&&ApiKeys.metadataReady(context)){JSONObject fresh=metadata.enrich(title);if(fresh!=null)tmdbKey=fresh.optString("trailerKey");}
        }catch(Exception ignored){}
        String searchKey="";
        if(tmdbKey.isEmpty()&&provider.isEmpty())searchKey=youtubeSearch(title);
        TrailerResolver.Source src=TrailerResolver.pick(provider,tmdbKey,searchKey);
        if(src!=null)cache.put(title.key(),src);return src;
    }
    private String youtubeSearch(Library.Title title){
        String key=ApiKeys.youtube(context);if(key.isEmpty())return "";
        try{
            String q=title.name+" "+(title.year.isEmpty()?"":title.year)+" official trailer";
            String body=ProviderRepository.request("https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=1&q="+URLEncoder.encode(q,"UTF-8").replace("+","%20")+"&key="+URLEncoder.encode(key,"UTF-8"),"").body;
            JSONObject data=new JSONObject(body);JSONArray items=data.optJSONArray("items");if(items==null||items.length()==0)return "";
            JSONObject id=items.getJSONObject(0).optJSONObject("id");return id==null?"":id.optString("videoId");
        }catch(Exception e){return "";}
    }
    private void start(TrailerResolver.Source src){
        if(host==null)return;clear();
        if(src.youtube()){
            YoutubeTrailerView web=new YoutubeTrailerView(context);web.setAlpha(0f);host.addView(web,new FrameLayout.LayoutParams(-1,-1));web.play(src.value,muted());playing=web;
        }else{
            PlayerView view=new PlayerView(context);view.setUseController(false);view.setFocusable(false);view.setAlpha(0f);
            player=new ExoPlayer.Builder(context).build();player.setVolume(muted()?0f:1f);player.setRepeatMode(Player.REPEAT_MODE_ONE);
            player.setMediaItem(MediaItem.fromUri(src.value));view.setPlayer(player);host.addView(view,new FrameLayout.LayoutParams(-1,-1));player.prepare();player.play();playing=view;
        }
        playing.animate().alpha(1f).setDuration(TvTheme.reduced(context)?0:320).start();
    }
    private void clear(){
        if(player!=null){player.release();player=null;}
        if(host!=null)host.removeAllViews();playing=null;
    }
}
