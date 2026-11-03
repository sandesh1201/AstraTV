package com.astratv.app;

import java.util.Locale;

/** Per-source time-to-live, retry backoff and the freshness text shown to the viewer. Pure logic. */
final class CatalogFreshnessTracker {
    private CatalogFreshnessTracker(){}
    /** Two checks of the same source closer than this are always coalesced. */
    static final long MIN_GAP_MS=60_000L;
    static final long BASE_BACKOFF_MS=30_000L,MAX_BACKOFF_MS=30L*60_000L;

    static final class State {
        long syncedAt,attemptAt,nextAttemptAt;int failures,items;String message="";
        State copy(){State s=new State();s.syncedAt=syncedAt;s.attemptAt=attemptAt;s.nextAttemptAt=nextAttemptAt;s.failures=failures;s.items=items;s.message=message;return s;}
    }

    /**
     * A source is checked when its TTL has passed, or on app launch/foreground when the source
     * supports cheap freshness checks. Failed sources wait for their backoff window either way.
     */
    static boolean due(State s,long ttlMs,long now,boolean foregroundCheck){
        if(s==null||s.attemptAt==0)return true;
        if(s.failures>0&&now<s.nextAttemptAt)return false;
        if(now-s.attemptAt<MIN_GAP_MS)return false;
        if(foregroundCheck)return true;
        return s.syncedAt==0||now-s.syncedAt>=ttlMs;
    }
    /** 30 s, 60 s, 2 min … capped at the smaller of 30 min and the source TTL (never below 30 s). */
    static long backoff(int failures,long ttlMs){
        int n=Math.max(1,failures);long delay=BASE_BACKOFF_MS<<Math.min(n-1,10);
        return Math.min(delay,Math.max(BASE_BACKOFF_MS,Math.min(MAX_BACKOFF_MS,ttlMs)));
    }
    static State started(State prev,long now){State s=prev==null?new State():prev.copy();s.attemptAt=now;return s;}
    static State succeeded(State prev,long now,int items){
        State s=prev==null?new State():prev.copy();s.syncedAt=now;s.attemptAt=Math.max(s.attemptAt,now);s.failures=0;s.nextAttemptAt=0;s.items=items;s.message="";return s;
    }
    static State failed(State prev,long now,long ttlMs,String message){
        State s=prev==null?new State():prev.copy();s.attemptAt=Math.max(s.attemptAt,now);s.failures++;s.nextAttemptAt=now+backoff(s.failures,ttlMs);s.message=message==null?"":message;return s;
    }
    /** True when the viewer is looking at an older catalog than the source would normally allow. */
    static boolean stale(State s,long ttlMs,long now){return s!=null&&s.syncedAt>0&&(s.failures>0||now-s.syncedAt>2*ttlMs);}
    static String label(State s,long ttlMs,long now){
        if(s==null||s.syncedAt==0)return s!=null&&s.failures>0?"Not loaded yet · "+s.message:"Not loaded yet";
        String age=age(now-s.syncedAt);
        if(s.failures>0)return "Showing saved catalog from "+age+" · source unavailable, retrying";
        return stale(s,ttlMs,now)?"Updated "+age+" · refresh pending":"Updated "+age;
    }
    static String age(long ms){
        long m=Math.max(0,ms)/60_000L;
        if(m<1)return "just now";if(m<60)return m+" min ago";
        long h=m/60;if(h<48)return h+" h ago";
        return String.format(Locale.US,"%d days ago",h/24);
    }
}
