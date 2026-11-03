package com.astratv.app;

/**
 * Stage-by-stage log of one Watch Movie attempt, to logcat (tag {@value #TAG}) and the developer
 * diagnostics screen. Details pass through {@link Diagnostics#redact}, so links and tokens never appear.
 */
final class PlaybackTrace {
    static final String TAG="SaandioPlayback";
    enum Stage{SELECTED,RECORD,RESOLVER,DESCRIPTOR,PLAYER,READY,ERROR}
    private PlaybackTrace(){}

    static String line(Stage stage,String titleKey,String detail){
        return stage.name()+" · "+Diagnostics.redact(titleKey)+(detail==null||detail.isEmpty()?"":" · "+Diagnostics.redact(detail));
    }
    static void log(Stage stage,String titleKey,String detail){
        String l=line(stage,titleKey,detail);
        try{if(stage==Stage.ERROR)android.util.Log.w(TAG,l);else android.util.Log.i(TAG,l);}catch(RuntimeException ignored){/* JVM unit tests have no logcat */}
        Diagnostics.get().playbackStage(stage.name(),l);
    }
}
