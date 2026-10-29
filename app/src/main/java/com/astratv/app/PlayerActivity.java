package com.astratv.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;

public class PlayerActivity extends Activity implements SurfaceHolder.Callback {
    PlaybackService service;SurfaceView surface;TextView status;Button toggle;SeekBar seek;boolean bound=false,visible=false;
    Handler handler=new Handler(Looper.getMainLooper());
    final Runnable tick=new Runnable(){public void run(){if(service!=null){status.setText(!service.error.isEmpty()?service.error:service.loading?"Connecting…":time(service.position())+" / "+time(service.duration()));toggle.setText(service.playing()?"Pause":"Play");seek.setMax(Math.max(1,service.duration()));seek.setProgress(service.position());fitVideo();}handler.postDelayed(this,700);}};
    final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){service=((PlaybackService.LocalBinder)b).get();service.play(getIntent().getStringExtra("id"),getIntent().getStringExtra("title"),getIntent().getStringExtra("url"));if(surface.getHolder().getSurface().isValid())service.surface(surface.getHolder().getSurface());}public void onServiceDisconnected(ComponentName n){service=null;}};
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Color.BLACK);surface=new SurfaceView(this);surface.getHolder().addCallback(this);frame.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);controls.setPadding(24,10,24,10);controls.setBackgroundColor(0xd9080d18);TextView title=new TextView(this);title.setText(getIntent().getStringExtra("title"));title.setTextSize(20);title.setTextColor(Color.WHITE);controls.addView(title);status=new TextView(this);status.setTextColor(Color.LTGRAY);controls.addView(status);seek=new SeekBar(this);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){if(user&&service!=null)service.seek(p);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});controls.addView(seek);
        LinearLayout row=new LinearLayout(this);add(row,"‹ Back",this::finish);add(row,"−30 seconds",()->{if(service!=null)service.seek(service.position()-30000);});toggle=add(row,"Play",()->{if(service!=null){if(service.playing())service.pause();else service.resume();}});add(row,"+30 seconds",()->{if(service!=null)service.seek(service.position()+30000);});add(row,"Restart",()->{if(service!=null){if(!service.prepared){service.error="retry";service.play(getIntent().getStringExtra("id"),getIntent().getStringExtra("title"),getIntent().getStringExtra("url"));service.surface(surface.getHolder().getSurface());}else service.seek(0);}});controls.addView(row);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);frame.addView(controls,p);setContentView(frame);toggle.requestFocus();
    }
    Button add(LinearLayout l,String name,Runnable r){Button b=new Button(this);b.setText(name);b.setAllCaps(false);b.setOnClickListener(v->r.run());l.addView(b);return b;}
    void fitVideo(){if(service.player==null||!service.prepared)return;int vw=service.player.getVideoWidth(),vh=service.player.getVideoHeight();View parent=(View)surface.getParent();if(vw<=0||vh<=0||parent.getWidth()==0)return;float scale=Math.min((float)parent.getWidth()/vw,(float)parent.getHeight()/vh);int width=(int)(vw*scale),height=(int)(vh*scale);if(surface.getWidth()!=width||surface.getHeight()!=height)surface.setLayoutParams(new FrameLayout.LayoutParams(width,height,Gravity.CENTER));}
    @Override protected void onStart(){super.onStart();visible=true;startForegroundService(new Intent(this,PlaybackService.class));bound=bindService(new Intent(this,PlaybackService.class),connection,BIND_AUTO_CREATE);handler.post(tick);}
    @Override protected void onStop(){visible=false;handler.removeCallbacks(tick);if(service!=null){service.surface(null);if(!getSharedPreferences("astra",0).getBoolean("background",false))service.pause();}if(bound){unbindService(connection);bound=false;}service=null;super.onStop();}
    @Override public void surfaceCreated(SurfaceHolder h){if(service!=null)service.surface(h.getSurface());}
    @Override public void surfaceChanged(SurfaceHolder h,int f,int w,int ht){if(service!=null)service.surface(h.getSurface());}
    @Override public void surfaceDestroyed(SurfaceHolder h){if(service!=null)service.surface(null);}
    @Override public boolean onKeyDown(int key,android.view.KeyEvent e){if(service!=null){if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){toggle.performClick();return true;}if(key==KeyEvent.KEYCODE_MEDIA_PLAY){service.resume();return true;}if(key==KeyEvent.KEYCODE_MEDIA_PAUSE){service.pause();return true;}if(key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD){service.seek(service.position()+30000);return true;}if(key==KeyEvent.KEYCODE_MEDIA_REWIND){service.seek(service.position()-30000);return true;}}return super.onKeyDown(key,e);}
    static String time(int ms){int seconds=ms/1000;return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
}
