package com.astratv.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/** Observes OS VPN networks, whether created by Astra's WireGuard tunnel or by another VPN app. */
final class NetworkStatus {
    private NetworkStatus(){}
    /** The VPN network this app owns, else any VPN network, else null. */
    static Network vpnNetwork(Context context){
        ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm==null)return null;
        Network any=null;
        for(Network network:cm.getAllNetworks()){
            NetworkCapabilities caps=cm.getNetworkCapabilities(network);
            if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))continue;
            if(android.os.Build.VERSION.SDK_INT>=30&&caps.getOwnerUid()==android.os.Process.myUid())return network;
            if(any==null)any=network;
        }
        return any;
    }
    static boolean vpnActive(Context context){
        ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm==null)return false;
        for(Network network:cm.getAllNetworks()){
            NetworkCapabilities caps=cm.getNetworkCapabilities(network);
            if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return true;
        }
        return false;
    }
    static List<String> dns(Context context){
        List<String> out=new ArrayList<>();
        ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm==null)return out;
        for(Network network:cm.getAllNetworks()){
            NetworkCapabilities caps=cm.getNetworkCapabilities(network);
            if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))continue;
            LinkProperties link=cm.getLinkProperties(network);if(link==null)continue;
            for(InetAddress address:link.getDnsServers())out.add(address.getHostAddress());
        }
        return out;
    }
}
