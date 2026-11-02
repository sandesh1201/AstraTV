package com.astratv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
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
import java.util.*;

/** Full-screen native player; Media3 supplies focusable TV controls, tracks and subtitles. */
@androidx.media3.common.util.UnstableApi
public class PlayerActivity extends Activity {
    private PlayerView view;
    private ListenableFuture<MediaController> future;
    private MediaController controller;
    private TextView error,quality,speed;
    private boolean starting=true;
    private boolean active=false;
    private int retries=0;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private static final float[] SPEEDS={0.75f,1f,1.25f,1.5f,2f};
    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff05070b);
        view=new PlayerView(this);view.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);view.setControllerShowTimeoutMs(3500);view.setShowSubtitleButton(true);view.setShowNextButton(false);view.setShowPreviousButton(false);view.setShowFastForwardButton(true);view.setShowRewindButton(true);root.addView(view,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout basic=view.findViewById(androidx.media3.ui.R.id.exo_basic_controls);
        quality=controlButton("Quality · Auto",this::chooseQuality);speed=controlButton("Speed · 1×",this::chooseSpeed);
        if(basic!=null){basic.addView(quality,0);basic.addView(speed,1);}
        LinearLayout message=new LinearLayout(this);message.setOrientation(LinearLayout.VERTICAL);message.setPadding(40,20,40,20);message.setBackgroundColor(0xe6080b12);
        error=new TextView(this);error.setTextColor(0xffedf1f6);error.setTextSize(17);message.addView(error);message.setVisibility(View.GONE);
        Button retry=new Button(this);retry.setText("Retry playback");retry.setOnClickListener(v->{if(controller!=null){retries=0;controller.prepare();controller.play();message.setVisibility(View.GONE);}});message.addView(retry);
        Button back=new Button(this);back.setText("Back to title");back.setOnClickListener(v->finish());message.addView(back);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);root.addView(message,p);setContentView(root);
        view.setErrorMessageProvider(e->android.util.Pair.create(e.errorCode,"Playback failed. Check this source or choose another one."));
        error.setTag(message);
    }
    private TextView controlButton(String label,Runnable click){
        TextView v=new TextView(this);v.setText(label);v.setTextColor(0xffffffff);v.setTextSize(13);v.setPadding(24,12,24,12);v.setFocusable(true);v.setClickable(true);v.setGravity(Gravity.CENTER);
        v.setBackgroundColor(0x00000000);v.setOnClickListener(w->click.run());
        v.setOnFocusChangeListener((w,f)->{v.setBackgroundColor(f?0xffffffff:0x00000000);v.setTextColor(f?0xff08090c:0xffffffff);});
        return v;
    }
    @Override protected void onStart(){
        super.onStart();active=true;Intent service=new Intent(this,PlaybackService.class);
        if(starting&&getIntent().hasExtra("url"))service.putExtras(getIntent());starting=false;
        startService(service);
        future=new MediaController.Builder(this,new SessionToken(this,new ComponentName(this,PlaybackService.class)))
            .setListener(new MediaController.Listener(){@Override public void onExtrasChanged(MediaController c,Bundle extras){updateQualityLabel();}})
            .buildAsync();
        future.addListener(()->{try{
            if(!active||isFinishing()||isDestroyed())return;controller=future.get();view.setPlayer(controller);view.requestFocus();
            controller.addListener(new Player.Listener(){
                @Override public void onPlayerError(PlaybackException e){
                    long delay=PlaybackPolicy.retryDelayMs(retries,e.errorCode);
                    if(delay>=0){
                        retries++;error.setText("Connection interrupted. Retrying in "+Math.max(1,delay/1000)+" s (attempt "+retries+" of "+PlaybackPolicy.MAX_RETRIES+")…");((View)error.getTag()).setVisibility(View.VISIBLE);
                        handler.postDelayed(()->{if(controller==null)return;if(e.errorCode==PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW)controller.seekToDefaultPosition();controller.prepare();controller.play();},delay);
                        return;
                    }
                    error.setText("This source could not play ("+e.getErrorCodeName()+"). Try another source or retry.");((View)error.getTag()).setVisibility(View.VISIBLE);
                }
                @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_READY){retries=0;((View)error.getTag()).setVisibility(View.GONE);}}
                @Override public void onVideoSizeChanged(VideoSize size){updateQualityLabel();}
                @Override public void onTracksChanged(Tracks tracks){updateQualityLabel();}
                @Override public void onPlaybackParametersChanged(PlaybackParameters params){speed.setText("Speed · "+speedLabel(params.speed));}
            });
            updateQualityLabel();speed.setText("Speed · "+speedLabel(controller.getPlaybackParameters().speed));
        }catch(Exception e){error.setText("Could not connect to playback service.");((View)error.getTag()).setVisibility(View.VISIBLE);}},command->handler.post(command));
    }
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
        active=false;handler.removeCallbacksAndMessages(null);
        if(!getSharedPreferences("astra",0).getBoolean("background",false)){if(controller!=null)controller.pause();else startService(new Intent(this,PlaybackService.class).setAction("pause"));}
        view.setPlayer(null);if(future!=null)MediaController.releaseFuture(future);controller=null;super.onStop();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){return view.dispatchKeyEvent(event)||super.dispatchKeyEvent(event);}
    @Override public void onBackPressed(){if(view.isControllerFullyVisible())view.hideController();else finish();}
}
