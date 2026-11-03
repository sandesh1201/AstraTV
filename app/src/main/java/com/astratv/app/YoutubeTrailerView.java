package com.astratv.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.*;

/**
 * Locked-down WebView hosting the official YouTube IFrame Player API (youtube-nocookie host).
 * The page is loaded under an HTTPS base URL named after the app, so YouTube receives the
 * client identity (Referer / origin) its embed requirements ask for. Player state and error
 * events come back through a small JavaScript bridge, so failures fall back to the backdrop.
 * Only Google/YouTube player hosts are reachable; top-level navigation and new windows are blocked.
 * Cookies are off. This is the only WebView in the app, and only one instance exists at a time.
 */
final class YoutubeTrailerView extends WebView {
    interface Listener{void onPlaying();void onError(int code);}
    static final String BRIDGE="SaandioPlayer";
    /** Not a YouTube code: the page or the IFrame API could not load. */
    static final int ERROR_PAGE=-1;
    private final String base;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile Listener listener;

    YoutubeTrailerView(Context c){
        super(c);
        base="https://"+c.getPackageName()+"/";
        WebSettings s=getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSaveFormData(false);
        s.setSupportMultipleWindows(false);
        s.setSupportZoom(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager cookies=CookieManager.getInstance();
        cookies.setAcceptCookie(false);cookies.setAcceptThirdPartyCookies(this,false);
        setBackgroundColor(0xff000000);
        setFocusable(false);setFocusableInTouchMode(false);setHorizontalScrollBarEnabled(false);setVerticalScrollBarEnabled(false);
        addJavascriptInterface(new Bridge(),BRIDGE);
        setWebChromeClient(new WebChromeClient(){@Override public boolean onCreateWindow(WebView v,boolean d,boolean u,android.os.Message r){return false;}});
        setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){return req.isForMainFrame()||!TrailerResolver.youtubePlayerResource(req.getUrl().toString());}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest req){
                String url=req.getUrl()==null?"":req.getUrl().toString();
                if(TrailerResolver.youtubePlayerResource(url))return super.shouldInterceptRequest(v,req);
                return new WebResourceResponse("text/plain","utf-8",new java.io.ByteArrayInputStream(new byte[0]));
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest req,WebResourceError err){if(req.isForMainFrame())report(ERROR_PAGE);}
            @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail d){report(ERROR_PAGE);return true;}
        });
    }
    void setListener(Listener l){listener=l;}
    /** Starts one muted (or audible) looping preview. Rejects anything that is not a YouTube video id. */
    boolean play(String videoId,boolean mute){
        if(!validId(videoId))return false;
        loadDataWithBaseURL(base,html(videoId,mute,base.substring(0,base.length()-1)),"text/html","utf-8",null);
        return true;
    }
    /** Stops decoding and network activity without destroying the view, so it can be reused. */
    void stopPreview(){listener=null;loadDataWithBaseURL(base,"<html><body style=\"margin:0;background:#000\"></body></html>","text/html","utf-8",null);}

    static boolean validId(String id){return id!=null&&id.matches("[A-Za-z0-9_-]{11}");}
    static String html(String videoId,boolean mute,String origin){
        return "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            +"<meta name=\"referrer\" content=\"strict-origin-when-cross-origin\">"
            +"<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#player{position:absolute;top:0;left:0;width:100%;height:100%}</style></head>"
            +"<body><div id=\"player\"></div><script>"
            +"function send(f,v){try{"+BRIDGE+"[f](v);}catch(e){}}"
            +"var tag=document.createElement('script');tag.src='https://www.youtube.com/iframe_api';tag.onerror=function(){send('error',"+ERROR_PAGE+");};document.head.appendChild(tag);"
            +"function onYouTubeIframeAPIReady(){new YT.Player('player',{width:'100%',height:'100%',videoId:'"+videoId+"',host:'https://www.youtube-nocookie.com',"
            +"playerVars:{autoplay:1,mute:"+(mute?1:0)+",controls:0,playsinline:1,rel:0,fs:0,iv_load_policy:3,disablekb:1,loop:1,playlist:'"+videoId+"',origin:'"+origin+"',widget_referrer:'"+origin+"'},"
            +"events:{onReady:function(e){"+(mute?"e.target.mute();":"")+"e.target.playVideo();},onStateChange:function(e){send('state',e.data);},onError:function(e){send('error',e.data);}}});}"
            +"</script></body></html>";
    }
    private void report(int code){Listener l=listener;if(l!=null)main.post(()->{if(listener==l)l.onError(code);});}

    private final class Bridge{
        @JavascriptInterface public void state(int state){Listener l=listener;if(l!=null&&state==1)main.post(()->{if(listener==l)l.onPlaying();});}
        @JavascriptInterface public void error(int code){report(code);}
    }
}
