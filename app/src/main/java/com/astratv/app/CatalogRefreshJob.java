package com.astratv.app;

import android.app.job.*;
import android.content.ComponentName;
import android.content.Context;

/**
 * Keeps the saved catalog warm between launches: every six hours, on any network, the sources
 * whose refresh window has passed sync with the same TTL, backoff and conditional requests as a
 * launch. First-run content never depends on this job; the app syncs on launch regardless.
 * JobScheduler (API 21+) is used instead of WorkManager to keep its startup initializer and
 * dependencies out of cold start on low-memory TVs.
 */
public class CatalogRefreshJob extends JobService {
    static final int JOB_ID=0x5a4d01;
    static final long PERIOD_MS=6L*60*60_000L,FLEX_MS=60L*60_000L,BUDGET_MS=8L*60_000L;
    private volatile boolean stopped;

    /** Idempotent; called off the main thread when the app comes to the foreground. */
    static void schedule(Context context){
        try{
            JobScheduler js=context.getSystemService(JobScheduler.class);if(js==null||js.getPendingJob(JOB_ID)!=null)return;
            js.schedule(new JobInfo.Builder(JOB_ID,new ComponentName(context,CatalogRefreshJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setRequiresBatteryNotLow(true).setPeriodic(PERIOD_MS,FLEX_MS).build());
        }catch(RuntimeException ignored){/* scheduling is an optimization; launch sync still runs */}
    }
    @Override public boolean onStartJob(JobParameters params){
        stopped=false;
        CatalogRepository.get(this).syncInBackground(BUDGET_MS,()->{if(!stopped)jobFinished(params,false);});
        return true;
    }
    @Override public boolean onStopJob(JobParameters params){stopped=true;return true;}
}
