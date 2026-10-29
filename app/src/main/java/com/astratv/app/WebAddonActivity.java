package com.astratv.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.ByteArrayInputStream;
import java.util.*;

/** Isolated, popup-denying browser. No bridge, stream extraction, or TLS overrides. */
public class WebAddonActivity extends Activity {
    WebView web; FrameLayout frame; View fullScreen; WebChromeClient.CustomViewCallback fullCallback;
    TextView status; String initial; LinearLayout toolbar;
    static final Set<String> BLOCKED=new HashSet<>(Arrays.asList("doubleclick.net","googlesyndication.com","popads.net","popcash.net","propellerads.com","adsterra.com"));
    static boolean blocked(String host){if(host==null)return false;for(String d:BLOCKED)if(host.equals(d)||host.endsWith("."+d))return true;return false;}
    @Override public void onCreate(Bundle b){super.onCreate(b);initial=getIntent().getStringExtra("url");if(!Library.https(initial)){finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff080d18);toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        Button back=new Button(this);back.setText("‹ Back");back.setOnClickListener(v->onBackPressed());toolbar.addView(back);
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setText(getIntent().getStringExtra("title"));toolbar.addView(status,new LinearLayout.LayoutParams(0,-2,1));
        Button reload=new Button(this);reload.setText("Reload");reload.setOnClickListener(v->web.reload());toolbar.addView(reload);
        Button external=new Button(this);external.setText("Open externally");external.setOnClickListener(v->{String url=web.getUrl();if(!Library.https(url))url=initial;try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException e){status.setText("No compatible browser is installed");}});toolbar.addView(external);root.addView(toolbar);
        frame=new FrameLayout(this);web=new WebView(this);frame.addView(web);root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setSupportMultipleWindows(true);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setMediaPlaybackRequiresUserGesture(true);s.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return !Library.https(r.getUrl().toString())||blocked(r.getUrl().getHost());}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){if(blocked(r.getUrl().getHost()))return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));return null;}
            @Override public void onPageFinished(WebView v,String url){status.setText(getIntent().getStringExtra("title")+" · "+Uri.parse(url).getHost());}
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())status.setText("Page unavailable · Reload or open externally");}
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse e){if(r.isForMainFrame())status.setText("Provider returned HTTP "+e.getStatusCode());}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onCreateWindow(WebView v,boolean dialog,boolean gesture,android.os.Message message){runOnUiThread(()->Toast.makeText(WebAddonActivity.this,"Pop-up blocked. If playback needs this window, use Open externally.",Toast.LENGTH_SHORT).show());return false;}
            @Override public void onShowCustomView(View view,CustomViewCallback callback){if(fullScreen!=null){callback.onCustomViewHidden();return;}fullScreen=view;fullCallback=callback;web.setVisibility(View.GONE);toolbar.setVisibility(View.GONE);frame.addView(view,new FrameLayout.LayoutParams(-1,-1));}
            @Override public void onHideCustomView(){hideFullscreen();}
        });web.loadUrl(initial);web.requestFocus();
    }
    void hideFullscreen(){if(fullScreen==null)return;frame.removeView(fullScreen);fullScreen=null;web.setVisibility(View.VISIBLE);toolbar.setVisibility(View.VISIBLE);WebChromeClient.CustomViewCallback callback=fullCallback;fullCallback=null;if(callback!=null)callback.onCustomViewHidden();}
    @Override public void onBackPressed(){if(fullScreen!=null)hideFullscreen();else if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onPause(){super.onPause();web.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){if(web!=null){web.stopLoading();frame.removeView(web);web.destroy();}super.onDestroy();}
}
