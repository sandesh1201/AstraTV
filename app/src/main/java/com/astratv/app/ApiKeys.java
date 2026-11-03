package com.astratv.app;

import android.content.Context;

/** Settings override BuildConfig. Keys are never written to the research log or library JSON. */
final class ApiKeys {
    private ApiKeys(){}
    static String tmdb(Context c){return first(pref(c,"key.tmdb"),BuildConfig.TMDB_API_KEY);}
    static String omdb(Context c){return first(pref(c,"key.omdb"),BuildConfig.OMDB_API_KEY);}
    static String trakt(Context c){return first(pref(c,"key.trakt"),BuildConfig.TRAKT_CLIENT_ID);}
    static String youtube(Context c){return first(pref(c,"key.youtube"),BuildConfig.YOUTUBE_API_KEY);}
    /** TheSportsDB publishes "123" as its free low-volume key; a personal or premium key replaces it. */
    static final String SPORTSDB_FREE="123";
    static String sportsDb(Context c){return first(pref(c,"key.sportsdb"),SPORTSDB_FREE);}
    static void saveSportsDb(Context c,String key){c.getSharedPreferences("astra_ui_v1",0).edit().putString("key.sportsdb",key==null?"":key.trim()).apply();}
    static boolean metadataReady(Context c){return !tmdb(c).isEmpty();}
    static void save(Context c,String tmdb,String omdb,String trakt,String youtube){
        c.getSharedPreferences("astra_ui_v1",0).edit().putString("key.tmdb",tmdb.trim()).putString("key.omdb",omdb.trim()).putString("key.trakt",trakt.trim()).putString("key.youtube",youtube.trim()).apply();
    }
    private static String pref(Context c,String key){return c.getSharedPreferences("astra_ui_v1",0).getString(key,"");}
    private static String first(String a,String b){return a!=null&&!a.isEmpty()?a:b==null?"":b;}
}
