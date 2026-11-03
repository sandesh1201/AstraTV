package com.astratv.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.astratv.app.coreui.TvTheme;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * Muted background trailer for the focused or selected movie. Separate from full-movie playback
 * ({@link FullMoviePlaybackController}): it never opens the player screen and never counts as watching.
 *
 * The backdrop is always drawn first. After {@link #delayMs()} without another focus change the
 * trailer is resolved (cache → provider trailer file → TMDB official trailer → YouTube search
 * checked by {@link TrailerMatcher}) and started in the one preview view. The view stays invisible
 * until the player reports PLAYING; an error, a timeout or a focus change hides it again, so the
 * viewer sees the backdrop rather than a black box or a spinner.
 */
@androidx.media3.common.util.UnstableApi
final class TrailerPreviewController {
    static final int DEFAULT_DELAY_MS=700,START_TIMEOUT_MS=8000;
    /** True while nothing on screen overlaps the preview area (YouTube's player must not be covered). */
    interface Gate{boolean clear();}

    private final Context context;
    private final MetadataRepository metadata;
    private final ExecutorService io;
    private final TrailerCache cache;
    private final Handler main=new Handler(Looper.getMainLooper());
    private FrameLayout host;private Gate gate=()->true;
    private Runnable pending,timeout;
    private int generation,shownGeneration=-1;
    private String focusedKey="",playingVideo="",playingTitle="";
    private YoutubeTrailerView web;
    private ExoPlayer player;private PlayerView playerView;

    TrailerPreviewController(Context context,MetadataRepository metadata,ExecutorService io){
        this.context=context;this.metadata=metadata;this.io=io;this.cache=new TrailerCache(context);
    }

    /** New screen: drops the previous preview view entirely. */
    void attach(FrameLayout host,Gate gate){release();this.host=host;this.gate=gate==null?()->true:gate;if(host!=null)host.setVisibility(View.INVISIBLE);}
    /** Focus moved to {@code title}: stop whatever plays now and schedule this title's trailer. */
    void consider(Library.Title title){
        cancel();
        if(title==null||host==null||!enabled()||"tv".equals(title.type)||"series".equals(title.type)||"episode".equals(title.type))return;
        int g=generation;focusedKey=title.key();Library.Title target=title;
        pending=()->{if(g!=generation)return;io.execute(()->{TrailerResolver.Source src=resolve(target);main.post(()->{
            if(g!=generation||host==null)return;
            if(src==null){Diagnostics.get().trailer("none",target.key());return;}
            if(!gate.clear()){Diagnostics.get().trailer("covered",target.key());return;}
            start(src,target,g);
        });});};
        main.postDelayed(pending,delayMs());
    }
    /** Something now overlaps the preview area (scrolling): hide and stop it. */
    void obstructed(){if(host!=null&&host.getVisibility()==View.VISIBLE)cancel();}
    /** Focus left: pause/stop and show the backdrop. The preview view is kept for reuse. */
    void cancel(){
        generation++;focusedKey="";
        if(pending!=null){main.removeCallbacks(pending);pending=null;}
        if(timeout!=null){main.removeCallbacks(timeout);timeout=null;}
        stop();
    }
    /** Screen hidden or destroyed: frees the WebView and any ExoPlayer. */
    void release(){
        cancel();
        if(web!=null){web.setListener(null);if(web.getParent()!=null)((android.view.ViewGroup)web.getParent()).removeView(web);web.destroy();web=null;}
        if(host!=null)host.removeAllViews();
    }
    boolean showing(){return host!=null&&host.getVisibility()==View.VISIBLE;}

    private boolean enabled(){return context.getSharedPreferences("astra_ui_v1",0).getBoolean("trailer.autoplay",true)&&!TvTheme.reduced(context);}
    private boolean muted(){return !context.getSharedPreferences("astra_ui_v1",0).getBoolean("trailer.sound",false);}
    int delayMs(){return context.getSharedPreferences("astra_ui_v1",0).getInt("trailer.delay",DEFAULT_DELAY_MS);}

    // ---- resolution (background thread) --------------------------------------------------------
    private TrailerResolver.Source resolve(Library.Title t){
        long now=System.currentTimeMillis();String key=t.key();
        String provider=TrailerResolver.providerUrl(t);
        if(!provider.isEmpty())return new TrailerResolver.Source(TrailerResolver.Kind.PROVIDER,provider);
        TrailerCache.Entry hit=cache.get(key,now);
        if(hit!=null)return hit.hit()?new TrailerResolver.Source("search".equals(hit.source)?TrailerResolver.Kind.YOUTUBE_SEARCH:TrailerResolver.Kind.TMDB_YOUTUBE,hit.videoId):null;
        String official="";
        try{JSONObject rec=metadata.cached(t);if(rec!=null)official=rec.optString("officialTrailerKey");
            if(official.isEmpty()&&rec==null&&ApiKeys.metadataReady(context)){JSONObject fresh=metadata.enrich(t);if(fresh!=null)official=fresh.optString("officialTrailerKey");}
        }catch(Exception ignored){/* metadata is optional */}
        if(YoutubeTrailerView.validId(official)&&!cache.bad(official,now)){cache.put(key,official,"tmdb",now);return new TrailerResolver.Source(TrailerResolver.Kind.TMDB_YOUTUBE,official);}
        String found=search(t,now);
        if(found.isEmpty()){cache.put(key,"","",now);return null;}
        cache.put(key,found,"search",now);
        return new TrailerResolver.Source(TrailerResolver.Kind.YOUTUBE_SEARCH,found);
    }
    /** YouTube Data API through the catalog service (release) or a developer key (debug). Empty when neither is configured or nothing passes. */
    private String search(Library.Title t,long now){
        String query=TrailerMatcher.query(t.name,t.year,t.language);
        String country=context.getResources().getConfiguration().getLocales().get(0).getCountry();
        try{
            JSONObject videos=null;
            String backend=YoutubeTrailerSearch.backendUrl(BuildConfig.CATALOG_API_BASE,query);
            if(backend!=null){
                Map<String,String> headers=BuildConfig.CATALOG_CLIENT_TOKEN.isEmpty()?Collections.emptyMap():Collections.singletonMap(TmdbRoute.CLIENT_HEADER,BuildConfig.CATALOG_CLIENT_TOKEN);
                videos=new JSONObject(ProviderRepository.request(backend,"",headers).body);
            }else{
                String key=ApiKeys.youtube(context);if(key.isEmpty())return "";
                List<String> ids=YoutubeTrailerSearch.searchIds(new JSONObject(ProviderRepository.request(YoutubeTrailerSearch.searchUrl(query,key),"").body));
                if(ids.isEmpty())return "";
                videos=new JSONObject(ProviderRepository.request(YoutubeTrailerSearch.videosUrl(ids,key),"").body);
            }
            List<TrailerMatcher.Candidate> candidates=new ArrayList<>();
            for(TrailerMatcher.Candidate c:YoutubeTrailerSearch.candidates(videos,country))if(!cache.bad(c.videoId,now))candidates.add(c);
            TrailerMatcher.Candidate best=TrailerMatcher.best(t.name,t.year,t.language,candidates);
            Diagnostics.get().trailer(best==null?"no official match":"matched",t.name+" · "+candidates.size()+" candidates");
            return best==null?"":best.videoId;
        }catch(Exception e){Diagnostics.get().trailer("search failed",ProviderRepository.message(e));return "";}
    }

    // ---- playback (main thread) --------------------------------------------------------------
    private void start(TrailerResolver.Source src,Library.Title t,int g){
        stop();playingTitle=t.key();
        timeout=()->{if(g==generation)fail(g,"timeout",0);};
        main.postDelayed(timeout,START_TIMEOUT_MS);
        if(src.youtube()){
            if(web==null){web=new YoutubeTrailerView(context);host.addView(web,new FrameLayout.LayoutParams(-1,-1));}
            web.setAlpha(0f);host.setVisibility(View.VISIBLE);playingVideo=src.value;
            web.setListener(new YoutubeTrailerView.Listener(){
                @Override public void onPlaying(){if(g==generation)shown(g,web);}
                @Override public void onError(int code){if(g==generation)fail(g,"youtube error "+code,code);}
            });
            if(!web.play(src.value,muted()))fail(g,"invalid id",2);
        }else{
            playerView=new PlayerView(context);playerView.setUseController(false);playerView.setFocusable(false);playerView.setAlpha(0f);
            player=new ExoPlayer.Builder(context).build();player.setVolume(muted()?0f:1f);player.setRepeatMode(Player.REPEAT_MODE_ONE);
            player.addListener(new Player.Listener(){
                @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_READY&&g==generation)shown(g,playerView);}
                @Override public void onPlayerError(PlaybackException e){if(g==generation)fail(g,"provider trailer "+e.getErrorCodeName(),0);}
            });
            player.setMediaItem(MediaItem.fromUri(src.value));playerView.setPlayer(player);host.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
            host.setVisibility(View.VISIBLE);player.prepare();player.play();
        }
    }
    private void shown(int g,View v){
        if(g!=generation||v==null)return;
        if(timeout!=null){main.removeCallbacks(timeout);timeout=null;}
        if(!gate.clear()){cancel();return;}
        if(shownGeneration==g)return;
        shownGeneration=g;Diagnostics.get().trailersStarted.incrementAndGet();
        v.animate().alpha(1f).setDuration(TvTheme.reduced(context)?0:320).start();
    }
    private void fail(int g,String why,int youtubeError){
        if(g!=generation)return;
        Diagnostics.get().trailerFallbacks.incrementAndGet();Diagnostics.get().trailer("fallback",why);
        long now=System.currentTimeMillis();
        if(!playingVideo.isEmpty()&&TrailerCache.permanent(youtubeError)){cache.markBad(playingVideo,now);cache.forget(playingTitle);}
        generation++;
        if(timeout!=null){main.removeCallbacks(timeout);timeout=null;}
        stop();
    }
    private void stop(){
        playingVideo="";
        if(player!=null){player.release();player=null;}
        if(playerView!=null){if(host!=null)host.removeView(playerView);playerView=null;}
        if(web!=null){web.animate().cancel();web.setAlpha(0f);web.stopPreview();}
        if(host!=null)host.setVisibility(View.INVISIBLE);
    }
}
