package com.astratv.app;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.session.*;
import androidx.media3.session.MediaController;
import androidx.media3.ui.PlayerView;
import com.google.common.util.concurrent.ListenableFuture;

/** Full-screen native player; Media3 supplies focusable TV controls, tracks and subtitles. */
@androidx.media3.common.util.UnstableApi
public class PlayerActivity extends Activity {
    private PlayerView view;
    private ListenableFuture<MediaController> future;
    private MediaController controller;
    private TextView error;
    private boolean starting=true;
    private boolean active=false;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff05070b);
        view=new PlayerView(this);view.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);view.setControllerShowTimeoutMs(3500);view.setShowSubtitleButton(true);view.setShowNextButton(false);view.setShowPreviousButton(false);view.setShowFastForwardButton(true);view.setShowRewindButton(true);root.addView(view,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout message=new LinearLayout(this);message.setOrientation(LinearLayout.VERTICAL);message.setPadding(40,20,40,20);message.setBackgroundColor(0xe6080b12);
        error=new TextView(this);error.setTextColor(0xffedf1f6);error.setTextSize(17);message.addView(error);message.setVisibility(View.GONE);
        Button retry=new Button(this);retry.setText("Retry playback");retry.setOnClickListener(v->{if(controller!=null){controller.prepare();controller.play();message.setVisibility(View.GONE);}});message.addView(retry);
        Button back=new Button(this);back.setText("Back to title");back.setOnClickListener(v->finish());message.addView(back);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);root.addView(message,p);setContentView(root);
        view.setErrorMessageProvider(e->android.util.Pair.create(e.errorCode,"Playback failed. Check this source or choose another one."));
        error.setTag(message);
    }
    @Override protected void onStart(){
        super.onStart();active=true;Intent service=new Intent(this,PlaybackService.class);
        if(starting&&getIntent().hasExtra("url"))service.putExtras(getIntent());starting=false;
        startService(service);
        future=new MediaController.Builder(this,new SessionToken(this,new ComponentName(this,PlaybackService.class))).buildAsync();
        future.addListener(()->{try{
            if(!active||isFinishing()||isDestroyed())return;controller=future.get();view.setPlayer(controller);view.requestFocus();
            controller.addListener(new Player.Listener(){@Override public void onPlayerError(PlaybackException e){error.setText("This source could not play ("+e.getErrorCodeName()+"). Try another source or retry.");((View)error.getTag()).setVisibility(View.VISIBLE);}});
        }catch(Exception e){error.setText("Could not connect to playback service.");((View)error.getTag()).setVisibility(View.VISIBLE);}},command->new android.os.Handler(android.os.Looper.getMainLooper()).post(command));
    }
    @Override protected void onStop(){
        active=false;
        if(!getSharedPreferences("astra",0).getBoolean("background",false)){if(controller!=null)controller.pause();else startService(new Intent(this,PlaybackService.class).setAction("pause"));}
        view.setPlayer(null);if(future!=null)MediaController.releaseFuture(future);controller=null;super.onStop();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){return view.dispatchKeyEvent(event)||super.dispatchKeyEvent(event);}
    @Override public void onBackPressed(){if(view.isControllerFullyVisible())view.hideController();else finish();}
}
