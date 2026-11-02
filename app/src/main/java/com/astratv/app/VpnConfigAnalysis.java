package com.astratv.app;

import java.util.*;

/**
 * Reads a user-supplied WireGuard (wg-quick) configuration without the native library so it can be
 * validated and unit tested. Keys are never copied into findings or logs.
 */
public final class VpnConfigAnalysis {
    public static final int MAX_BYTES=16*1024;
    public final List<String> errors=new ArrayList<>(),warnings=new ArrayList<>();
    public boolean fullV4,fullV6,hasDns,hasV6Address;
    public int peers;
    public String endpointHost="";
    public final List<String> dns=new ArrayList<>();

    public boolean valid(){return errors.isEmpty();}
    public boolean fullTunnel(){return fullV4&&fullV6;}

    public static VpnConfigAnalysis of(String text){
        VpnConfigAnalysis a=new VpnConfigAnalysis();
        if(text==null||text.trim().isEmpty()){a.errors.add("The configuration is empty.");return a;}
        if(text.length()>MAX_BYTES){a.errors.add("The configuration is larger than 16 KB.");return a;}
        String section="";boolean iface=false,privateKey=false,address=false;boolean peerKey=false,peerAllowed=false,peerEndpoint=false;
        for(String raw:text.split("\\r?\\n")){
            String line=raw.trim();int hash=line.indexOf('#');if(hash>=0)line=line.substring(0,hash).trim();if(line.isEmpty())continue;
            if(line.startsWith("[")&&line.endsWith("]")){
                if(section.equals("peer"))a.closePeer(peerKey,peerAllowed,peerEndpoint);
                section=line.substring(1,line.length()-1).trim().toLowerCase(Locale.ROOT);
                if(section.equals("interface")){if(iface)a.errors.add("More than one [Interface] section.");iface=true;}
                else if(section.equals("peer")){a.peers++;peerKey=peerAllowed=peerEndpoint=false;}
                else a.errors.add("Unknown section ["+section+"].");
                continue;
            }
            int eq=line.indexOf('=');if(eq<=0){a.errors.add("A line is not in key = value form.");continue;}
            String key=line.substring(0,eq).trim().toLowerCase(Locale.ROOT),value=line.substring(eq+1).trim();
            if(key.equals("preup")||key.equals("postup")||key.equals("predown")||key.equals("postdown")){a.errors.add("Configurations with shell hooks ("+key+") are refused.");continue;}
            if(section.equals("interface")){
                if(key.equals("privatekey"))privateKey=!value.isEmpty();
                else if(key.equals("address")){address=true;for(String v:split(value))if(v.contains(":"))a.hasV6Address=true;}
                else if(key.equals("dns")){for(String v:split(value)){a.dns.add(v);a.hasDns=true;}}
            }else if(section.equals("peer")){
                if(key.equals("publickey"))peerKey=!value.isEmpty();
                else if(key.equals("allowedips")){peerAllowed=true;for(String v:split(value)){if(v.equals("0.0.0.0/0"))a.fullV4=true;if(v.equals("::/0"))a.fullV6=true;}}
                else if(key.equals("endpoint")){peerEndpoint=!value.isEmpty();if(a.endpointHost.isEmpty())a.endpointHost=host(value);}
            }else a.errors.add("Settings appear before any section.");
        }
        if(section.equals("peer"))a.closePeer(peerKey,peerAllowed,peerEndpoint);
        if(!iface)a.errors.add("Missing [Interface] section.");
        else{if(!privateKey)a.errors.add("[Interface] needs a PrivateKey.");if(!address)a.errors.add("[Interface] needs an Address.");}
        if(a.peers==0)a.errors.add("At least one [Peer] is required.");
        if(a.valid()){
            if(!a.fullV4)a.warnings.add("Split tunnel: IPv4 traffic outside AllowedIPs uses your normal connection.");
            if(!a.fullV6)a.warnings.add("IPv6 is not routed through the tunnel. Turn on IPv6 leak protection to block it.");
            if(!a.hasDns)a.warnings.add("No DNS server is set. Lookups may go to your network's resolver.");
        }
        return a;
    }
    private void closePeer(boolean key,boolean allowed,boolean endpoint){
        if(!key)errors.add("A [Peer] is missing PublicKey.");
        if(!allowed)errors.add("A [Peer] is missing AllowedIPs.");
        if(!endpoint)errors.add("A [Peer] is missing Endpoint.");
    }
    /** Adds ::/0 to every peer so IPv6 cannot leave outside the tunnel. Leaves the text unchanged when already present. */
    public static String withIpv6Block(String text){
        if(of(text).fullV6)return text;
        StringBuilder out=new StringBuilder();String section="";
        for(String raw:text.split("\\r?\\n",-1)){
            String trimmed=raw.trim();
            if(trimmed.startsWith("["))section=trimmed.toLowerCase(Locale.ROOT);
            if(section.equals("[peer]")&&trimmed.toLowerCase(Locale.ROOT).startsWith("allowedips")&&trimmed.contains("=")){
                String line=raw,comment="";int h=line.indexOf('#');if(h>=0){comment=" "+line.substring(h);line=line.substring(0,h);}
                out.append(line.replaceAll("\\s+$","")).append(", ::/0").append(comment).append('\n');continue;
            }
            out.append(raw).append('\n');
        }
        return out.toString().replaceAll("\n+$","\n");
    }
    /** Copy safe for display or support: key material replaced. */
    public static String redacted(String text){
        StringBuilder out=new StringBuilder();
        for(String raw:text.split("\\r?\\n")){String k=raw.trim().toLowerCase(Locale.ROOT);
            if(k.startsWith("privatekey")||k.startsWith("presharedkey"))out.append(raw.substring(0,raw.indexOf('=')+1)).append(" (hidden)\n");else out.append(raw).append('\n');}
        return out.toString();
    }
    private static List<String> split(String v){List<String> out=new ArrayList<>();for(String p:v.split(","))if(!p.trim().isEmpty())out.add(p.trim());return out;}
    private static String host(String endpoint){
        String e=endpoint.trim();if(e.startsWith("[")){int end=e.indexOf(']');return end>0?e.substring(1,end):e;}
        int colon=e.lastIndexOf(':');return colon>0?e.substring(0,colon):e;
    }
}
