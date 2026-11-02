package com.astratv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.*;
import android.net.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.net.Inet6Address;
import java.util.*;
import java.util.concurrent.*;

/**
 * Security Research Dashboard for authorized testers. Separate, non-exported activity; never linked
 * from movie screens. All retests are in-process checks against this app and local fixtures.
 */
public class SecurityDashboardActivity extends Activity {
    private TvUi ui;
    private LinearLayout body;
    private SharedPreferences gate;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);ui=new TvUi(this);gate=getSharedPreferences("astra_ui_v1",0);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(TvUi.BG);body=ui.column();body.setPadding(ui.dp(56),ui.dp(36),ui.dp(56),ui.dp(48));scroll.addView(body);setContentView(scroll);
        if(!TesterGate.configured(BuildConfig.TESTER_PIN_HASH)){body.addView(ui.bold("Security research is not enabled in this build",24));ui.gap(body,10);body.addView(ui.text("Set security.tester.pin in local.properties and rebuild. Only a PBKDF2 hash of the PIN is compiled in.",14,TvUi.MUTED));return;}
        askPin(null);
    }
    private void askPin(String message){
        int failures=gate.getInt("tester.failures",0);
        long locked=TesterGate.lockedFor(failures,gate.getLong("tester.failed_at",0),System.currentTimeMillis());
        if(locked>0){notice("Locked","Too many wrong PINs. Try again in "+(locked/1000+1)+" seconds.",true);return;}
        EditText pin=ui.input("Tester PIN","");pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        AlertDialog.Builder prompt=new AlertDialog.Builder(this).setTitle("Authorized testers only").setView(pin).setCancelable(false);
        if(message!=null)prompt.setMessage(message);
        prompt
            .setPositiveButton("Unlock",(d,w)->{String entered=pin.getText().toString();worker.execute(()->{boolean ok=TesterGate.verify(entered,BuildConfig.TESTER_PIN_SALT,BuildConfig.TESTER_PIN_HASH);runOnUiThread(()->{if(isDestroyed())return;
                if(ok){gate.edit().putInt("tester.failures",0).apply();render();}
                else{int failed=gate.getInt("tester.failures",0)+1;gate.edit().putInt("tester.failures",failed).putLong("tester.failed_at",System.currentTimeMillis()).apply();
                    int left=TesterGate.MAX_FAILURES-failed;askPin(left>0?"Wrong PIN. "+left+(left==1?" attempt":" attempts")+" left before a "+(TesterGate.LOCKOUT_MS/60000)+"-minute lockout.":null);}});});})
            .setNegativeButton("Leave",(d,w)->finish()).show();
    }
    private void render(){
        body.removeAllViews();
        body.addView(ui.bold("Security Research Dashboard",30));ui.gap(body,8);
        body.addView(ui.text("Retests run inside this app against its own code, settings and offline fixtures. No external system is probed.",14,TvUi.MUTED));ui.gap(body,18);
        TextView summary=ui.bold("Running retests…",18);body.addView(summary);ui.gap(body,16);
        LinearLayout list=ui.column();body.addView(list);
        LinearLayout coverage=ui.column();body.addView(coverage);
        LinearLayout providers=ui.column();body.addView(providers);
        Env env=new Env();
        worker.execute(()->{
            List<SecurityFindings.Finding> findings=SecurityFindings.all();Map<String,SecurityFindings.Result> results=new LinkedHashMap<>();
            for(SecurityFindings.Finding f:findings){SecurityFindings.Result r;try{r=f.retest.run(env);}catch(Exception e){r=new SecurityFindings.Result(SecurityFindings.Outcome.NOT_TESTABLE,"Retest error: "+e.getClass().getSimpleName());}results.put(f.id,r);}
            Map<String,ProviderRiskScanner.Report> risk=providerRisk();
            runOnUiThread(()->{if(isDestroyed())return;
                int pass=0,fail=0,na=0;for(SecurityFindings.Result r:results.values()){if(r.outcome==SecurityFindings.Outcome.PASS)pass++;else if(r.outcome==SecurityFindings.Outcome.FAIL)fail++;else na++;}
                summary.setText(findings.size()+" findings · retest "+pass+" pass, "+fail+" fail, "+na+" not testable here · retested "+new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date()));
                for(SecurityFindings.Finding f:findings)list.addView(card(f,results.get(f.id)));
                coverage.addView(ui.bold("Test-area coverage",20));ui.gap(coverage,10);
                for(int i=0;i<SecurityFindings.AREAS.length;i++){List<String> ids=new ArrayList<>();for(SecurityFindings.Finding f:findings)for(int a:f.areas)if(a==i+1)ids.add(f.id);
                    coverage.addView(ui.text((i+1)+". "+SecurityFindings.AREAS[i]+" — "+(ids.isEmpty()?"no test":String.join(", ",ids)),13,ids.isEmpty()?0xffe7b75f:TvUi.MUTED));ui.gap(coverage,4);}
                ui.gap(coverage,20);
                providers.addView(ui.bold("Provider risk (cached catalogs, URL checks only)",20));ui.gap(providers,10);
                if(risk.isEmpty())providers.addView(ui.text("No cached catalogs yet.",13,TvUi.MUTED));
                for(Map.Entry<String,ProviderRiskScanner.Report> e:risk.entrySet()){ProviderRiskScanner.Report r=e.getValue();Set<String> rules=new LinkedHashSet<>();for(ProviderRiskScanner.Finding x:r.findings)rules.add(x.rule);
                    providers.addView(ui.text(Library.providerName(e.getKey())+" · "+r.level()+" (score "+r.score+")"+(rules.isEmpty()?"":" · "+String.join(", ",rules)),13,r.score>=60?0xffff8a7a:r.score>=25?0xffe7b75f:TvUi.MUTED));ui.gap(providers,4);}
                ui.gap(providers,20);providers.addView(ui.action("Run retests again",true,this::render));ui.gap(providers,10);providers.addView(ui.action("Close dashboard",false,this::finish));
            });
        });
    }
    private View card(SecurityFindings.Finding f,SecurityFindings.Result r){
        LinearLayout c=ui.column();c.setPadding(ui.dp(18),ui.dp(14),ui.dp(18),ui.dp(14));c.setBackground(ui.shape(0xff15171c,0,14));c.setFocusable(true);
        int sev=f.severity==SecurityFindings.Severity.CRITICAL?0xffd6453d:f.severity==SecurityFindings.Severity.HIGH?0xffe0703a:f.severity==SecurityFindings.Severity.MEDIUM?0xffd3a33a:f.severity==SecurityFindings.Severity.LOW?0xff5f8fd0:0xff6b7280;
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.addView(ui.badge(f.severity.name(),sev));
        TextView title=ui.bold("  "+f.id+" · "+f.title,16);head.addView(title);c.addView(head);ui.gap(c,8);
        c.addView(ui.text("Component: "+f.component+" · Status: "+f.status+" · Detected: "+f.detected,12,TvUi.MUTED));ui.gap(c,6);
        c.addView(ui.text("Reproduce: "+f.reproduction,13,TvUi.WHITE));ui.gap(c,4);
        c.addView(ui.text("Impact: "+f.impact,13,TvUi.WHITE));ui.gap(c,4);
        c.addView(ui.text("Remediation: "+f.remediation,13,TvUi.WHITE));ui.gap(c,6);
        int color=r.outcome==SecurityFindings.Outcome.PASS?0xff94c8a3:r.outcome==SecurityFindings.Outcome.FAIL?0xffff8a7a:0xffb5b9c2;
        c.addView(ui.text("Retest: "+r.outcome.name().replace('_',' ')+" — "+r.detail,13,color));
        c.setOnFocusChangeListener((v,focus)->v.setBackground(ui.shape(focus?0xff22252d:0xff15171c,focus?TvUi.ACCENT:0,14)));
        LinearLayout wrap=ui.column();wrap.addView(c);ui.gap(wrap,12);return wrap;
    }
    private Map<String,ProviderRiskScanner.Report> providerRisk(){
        ProviderRepository repo=new ProviderRepository(this);Map<String,ProviderRiskScanner.Report> out=new LinkedHashMap<>();
        try{for(Library.Addon a:Library.addons()){List<Library.Title> titles=repo.cached(a.id);if(titles.isEmpty())continue;StringBuilder urls=new StringBuilder();
            for(Library.Title t:titles){urls.append(t.url).append('\n');for(int i=0;i<t.videos.length();i++){JSONObject v=t.videos.optJSONObject(i);if(v!=null)urls.append(v.optString("url")).append('\n');}}
            out.put(a.id,ProviderRiskScanner.playlist(urls.toString()));}}
        finally{repo.io.shutdownNow();}
        return out;
    }
    private void notice(String title,String message,boolean close){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setCancelable(false).setPositiveButton("OK",(d,w)->{if(close)finish();}).show();}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}

    private final class Env implements SecurityFindings.Env{
        private final ApplicationInfo info=getApplicationInfo();
        @Override public boolean cleartextAllowed(){return (info.flags&ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC)!=0;}
        @Override public boolean backupAllowed(){return (info.flags&ApplicationInfo.FLAG_ALLOW_BACKUP)!=0;}
        @Override public boolean debuggable(){return (info.flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0;}
        @Override public List<String> exportedComponents(){
            List<String> out=new ArrayList<>();
            try{PackageInfo p=getPackageManager().getPackageInfo(getPackageName(),PackageManager.GET_ACTIVITIES|PackageManager.GET_SERVICES|PackageManager.GET_RECEIVERS|PackageManager.GET_PROVIDERS);
                for(ComponentInfo[] group:new ComponentInfo[][]{p.activities,p.services,p.receivers,p.providers})if(group!=null)for(ComponentInfo ci:group)if(ci.exported)out.add(ci.name);}
            catch(PackageManager.NameNotFoundException ignored){/* own package always exists */}
            return out;
        }
        @Override public boolean dashboardExported(){return exportedComponents().contains(SecurityDashboardActivity.class.getName());}
        @Override public boolean buildConfigKeysEmpty(){return BuildConfig.TMDB_API_KEY.isEmpty()&&BuildConfig.OMDB_API_KEY.isEmpty()&&BuildConfig.TRAKT_CLIENT_ID.isEmpty()&&BuildConfig.YOUTUBE_API_KEY.isEmpty();}
        @Override public VpnConfigAnalysis vpnConfig(){return VpnController.get(SecurityDashboardActivity.this).snapshot().analysis;}
        @Override public boolean vpnLeakProtection(){return VpnController.get(SecurityDashboardActivity.this).store().leakProtection();}
        @Override public VpnHealth.State vpnState(){return VpnController.get(SecurityDashboardActivity.this).snapshot().state;}
        @Override public List<String> vpnDnsReported(){return NetworkStatus.dns(SecurityDashboardActivity.this);}
        @Override public boolean vpnRoutesIpv6(){
            ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);if(cm==null)return false;
            for(Network n:cm.getAllNetworks()){NetworkCapabilities caps=cm.getNetworkCapabilities(n);if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))continue;
                LinkProperties lp=cm.getLinkProperties(n);if(lp==null)continue;for(RouteInfo r:lp.getRoutes())if(r.getDestination().getAddress() instanceof Inet6Address&&r.getDestination().getPrefixLength()==0)return true;}
            return false;
        }
        private Boolean fileAccess,contentAccess,cookies;
        private void inspectWebView(){
            if(fileAccess!=null)return;CountDownLatch done=new CountDownLatch(1);
            runOnUiThread(()->{try{YoutubeTrailerView v=new YoutubeTrailerView(SecurityDashboardActivity.this);fileAccess=v.getSettings().getAllowFileAccess();contentAccess=v.getSettings().getAllowContentAccess();cookies=android.webkit.CookieManager.getInstance().acceptCookie();v.destroy();}catch(Exception e){fileAccess=true;contentAccess=true;cookies=true;}finally{done.countDown();}});
            try{done.await(5,TimeUnit.SECONDS);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}
            if(fileAccess==null){fileAccess=true;contentAccess=true;cookies=true;}
        }
        @Override public boolean webViewFileAccess(){inspectWebView();return fileAccess;}
        @Override public boolean webViewContentAccess(){inspectWebView();return contentAccess;}
        @Override public boolean webCookiesAccepted(){inspectWebView();return cookies;}
        @Override public String researchExport(){try{return new ResearchLog(getSharedPreferences("astra",0)).export();}catch(Exception e){return "";}}
        @Override public String sqlInjectionProbe(){
            MetadataStore store=new MetadataStore(SecurityDashboardActivity.this);String key="security-probe:x' OR '1'='1";
            try{store.put(key,new JSONObject().put("probe",true),false);
                if(store.get("security-probe:nothing' OR '1'='1",true)!=null)return "Injected selector matched a row";
                JSONObject back=store.get(key,true);return back!=null&&back.optBoolean("probe")?"":"Hostile key did not round-trip";}
            catch(Exception e){return "Probe error: "+e.getClass().getSimpleName();}
            finally{store.delete(key);}
        }
        @Override public boolean testerGateConfigured(){return TesterGate.configured(BuildConfig.TESTER_PIN_HASH);}
    }
}
