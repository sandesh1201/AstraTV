package com.astratv.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

/**
 * Playback validations for live channels: the time the player last reached READY on a channel and
 * kept playing for {@link #CONTINUITY_MS}. Stored apart from probe health so existing health
 * history, retirement and sorting are untouched. Keys are catalog keys only, never stream URLs.
 */
final class ChannelValidation {
    static final long CONTINUITY_MS=10_000L;
    private static final String PREFS="astra_live_validation";
    private ChannelValidation(){}

    static void recordPlayback(Context c,String key,long at){if(key!=null&&!key.isEmpty())prefs(c).edit().putLong(key,at).apply();}
    static Map<String,Long> all(Context c){
        Map<String,Long> out=new HashMap<>();
        for(Map.Entry<String,?> e:prefs(c).getAll().entrySet())if(e.getValue() instanceof Long)out.put(e.getKey(),(Long)e.getValue());
        return out;
    }
    /** True when playback moved forward by at least most of the observed window: a frozen or looping-at-start stream does not count. */
    static boolean continuous(long startPositionMs,long endPositionMs,long elapsedMs,boolean playing){
        return playing&&elapsedMs>=CONTINUITY_MS&&endPositionMs-startPositionMs>=elapsedMs/2;
    }
    private static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences(PREFS,0);}
}
