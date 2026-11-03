package com.astratv.app;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import java.util.*;

/**
 * Hands playback to an official app instead of extracting its streams. Only allowlisted packages
 * are opened; YouTube links open in the YouTube app (never a browser or an extracted media URL),
 * and a missing app leads to its Play Store page when a store is installed.
 */
final class OfficialAppHandoff {
    static final String SUN_NXT="com.suntv.sunnxt",YOUTUBE_TV="com.google.android.youtube.tv",YOUTUBE="com.google.android.youtube",WAVES="com.prasarbharati.android";
    /** Broadcasters' own catch-up apps, Play listings checked 2026-11-03 (tools/broadcaster-apps-probe.js). */
    static final String JIOHOTSTAR="in.startv.hotstar",ZEE5="com.graymatrix.did",ETV_WIN="com.etvwin.mobile",SONY_LIV="com.sonyliv";
    static final Set<String> ALLOWED=Collections.unmodifiableSet(new HashSet<>(Arrays.asList(SUN_NXT,YOUTUBE_TV,YOUTUBE,WAVES,JIOHOTSTAR,ZEE5,ETV_WIN,SONY_LIV)));
    enum Result{OPENED,STORE,MISSING}
    private OfficialAppHandoff(){}

    static String appName(String pkg){
        if(SUN_NXT.equals(pkg))return "Sun NXT";
        if(WAVES.equals(pkg))return "WAVES (Prasar Bharati)";
        if(JIOHOTSTAR.equals(pkg))return "JioHotstar";
        if(ZEE5.equals(pkg))return "ZEE5";
        if(ETV_WIN.equals(pkg))return "ETV Win";
        if(SONY_LIV.equals(pkg))return "Sony LIV";
        if(YOUTUBE_TV.equals(pkg)||YOUTUBE.equals(pkg))return "YouTube";
        return "the official app";
    }
    /** Opens the item's official route. */
    static Result open(Context c,CatalogItem item){
        if(CatalogItem.YOUTUBE_HANDOFF.equals(item.playbackType))return openYoutube(c,item.playbackReference);
        if(CatalogItem.APP_HANDOFF.equals(item.playbackType))return openApp(c,item.playbackReference);
        return Result.MISSING;
    }
    static Result openApp(Context c,String pkg){
        if(!ALLOWED.contains(pkg))return Result.MISSING;
        PackageManager pm=c.getPackageManager();
        Intent launch=pm.getLeanbackLaunchIntentForPackage(pkg);if(launch==null)launch=pm.getLaunchIntentForPackage(pkg);
        if(launch!=null&&start(c,launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))return Result.OPENED;
        return store(c,pkg);
    }
    static Result openYoutube(Context c,String reference){
        String url=youtubeUrl(reference);if(url==null)return Result.MISSING;
        for(String pkg:new String[]{YOUTUBE_TV,YOUTUBE})
            if(installed(c,pkg)&&start(c,new Intent(Intent.ACTION_VIEW,Uri.parse(url)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))return Result.OPENED;
        return store(c,YOUTUBE_TV);
    }
    /** youtube.com URL for an 11-character video id or a channel's live page; null for anything else. */
    static String youtubeUrl(String reference){
        if(reference==null)return null;
        if(reference.matches("[A-Za-z0-9_-]{11}"))return "https://www.youtube.com/watch?v="+reference;
        if(reference.matches("UC[A-Za-z0-9_-]{22}"))return "https://www.youtube.com/channel/"+reference+"/live";
        return null;
    }
    static boolean installed(Context c,String pkg){
        try{c.getPackageManager().getPackageInfo(pkg,0);return true;}catch(PackageManager.NameNotFoundException e){return false;}
    }
    private static Result store(Context c,String pkg){
        Intent market=new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id="+pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return start(c,market)?Result.STORE:Result.MISSING;
    }
    private static boolean start(Context c,Intent i){
        if(i.resolveActivity(c.getPackageManager())==null)return false;
        try{c.startActivity(i);return true;}catch(ActivityNotFoundException|SecurityException e){return false;}
    }
}
