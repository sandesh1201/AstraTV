package com.astratv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.session.*;
import androidx.media3.session.MediaController;
import androidx.media3.ui.PlayerView;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Full-screen native player; Media3 supplies focusable TV controls, tracks and subtitles. */
@androidx.media3.common.util.UnstableApi
public class PlayerActivity extends Activity {
    private static final ExecutorService REFRESH=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"playback-refresh");t.setDaemon(true);return t;});
    private static ProviderRepository providers;
    private PlayerView view;
    private ListenableFuture<MediaController> future;
    private MediaController controller;
    private TextView error,quality,speed,loading,hint;
    private LinearLayout message;
    private boolean starting=true,active=false,everReady=false,waitingForNetwork=false;
    private int retries=0,refreshes=0;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Runnable retryTask;
    private ConnectivityManager.NetworkCallback networkCallback;
    private final ArrayDeque<Long> stalls=new ArrayDeque<>();
    private long lastStallHint;
    private static final float[] SPEEDS={0.75f,1f,1.25f,1.5f,2f};

    /** Everything the player needs from a validated candidate. The title JSON lets the player ask the provider for a fresh signed link. */
    static Intent intent(Context context,Library.Title title,PlaybackCandidate c){
        Intent i=new Intent(context,PlayerActivity.class).putExtra("id",title.key()).putExtra("title",title.name).putExtra("provider",c.providerId)
            .putExtra("url",c.url).putExtra("mimeType",c.mimeType).putExtra("headers",new JSONObject(c.headers).toString()).putExtra("subtitles",c.subtitles.toString())
            .putExtra("stream",c.name).putExtra("label",c.label()).putExtra("expiresAt",c.expiresAt).putExtra("drmScheme",c.drmScheme).putExtra("drmLicenseUrl",c.drmLicenseUrl);
        try{i.putExtra("titleJson",title.json().toString());}catch(Exception ignored){/* link refresh unavailable for this title */}
        return i;
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff05070b);
        view=new PlayerView(this);view.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);view.setControllerShowTimeoutMs(3500);view.setShowSubtitleButton(true);view.setShowNextButton(false);view.setShowPreviousButton(false);view.setShowFastForwardButton(true);view.setShowRewindButton(true);root.addView(view,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout basic=view.findViewById(androidx.media3.ui.R.id.exo_basic_controls);
        quality=controlButton("Quality · Auto",this::chooseQuality);speed=controlButton("Speed · 1×",this::chooseSpeed);
        if(basic!=null){basic.addView(quality,0);basic.addView(speed,1);}
        loading=new TextView(this);loading.setTextColor(0xffedf1f6);loading.setTextSize(18);loading.setGravity(Gravity.CENTER);loading.setPadding(40,0,40,120);
        String title=getIntent().getStringExtra("title"),label=getIntent().getStringExtra("label");
        loading.setText("Starting "+(title==null?"playback":title)+"…"+(label==null||label.isEmpty()?"":"\n"+label));
        root.addView(loading,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        hint=new TextView(this);hint.setTextColor(0xffedf1f6);hint.setTextSize(14);hint.setPadding(28,14,28,14);hint.setBackgroundColor(0xd0080b12);hint.setVisibility(View.GONE);
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);hp.bottomMargin=140;root.addView(hint,hp);
        message=new LinearLayout(this);message.setOrientation(LinearLayout.VERTICAL);message.setPadding(40,20,40,20);message.setBackgroundColor(0xe6080b12);
        error=new TextView(this);error.setTextColor(0xffedf1f6);error.setTextSize(17);message.addView(error);message.setVisibility(View.GONE);
        Button retry=new Button(this);retry.setText("Retry playback");retry.setOnClickListener(v->manualRetry());message.addView(retry);
        Button back=new Button(this);back.setText("Back to title");back.setOnClickListener(v->finish());message.addView(back);
        root.addView(message,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));setContentView(root);
        view.setErrorMessageProvider(e->android.util.Pair.create(e.errorCode,"Playback failed. Check this source or choose another one."));
    }
    private TextView controlButton(String label,Runnable click){
        TextView v=new TextView(this);v.setText(label);v.setTextColor(0xffffffff);v.setTextSize(13);v.setPadding(24,12,24,12);v.setFocusable(true);v.setClickable(true);v.setGravity(Gravity.CENTER);
        v.setBackgroundColor(0x00000000);v.setOnClickListener(w->click.run());
        v.setOnFocusChangeListener((w,f)->{v.setBackgroundColor(f?0xffffffff:0x00000000);v.setTextColor(f?0xff08090c:0xffffffff);});
        return v;
    }
    @Override protected void onStart(){
        super.onStart();active=true;PlaybackService.visiblePlayers++;Intent service=new Intent(this,PlaybackService.class);
        if(starting&&getIntent().hasExtra("url"))service.putExtras(getIntent());starting=false;
        startService(service);
        future=new MediaController.Builder(this,new SessionToken(this,new ComponentName(this,PlaybackService.class)))
            .setListener(new MediaController.Listener(){@Override public void onExtrasChanged(MediaController c,Bundle extras){updateQualityLabel();}})
            .buildAsync();
        future.addListener(()->{try{
            if(!active||isFinishing()||isDestroyed())return;controller=future.get();view.setPlayer(controller);view.requestFocus();
            controller.addListener(new Player.Listener(){
                @Override public void onPlayerError(PlaybackException e){failed(e);}
                @Override public void onPlaybackStateChanged(int state){
                    if(state==Player.STATE_READY){if(!everReady){PlaybackTrace.log(PlaybackTrace.Stage.READY,traceKey(),getIntent().getStringExtra("label"));validateChannel();}retries=0;everReady=true;loading.setVisibility(View.GONE);message.setVisibility(View.GONE);}
                    else if(state==Player.STATE_BUFFERING&&everReady&&controller!=null&&controller.getPlayWhenReady())stalled();
                }
                @Override public void onVideoSizeChanged(VideoSize size){updateQualityLabel();}
                @Override public void onTracksChanged(Tracks tracks){updateQualityLabel();}
                @Override public void onPlaybackParametersChanged(PlaybackParameters params){speed.setText("Speed · "+speedLabel(params.speed));}
            });
            if(controller.getPlaybackState()==Player.STATE_READY){everReady=true;loading.setVisibility(View.GONE);}
            updateQualityLabel();speed.setText("Speed · "+speedLabel(controller.getPlaybackParameters().speed));
            long expiresAt=getIntent().getLongExtra("expiresAt",0);
            if(expiresAt>0&&System.currentTimeMillis()>=expiresAt-PlaybackCandidate.EXPIRY_GRACE_MS)refreshLink();
        }catch(Exception e){Diagnostics.get().provider(Diagnostics.Problem.MEDIA3_INIT,getIntent().getStringExtra("provider"),e.getClass().getSimpleName());showError("Could not connect to the playback service.");}},command->handler.post(command));
    }

    private String traceKey(){return CatalogViews.join(getIntent().getStringExtra("provider"),getIntent().getStringExtra("id"));}
    /** Live channels: records a playback validation once the stream has kept playing and advancing for a short window. */
    private void validateChannel(){
        String key=getIntent().getStringExtra("id");
        if(controller==null||key==null||!key.startsWith("news:"))return;
        long startPos=controller.getCurrentPosition(),startAt=android.os.SystemClock.elapsedRealtime();
        handler.postDelayed(()->{
            if(!active||controller==null)return;
            long elapsed=android.os.SystemClock.elapsedRealtime()-startAt;
            if(ChannelValidation.continuous(startPos,controller.getCurrentPosition(),elapsed,controller.isPlaying())){
                ChannelValidation.recordPlayback(this,key,System.currentTimeMillis());
                CatalogRepository.get(this).touched(LiveTvSource.ROW,CatalogRepository.HOME_LIVE,CatalogRepository.ROW_FAVORITES);
            }
        },ChannelValidation.CONTINUITY_MS+500);
    }

    // ---- failures --------------------------------------------------------------------------
    private void failed(PlaybackException e){
        loading.setVisibility(View.GONE);
        long expiresAt=getIntent().getLongExtra("expiresAt",0);
        boolean expired=expiresAt>0&&System.currentTimeMillis()>=expiresAt-PlaybackCandidate.EXPIRY_GRACE_MS;
        int status=PlaybackService.lastHttpStatus;
        PlaybackErrors.Verdict v=PlaybackErrors.classify(e.errorCode,status,expired,NetworkStatus.online(this));
        Diagnostics.get().playbackError(getIntent().getStringExtra("provider"),v.category,e.getErrorCodeName(),status);
        Diagnostics.Problem problem=Diagnostics.ofPlayback(v.category,e.errorCode);
        if(problem!=null)Diagnostics.get().provider(problem,getIntent().getStringExtra("provider"),e.getErrorCodeName());
        PlaybackTrace.log(PlaybackTrace.Stage.ERROR,traceKey(),v.category.name()+" · "+e.getErrorCodeName()+(status>0?" · HTTP "+status:""));
        if(v.refreshUrl()){refreshLink();return;}
        if(v.category==PlaybackErrors.Category.NETWORK&&!NetworkStatus.online(this)){waitForNetwork(v.message);return;}
        long delay=PlaybackErrors.retryDelayMs(v,retries);
        if(delay>=0){
            retries++;
            if(delay>0)showError(v.message+" Retrying in "+Math.max(1,delay/1000)+" s (attempt "+retries+")…");
            boolean behindLive=v.category==PlaybackErrors.Category.BEHIND_LIVE;
            schedule(()->{if(controller==null)return;if(behindLive)controller.seekToDefaultPosition();controller.prepare();controller.play();},delay);
            return;
        }
        showError(v.message+(v.transientError?" Automatic retries did not help.":"")+" Try another source, or retry.");
    }
    private void schedule(Runnable r,long delay){
        if(retryTask!=null)handler.removeCallbacks(retryTask);
        retryTask=()->{retryTask=null;r.run();};handler.postDelayed(retryTask,delay);
    }
    private void manualRetry(){
        if(retryTask!=null){handler.removeCallbacks(retryTask);retryTask=null;}
        retries=0;message.setVisibility(View.GONE);
        long expiresAt=getIntent().getLongExtra("expiresAt",0);
        if(expiresAt>0&&System.currentTimeMillis()>=expiresAt-PlaybackCandidate.EXPIRY_GRACE_MS){refreshes=0;refreshLink();return;}
        if(controller!=null){controller.prepare();controller.play();}
    }
    /** Offline: wait for the default network instead of burning retries, then resume from the saved position. */
    private void waitForNetwork(String text){
        showError(text);if(waitingForNetwork)return;
        ConnectivityManager cm=getSystemService(ConnectivityManager.class);if(cm==null)return;
        waitingForNetwork=true;
        networkCallback=new ConnectivityManager.NetworkCallback(){@Override public void onAvailable(Network n){handler.post(()->{
            if(!waitingForNetwork)return;stopWaitingForNetwork();retries=0;message.setVisibility(View.GONE);
            if(controller!=null){controller.prepare();controller.play();}
        });}};
        try{cm.registerDefaultNetworkCallback(networkCallback);}catch(RuntimeException e){waitingForNetwork=false;networkCallback=null;}
    }
    private void stopWaitingForNetwork(){
        waitingForNetwork=false;if(networkCallback==null)return;
        try{ConnectivityManager cm=getSystemService(ConnectivityManager.class);if(cm!=null)cm.unregisterNetworkCallback(networkCallback);}catch(RuntimeException ignored){/* already unregistered */}
        networkCallback=null;
    }
    /** Asks the provider (through its permitted API) for a new signed link and resumes at the saved position. */
    private void refreshLink(){
        String json=getIntent().getStringExtra("titleJson"),wanted=getIntent().getStringExtra("stream");
        if(json==null){showError("The playback link expired and this source cannot issue a new one. Open the title again.");return;}
        if(refreshes>=PlaybackErrors.MAX_URL_REFRESHES){showError("The provider keeps returning expired links. Try again later.");return;}
        refreshes++;showError("The playback link expired. Requesting a fresh link from the provider…");
        Context app=getApplicationContext();String country=getResources().getConfiguration().getLocales().get(0).getCountry();
        REFRESH.execute(()->{
            try{
                Library.Title t=Library.Title.from(new JSONObject(json));
                List<ProviderRepository.Stream> streams=repository(app).streams(t);
                ProviderRepository.Stream pick=streams.get(0);for(ProviderRepository.Stream s:streams)if(s.name.equals(wanted))pick=s;
                PlaybackCandidate c=PlaybackCandidate.of(t.provider,pick);String problem=c.problem(System.currentTimeMillis(),country,PlaybackService.widevineSupported());
                if(problem!=null)throw new java.io.IOException(problem);
                handler.post(()->{
                    if(!active)return;Intent next=intent(this,t,c).putExtra("retry",true);setIntent(next);message.setVisibility(View.GONE);loading.setVisibility(View.VISIBLE);
                    startService(new Intent(this,PlaybackService.class).putExtras(next));
                });
            }catch(Exception e){handler.post(()->showError("A fresh playback link could not be obtained. "+ProviderRepository.message(e)));}
        });
    }
    private static synchronized ProviderRepository repository(Context app){if(providers==null)providers=new ProviderRepository(app);return providers;}
    private void stalled(){
        long now=System.currentTimeMillis();Diagnostics.get().rebuffers.incrementAndGet();
        stalls.addLast(now);while(!stalls.isEmpty()&&now-stalls.peekFirst()>120_000L)stalls.removeFirst();
        if(stalls.size()<3||now-lastStallHint<120_000L)return;
        lastStallHint=now;boolean adaptive=renditions().size()>1&&quality.getText().toString().contains("Auto");
        hint.setText(adaptive?"Your connection is slower than this stream. Quality is adjusting automatically.":"Your connection is slower than this stream. Try a lower quality or another source.");
        hint.setVisibility(View.VISIBLE);handler.postDelayed(()->hint.setVisibility(View.GONE),8000);
    }
    private void showError(String text){error.setText(text);message.setVisibility(View.VISIBLE);}

    // ---- quality and speed -----------------------------------------------------------------
    /** Video renditions the stream actually offers, by decoded height. */
    private List<int[]> renditions(){
        List<int[]> out=new ArrayList<>();if(controller==null)return out;
        ImmutableList<Tracks.Group> groups=controller.getCurrentTracks().getGroups();
        for(int g=0;g<groups.size();g++){Tracks.Group group=groups.get(g);if(group.getType()!=C.TRACK_TYPE_VIDEO)continue;
            for(int i=0;i<group.length;i++)if(group.isTrackSupported(i)&&group.getTrackFormat(i).height>0)out.add(new int[]{g,i,group.getTrackFormat(i).height});}
        out.sort((a,b)->Integer.compare(b[2],a[2]));return out;
    }
    private void chooseQuality(){
        if(controller==null)return;List<int[]> options=renditions();
        if(options.isEmpty()){new AlertDialog.Builder(this).setTitle("Quality").setMessage("This source offers a single rendition, so there is nothing to choose. Playing at "+(currentHeight()>0?PlaybackPolicy.heightLabel(currentHeight()):"its native quality")+".").setPositiveButton("OK",null).show();return;}
        List<String> labels=new ArrayList<>();labels.add("Auto (adapts to your connection)");Set<Integer> seen=new HashSet<>();List<int[]> unique=new ArrayList<>();
        for(int[] o:options)if(seen.add(o[2])){unique.add(o);labels.add(PlaybackPolicy.heightLabel(o[2]));}
        new AlertDialog.Builder(this).setTitle("Quality").setItems(labels.toArray(new String[0]),(d,w)->{
            TrackSelectionParameters.Builder params=controller.getTrackSelectionParameters().buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO);
            if(w>0){int[] pick=unique.get(w-1);params.addOverride(new TrackSelectionOverride(controller.getCurrentTracks().getGroups().get(pick[0]).getMediaTrackGroup(),pick[1]));}
            controller.setTrackSelectionParameters(params.build());updateQualityLabel();
        }).setNegativeButton("Cancel",null).show();
    }
    private void chooseSpeed(){
        if(controller==null)return;String[] labels=new String[SPEEDS.length];for(int i=0;i<SPEEDS.length;i++)labels[i]=speedLabel(SPEEDS[i]);
        new AlertDialog.Builder(this).setTitle("Playback speed").setItems(labels,(d,w)->controller.setPlaybackSpeed(SPEEDS[w])).setNegativeButton("Cancel",null).show();
    }
    private int currentHeight(){
        if(controller==null)return 0;
        int decoded=controller.getSessionExtras().getInt(PlaybackService.EXTRA_VIDEO_HEIGHT,0);
        return decoded>0?decoded:controller.getVideoSize().height;
    }
    private void updateQualityLabel(){
        if(controller==null||quality==null)return;
        boolean auto=true;for(TrackSelectionOverride o:controller.getTrackSelectionParameters().overrides.values())if(o.getType()==C.TRACK_TYPE_VIDEO)auto=false;
        String now=PlaybackPolicy.heightLabel(currentHeight());
        quality.setText("Quality · "+(auto?"Auto":"Fixed")+(now.isEmpty()?"":" · "+now));
    }
    private static String speedLabel(float s){return (s==(int)s?String.valueOf((int)s):String.valueOf(s))+"×";}
    @Override protected void onStop(){
        active=false;PlaybackService.visiblePlayers=Math.max(0,PlaybackService.visiblePlayers-1);handler.removeCallbacksAndMessages(null);retryTask=null;stopWaitingForNetwork();
        if(!getSharedPreferences("astra",0).getBoolean("background",false)){if(controller!=null)controller.pause();else startService(new Intent(this,PlaybackService.class).setAction("pause"));}
        view.setPlayer(null);if(future!=null)MediaController.releaseFuture(future);controller=null;super.onStop();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){return view.dispatchKeyEvent(event)||super.dispatchKeyEvent(event);}
    @Override public void onBackPressed(){if(view.isControllerFullyVisible())view.hideController();else finish();}
}
