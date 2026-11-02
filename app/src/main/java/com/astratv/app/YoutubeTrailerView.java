package com.astratv.app;

import android.content.Context;
import android.webkit.*;

/**
 * Locked-down WebView used only for the official YouTube IFrame embed.
 * Top-level navigation is youtube-nocookie.com only. New windows are blocked.
 * Cookies are not persisted. This is the sole WebView in the app.
 */
final class YoutubeTrailerView extends WebView {
    YoutubeTrailerView(Context c){
        super(c);
        WebSettings s=getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(false);
        s.setDatabaseEnabled(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSaveFormData(false);
        s.setSupportMultipleWindows(false);
        s.setSupportZoom(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager cookies=CookieManager.getInstance();
        cookies.setAcceptCookie(false);cookies.setAcceptThirdPartyCookies(this,false);cookies.removeAllCookies(null);cookies.flush();
        setFocusable(false);setFocusableInTouchMode(false);setHorizontalScrollBarEnabled(false);setVerticalScrollBarEnabled(false);
        setWebChromeClient(new WebChromeClient(){@Override public boolean onCreateWindow(WebView v,boolean d,boolean u,android.os.Message r){return false;}});
        setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){return !TrailerResolver.youtubeEmbed(req.getUrl().toString());}
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap f){if(!TrailerResolver.youtubeEmbed(url))v.stopLoading();}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest req){
                String url=req.getUrl()==null?"":req.getUrl().toString();
                if(TrailerResolver.youtubePlayerResource(url))return super.shouldInterceptRequest(v,req);
                return new WebResourceResponse("text/plain","utf-8",new java.io.ByteArrayInputStream(new byte[0]));
            }
        });
    }
    void play(String key,boolean mute){
        if(key==null||!key.matches("[A-Za-z0-9_-]{6,20}"))return;
        String url="https://www.youtube-nocookie.com/embed/"+key+"?autoplay=1&mute="+(mute?1:0)+"&controls=0&loop=1&playlist="+key+"&playsinline=1&rel=0&modestbranding=1&fs=0";
        loadUrl(url);
    }
}
