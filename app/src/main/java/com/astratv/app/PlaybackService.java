package com.astratv.app;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.view.Surface;
import java.io.IOException;

public class PlaybackService extends Service {
    public class LocalBinder extends Binder {PlaybackService get(){return PlaybackService.this;}}
    final LocalBinder binder=new LocalBinder();
    MediaPlayer player; MediaSession session; String id="",title="",url="",error=""; boolean prepared=false,loading=false;
    AudioManager audio; AudioFocusRequest focus; boolean resumeAfterFocus=false;
    final Handler handler=new Handler(Looper.getMainLooper());
    final Runnable checkpoint=new Runnable(){public void run(){save();handler.postDelayed(this,5000);}};
    @Override public void onCreate(){super.onCreate();audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build()).setOnAudioFocusChangeListener(change->{if(change==AudioManager.AUDIOFOCUS_LOSS){resumeAfterFocus=false;pause();}else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){resumeAfterFocus=playing();pause();}else if(change==AudioManager.AUDIOFOCUS_GAIN&&resumeAfterFocus){resumeAfterFocus=false;resume();}else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK&&player!=null){player.setVolume(.2f,.2f);}if(change==AudioManager.AUDIOFOCUS_GAIN&&player!=null)player.setVolume(1,1);}).build();
        NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("playback","Playback",NotificationManager.IMPORTANCE_LOW));
        session=new MediaSession(this,"AstraTV");session.setCallback(new MediaSession.Callback(){@Override public void onPlay(){resume();}@Override public void onPause(){pause();}@Override public void onSeekTo(long p){seek((int)p);}@Override public void onStop(){stopSelf();}});session.setActive(true);handler.post(checkpoint);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(intent!=null&&"stop".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}if(intent!=null&&"toggle".equals(intent.getAction())){if(playing())pause();else resume();}startForeground(7,notification());return START_NOT_STICKY;}
    @Override public IBinder onBind(Intent intent){return binder;}
    void play(String nextId,String nextTitle,String nextUrl){if(nextId.equals(id)&&player!=null&&error.isEmpty())return;save();if(player!=null)player.release();id=nextId;title=nextTitle;url=nextUrl;prepared=false;loading=true;error="";
        player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
        player.setOnPreparedListener(mp->{prepared=true;loading=false;int pos=getSharedPreferences("astra",0).getInt("position."+id,0);if(pos>0&&pos<mp.getDuration()-3000)mp.seekTo(pos);resume();});
        player.setOnErrorListener((mp,what,extra)->{error="Playback unavailable ("+what+"/"+extra+"). Check connection and media format.";loading=false;prepared=false;update();return true;});
        player.setOnCompletionListener(mp->{getSharedPreferences("astra",0).edit().remove("position."+id).apply();prepared=false;update();});
        try{if(!Library.https(nextUrl))throw new IOException("Only HTTPS media is supported");player.setDataSource(nextUrl);player.prepareAsync();MediaPlayer pending=player;handler.postDelayed(()->{if(player==pending&&loading){loading=false;error="Connection timed out. Check your network, then choose Restart to retry.";pending.release();player=null;update();}},30000);}catch(Exception e){loading=false;error="Cannot load this media: "+e.getMessage();}update();
    }
    boolean playing(){return player!=null&&prepared&&player.isPlaying();}
    void pause(){if(playing())player.pause();save();update();}
    void resume(){if(prepared&&audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED){player.start();update();}}
    void seek(int p){if(prepared){player.seekTo(Math.max(0,Math.min(p,player.getDuration())));update();}}
    int position(){return prepared?player.getCurrentPosition():0;} int duration(){return prepared?player.getDuration():0;}
    void surface(Surface s){if(player!=null)player.setSurface(s);}
    void save(){if(prepared&&!id.isEmpty())getSharedPreferences("astra",0).edit().putInt("position."+id,position()).apply();}
    // MediaStyle notifications associated with a MediaSession are exempt from
    // POST_NOTIFICATIONS: developer.android.com/develop/ui/views/notifications/notification-permission
    @android.annotation.SuppressLint("NotificationPermission")
    void update(){int state=loading?PlaybackState.STATE_BUFFERING:!error.isEmpty()?PlaybackState.STATE_ERROR:playing()?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED;session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_SEEK_TO|PlaybackState.ACTION_STOP).setState(state,position(),playing()?1:0).build());session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title).putLong(MediaMetadata.METADATA_KEY_DURATION,duration()).build());getSystemService(NotificationManager.class).notify(7,notification());}
    Notification notification(){Intent open=new Intent(this,PlayerActivity.class).putExtra("id",id).putExtra("title",title).putExtra("url",url);PendingIntent content=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);PendingIntent toggle=PendingIntent.getService(this,2,new Intent(this,PlaybackService.class).setAction("toggle"),PendingIntent.FLAG_IMMUTABLE);PendingIntent stop=PendingIntent.getService(this,3,new Intent(this,PlaybackService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);return new Notification.Builder(this,"playback").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title.isEmpty()?"Astra TV":title).setContentText(error.isEmpty()?(loading?"Connecting…":playing()?"Playing":"Paused"):error).setContentIntent(content).addAction(new Notification.Action.Builder(null,playing()?"Pause":"Play",toggle).build()).addAction(new Notification.Action.Builder(null,"Stop",stop).build()).setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,1)).setOngoing(playing()).build();}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);save();if(player!=null){player.release();player=null;}session.release();audio.abandonAudioFocusRequest(focus);super.onDestroy();}
}
