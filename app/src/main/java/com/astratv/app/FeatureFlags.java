package com.astratv.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import java.util.function.Function;

/**
 * Rollback switches for the Live TV additions and Catch-Up TV. Build defaults come from
 * {@code BuildConfig}; a developer can override each one at runtime in Settings. With
 * {@link Flag#NEW_LIVE_SOURCES} off, Live TV shows exactly the original iptv-org directory.
 */
final class FeatureFlags {
    enum Flag {
        NEW_LIVE_SOURCES("ENABLE_NEW_LIVE_SOURCES","New Live TV sources",BuildConfig.ENABLE_NEW_LIVE_SOURCES),
        SUN_NETWORK("ENABLE_SUN_NETWORK_INTEGRATION","Sun Network channels (official app handoff)",BuildConfig.ENABLE_SUN_NETWORK_INTEGRATION),
        CATCHUP_TV("ENABLE_CATCHUP_TV","Catch-Up TV tab",BuildConfig.ENABLE_CATCHUP_TV),
        /** Doordarshan/Prasar Bharati YouTube archives and Sun NXT tiles. Off: Catch-Up TV lists TamilTVSerial and TeluguStream only. */
        OFFICIAL_ARCHIVES("ENABLE_OFFICIAL_CATCHUP_ARCHIVES","Catch-Up: broadcaster archive rows",BuildConfig.ENABLE_OFFICIAL_CATCHUP_ARCHIVES),
        EXTENDED_EPG("ENABLE_EXTENDED_EPG","Programme guide (EPG)",BuildConfig.ENABLE_EXTENDED_EPG);
        final String key,label;final boolean buildDefault;
        Flag(String key,String label,boolean buildDefault){this.key=key;this.label=label;this.buildDefault=buildDefault;}
    }
    private static final String PREFS="astra_flags";
    private FeatureFlags(){}

    static boolean on(Context c,Flag f){SharedPreferences p=prefs(c);return effective(f,x->p.contains(x.key)?p.getBoolean(x.key,x.buildDefault):x.buildDefault);}
    /** Null clears the override and returns the flag to its build default. */
    static void set(Context c,Flag f,Boolean value){SharedPreferences.Editor e=prefs(c).edit();if(value==null)e.remove(f.key);else e.putBoolean(f.key,value);e.apply();}
    static boolean overridden(Context c,Flag f){return prefs(c).contains(f.key);}
    static void resetAll(Context c){prefs(c).edit().clear().apply();}
    /** Sun Network rides on the new-sources layer, so turning that off also turns Sun Network off. */
    static boolean effective(Flag f,Function<Flag,Boolean> raw){
        boolean v=Boolean.TRUE.equals(raw.apply(f));
        return f==Flag.SUN_NETWORK?v&&Boolean.TRUE.equals(raw.apply(Flag.NEW_LIVE_SOURCES)):v;
    }
    private static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences(PREFS,0);}
}
