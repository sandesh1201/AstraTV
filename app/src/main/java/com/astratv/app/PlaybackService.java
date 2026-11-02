package com.astratv.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.*;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.*;
import org.json.*;
import java.util.*;

/** One service-owned Media3 player, shared by TV controls and background playback. */
@androidx.media3.common.util.UnstableApi
public class PlaybackService extends MediaSessionService {
    /** Session extra: height of the rendition currently fed to the video decoder. */
    static final String EXTRA_VIDEO_HEIGHT="astra.videoHeight";
    private ExoPlayer player;
    private MediaSession session;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable checkpoint=new Runnable(){@Override public void run(){save();handler.postDelayed(this,4000);}};

    @Override public void onCreate(){
        super.onCreate();
        player=new ExoPlayer.Builder(this).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
        player.setHandleAudioBecomingNoisy(true);
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,PlayerActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        session=new MediaSession.Builder(this,player).setSessionActivity(open).build();
        player.addListener(new Player.Listener(){
            @Override public void onIsPlayingChanged(boolean playing){save();}
            @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_ENDED&&player.getCurrentMediaItem()!=null)getSharedPreferences("astra",0).edit().remove("position."+player.getCurrentMediaItem().mediaId).apply();}
        });
        player.addAnalyticsListener(new androidx.media3.exoplayer.analytics.AnalyticsListener(){
            @Override public void onVideoInputFormatChanged(EventTime time,Format format,androidx.media3.exoplayer.DecoderReuseEvaluation reuse){
                Bundle extras=new Bundle();extras.putInt(EXTRA_VIDEO_HEIGHT,Math.max(0,format.height));session.setSessionExtras(extras);
            }
        });
        handler.post(checkpoint);
    }
    @Override public MediaSession onGetSession(MediaSession.ControllerInfo info){return session;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&"pause".equals(intent.getAction())){player.pause();save();}
        if(intent!=null&&intent.hasExtra("url")){
            String id=intent.getStringExtra("id"),url=intent.getStringExtra("url");
            if(Library.https(url)&&(player.getCurrentMediaItem()==null||!player.getCurrentMediaItem().mediaId.equals(id)||intent.getBooleanExtra("retry",false))){
                save();Map<String,String> headers=new HashMap<>();
                try{headers.putAll(PlaylistParser.safeHeaders(new JSONObject(intent.getStringExtra("headers"))));}catch(Exception ignored){}
                DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setConnectTimeoutMs(10000).setReadTimeoutMs(12000).setAllowCrossProtocolRedirects(false).setDefaultRequestProperties(headers).setUserAgent("AstraTV/0.3");
                MediaItem.Builder item=new MediaItem.Builder().setMediaId(id).setUri(url).setMediaMetadata(new MediaMetadata.Builder().setTitle(intent.getStringExtra("title")).build());
                String mime=intent.getStringExtra("mimeType");if(mime!=null&&!mime.isEmpty())item.setMimeType(mime);
                List<MediaItem.SubtitleConfiguration> subtitles=new ArrayList<>();
                try{JSONArray a=new JSONArray(intent.getStringExtra("subtitles"));for(int i=0;i<a.length();i++){JSONObject s=a.getJSONObject(i);String uri=s.optString("url");if(Library.https(uri))subtitles.add(new MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(uri)).setMimeType(uri.toLowerCase(Locale.ROOT).contains(".srt")?MimeTypes.APPLICATION_SUBRIP:MimeTypes.TEXT_VTT).setLabel(s.optString("lang","Subtitles")).build());}}catch(Exception ignored){}
                item.setSubtitleConfigurations(subtitles);
                long position=getSharedPreferences("astra",0).getInt("position."+id,0);
                session.setSessionExtras(new Bundle());
                session.setSessionExtras(new Bundle());
                player.setMediaSource(new DefaultMediaSourceFactory(http).setLoadErrorHandlingPolicy(new androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(2)).createMediaSource(item.build()),position);
                player.prepare();player.play();
            }else if(Library.https(url)){player.play();}
        }
        return super.onStartCommand(intent,flags,startId);
    }
    private void save(){
        if(player==null||player.getCurrentMediaItem()==null||player.getPlaybackState()==Player.STATE_IDLE||player.getPlaybackState()==Player.STATE_ENDED)return;
        getSharedPreferences("astra",0).edit().putInt("position."+player.getCurrentMediaItem().mediaId,(int)Math.max(0,player.getCurrentPosition())).apply();
    }
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);save();if(session!=null)session.release();if(player!=null)player.release();super.onDestroy();}
}
