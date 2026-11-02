package com.astratv.app;

import android.content.Context;
import android.content.Intent;
import android.net.*;
import android.os.*;
import com.wireguard.android.backend.*;
import com.wireguard.config.Config;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Runs the user's own WireGuard tunnel through Android's VpnService (official wireguard-android
 * library). No servers or credentials are bundled. State shown to the user comes from the backend
 * statistics and the OS network capabilities, never from the button the user pressed.
 */
public final class VpnController {
    public interface Listener{void onVpnChanged();}
    public static final class Snapshot{
        public VpnHealth.State state=VpnHealth.State.NOT_CONFIGURED;
        public long lastHandshakeMs,rxBytes,txBytes,rxRate,txRate,latencyMs=-1,connectMs=-1;
        public int reconnects;public String error="",label="",endpoint="";
        public Boolean alwaysOn,lockdown;
        public VpnConfigAnalysis analysis;
    }
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static VpnController instance;
    public static synchronized VpnController get(Context c){if(instance==null)instance=new VpnController(c.getApplicationContext());return instance;}

    private static final String TUNNEL_NAME="astra";
    private final Context context;
    private final VpnConfigStore store;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<Listener> listeners=new CopyOnWriteArrayList<>();
    private final Snapshot snapshot=new Snapshot();
    private GoBackend backend;
    private volatile boolean wanted,tunnelUp,polling;
    private long lastSampleAt;
    private boolean wasProtected;
    private ConnectivityManager.NetworkCallback underlying;
    private final Tunnel tunnel=new Tunnel(){
        @Override public String getName(){return TUNNEL_NAME;}
        @Override public void onStateChange(Tunnel.State s){
            boolean wasUp=tunnelUp;tunnelUp=s==Tunnel.State.UP;
            main.post(()->{if(wasUp&&!tunnelUp&&wanted){snapshot.error="Android stopped the tunnel.";scheduleReconnect();}refresh();});
        }
    };
    private final Runnable poll=new Runnable(){@Override public void run(){if(!polling)return;refresh();main.postDelayed(this,3000);}};

    private VpnController(Context c){context=c;store=new VpnConfigStore(c);GoBackend.setAlwaysOnCallback(()->{if(store.configured())connect();});updateStatic();}

    public VpnConfigStore store(){return store;}
    public void addListener(Listener l){listeners.add(l);}
    public void removeListener(Listener l){listeners.remove(l);}
    public synchronized Snapshot snapshot(){return snapshot;}
    public boolean claimsProtection(){return VpnHealth.claimsProtection(snapshot.state);}
    /** Non-null when Android must show its VPN consent dialog first. */
    public Intent consentIntent(){return VpnService.prepare(context);}

    public void importConfig(String text,String label)throws IOException{
        if(tunnelUp)throw new IOException("Disconnect the VPN before replacing its configuration.");
        store.write(text,label);updateStatic();notifyListeners();
    }
    public void removeConfig(){disconnect();worker.execute(()->{store.clear();snapshot.connectMs=-1;snapshot.rxBytes=0;snapshot.txBytes=0;snapshot.lastHandshakeMs=0;snapshot.reconnects=0;snapshot.error="";updateStatic();main.post(this::notifyListeners);});}

    public void connect(){
        if(!store.configured()){set(VpnHealth.State.NOT_CONFIGURED,"Import a WireGuard configuration first.");return;}
        if(consentIntent()!=null){set(VpnHealth.State.CONSENT_REQUIRED,"");return;}
        wanted=true;snapshot.reconnects=0;set(VpnHealth.State.CONNECTING,"");
        worker.execute(this::bringUp);
        startMonitoring();
    }
    public void disconnect(){
        wanted=false;
        worker.execute(()->{try{if(backend!=null)backend.setState(tunnel,Tunnel.State.DOWN,null);}catch(Exception ignored){/* already down */}tunnelUp=false;snapshot.latencyMs=-1;snapshot.rxRate=0;snapshot.txRate=0;main.post(this::refresh);});
        stopMonitoring();
    }
    public void measure(){worker.execute(()->{snapshot.latencyMs=probe();main.post(this::notifyListeners);});}

    private void bringUp(){
        long start=SystemClock.elapsedRealtime();
        try{
            String text=store.read();if(text.isEmpty())throw new IOException("The stored configuration could not be decrypted. Import it again.");
            if(store.leakProtection())text=VpnConfigAnalysis.withIpv6Block(text);
            Config config=Config.parse(new BufferedReader(new StringReader(text)));
            Tunnel.State result=backend().setState(tunnel,Tunnel.State.UP,config);
            tunnelUp=result==Tunnel.State.UP;snapshot.connectMs=SystemClock.elapsedRealtime()-start;snapshot.error="";
            try{snapshot.alwaysOn=backend().isAlwaysOn();snapshot.lockdown=backend().isLockdownEnabled();}catch(Exception e){snapshot.alwaysOn=null;snapshot.lockdown=null;}
            snapshot.latencyMs=-1;
        }catch(Exception e){
            tunnelUp=false;snapshot.error=describe(e);
            main.post(()->{set(VpnHealth.State.FAILED,snapshot.error);scheduleReconnect();});return;
        }
        main.post(this::refresh);
    }
    private void scheduleReconnect(){
        if(!wanted||!store.autoReconnect())return;
        if(snapshot.reconnects>=VpnHealth.MAX_RECONNECTS){wanted=false;set(VpnHealth.State.FAILED,"Gave up after "+VpnHealth.MAX_RECONNECTS+" attempts. "+snapshot.error);return;}
        long delay=VpnHealth.backoffMs(snapshot.reconnects++);set(VpnHealth.State.RECONNECTING,"Retrying in "+(delay/1000)+" s");
        main.postDelayed(()->{if(wanted)worker.execute(()->{try{if(backend!=null&&tunnelUp)backend.setState(tunnel,Tunnel.State.DOWN,null);}catch(Exception ignored){/* restart below */}bringUp();});},delay);
    }
    private void refresh(){
        worker.execute(()->{
            long now=System.currentTimeMillis(),handshake=0,rx=0,tx=0;
            if(backend!=null&&tunnelUp)try{Statistics stats=backend.getStatistics(tunnel);rx=stats.totalRx();tx=stats.totalTx();
                for(com.wireguard.crypto.Key k:stats.peers()){Statistics.PeerStats p=stats.peer(k);if(p!=null)handshake=Math.max(handshake,p.latestHandshakeEpochMillis());}}catch(Exception ignored){/* treated as no handshake */}
            boolean os=NetworkStatus.vpnActive(context);
            VpnConfigAnalysis a=snapshot.analysis;boolean full=a!=null&&a.fullV4&&(a.fullV6||store.leakProtection());
            VpnHealth.State next=VpnHealth.evaluate(store.configured(),wanted,tunnelUp,os,handshake,now,full);
            long elapsed=now-lastSampleAt;
            synchronized(this){
                if(lastSampleAt>0){snapshot.rxRate=VpnHealth.rate(snapshot.rxBytes,rx,elapsed);snapshot.txRate=VpnHealth.rate(snapshot.txBytes,tx,elapsed);}
                lastSampleAt=now;snapshot.rxBytes=rx;snapshot.txBytes=tx;snapshot.lastHandshakeMs=handshake;
                if(snapshot.state==VpnHealth.State.RECONNECTING&&next==VpnHealth.State.CONNECTING){/* keep the retry label */}
                else if(!(snapshot.state==VpnHealth.State.FAILED&&!tunnelUp))snapshot.state=next;
                if(next==VpnHealth.State.PROTECTED||next==VpnHealth.State.PARTIAL)snapshot.reconnects=0;
            }
            if(next==VpnHealth.State.STALE&&probe()<0)main.post(this::scheduleReconnect);
            if((next==VpnHealth.State.PROTECTED||next==VpnHealth.State.PARTIAL)&&snapshot.latencyMs<0)snapshot.latencyMs=probe();
            enforcePlaybackGate(next);
            main.post(this::notifyListeners);
        });
    }
    /** App-level kill switch: with "Require VPN" on, playback pauses as soon as protection is lost. */
    @androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
    private void enforcePlaybackGate(VpnHealth.State next){
        boolean protectedNow=VpnHealth.claimsProtection(next);
        if(wasProtected&&!protectedNow&&context.getSharedPreferences("astra_ui_v1",0).getBoolean("vpn.required",false)&&!NetworkStatus.vpnActive(context))
            try{context.startService(new Intent(context,PlaybackService.class).setAction("pause"));}catch(IllegalStateException ignored){/* app in background with no running player */}
        wasProtected=protectedNow;
    }
    /** TCP connect time to the tunnel's DNS server, forced through the VPN network; -1 when there is no VPN network or no answer. */
    private long probe(){
        Network vpn=NetworkStatus.vpnNetwork(context);if(vpn==null)return -1;
        VpnConfigAnalysis a=snapshot.analysis;String target=a!=null&&!a.dns.isEmpty()?a.dns.get(0):"1.1.1.1";int port=a!=null&&!a.dns.isEmpty()?53:443;
        try(Socket s=new Socket()){
            vpn.bindSocket(s);long start=SystemClock.elapsedRealtime();
            s.connect(new InetSocketAddress(InetAddress.getByName(target),port),3000);return SystemClock.elapsedRealtime()-start;
        }catch(Exception e){return -1;}
    }
    private void startMonitoring(){
        if(!polling){polling=true;lastSampleAt=0;main.post(poll);}
        if(underlying==null){
            ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);if(cm==null)return;
            underlying=new ConnectivityManager.NetworkCallback(){
                @Override public void onAvailable(Network n){main.postDelayed(VpnController.this::refresh,4000);}
                @Override public void onLost(Network n){main.post(VpnController.this::refresh);}
            };
            try{cm.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),underlying);}catch(Exception e){underlying=null;}
        }
    }
    private void stopMonitoring(){
        polling=false;main.removeCallbacks(poll);
        if(underlying!=null){ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);try{if(cm!=null)cm.unregisterNetworkCallback(underlying);}catch(Exception ignored){/* not registered */}underlying=null;}
    }
    private synchronized GoBackend backend(){if(backend==null)backend=new GoBackend(context);return backend;}
    private void updateStatic(){
        String text=store.read();snapshot.analysis=text.isEmpty()?null:VpnConfigAnalysis.of(text);
        snapshot.label=store.label();snapshot.endpoint=snapshot.analysis==null?"":snapshot.analysis.endpointHost;
        if(!store.configured())snapshot.state=VpnHealth.State.NOT_CONFIGURED;else if(snapshot.state==VpnHealth.State.NOT_CONFIGURED)snapshot.state=VpnHealth.State.OFF;
    }
    private void set(VpnHealth.State s,String error){synchronized(this){snapshot.state=s;if(error!=null&&!error.isEmpty())snapshot.error=error;}notifyListeners();}
    private void notifyListeners(){for(Listener l:listeners)l.onVpnChanged();}
    static String describe(Exception e){
        if(e instanceof BackendException){
            switch(((BackendException)e).getReason()){
                case VPN_NOT_AUTHORIZED:return "Android has not granted VPN permission to Saandio TV.";
                case UNABLE_TO_START_VPN:return "Android could not start the VPN service. Another VPN may be set as always-on.";
                case TUN_CREATION_ERROR:return "Android refused to create the tunnel interface.";
                case DNS_RESOLUTION_FAILURE:return "The VPN server name could not be resolved.";
                default:return "The WireGuard backend reported an error.";
            }
        }
        if(e instanceof com.wireguard.config.BadConfigException)return "The configuration is not valid WireGuard syntax.";
        String m=e.getMessage();return m!=null&&m.startsWith("The ")?m:"The VPN could not be started.";
    }
}
