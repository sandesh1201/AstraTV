package com.astratv.app;

/** Playback gates and honest quality labels. Unknown quality is never called HD. */
public final class PlaybackPolicy {
    private PlaybackPolicy(){}
    public static boolean allow(boolean requireVpn,boolean vpnActive){return !requireVpn||vpnActive;}
    public static String qualityLabel(String sourceName){
        String n=sourceName==null?"":sourceName.toLowerCase(java.util.Locale.ROOT);
        if(n.contains("2160")||n.contains("4k"))return "4K";
        if(n.contains("1080"))return "1080p";
        if(n.contains("720"))return "720p";
        if(n.contains("480"))return "480p";
        return "";
    }
    /** Label from a declared or decoded frame height. Unknown (0) stays unlabelled. */
    public static String heightLabel(int height){
        if(height>=2160)return "4K";
        if(height>=1080)return "1080p";
        if(height>=720)return "720p";
        if(height>0)return height+"p";
        return "";
    }
    public static final int MAX_RETRIES=3;
    /**
     * Delay before retrying a failed stream, or -1 when the error is not transient. Codes are
     * Media3 PlaybackException values: 1002 behind live window, 1003 timeout, 2000-2002 and 2004 network/HTTP.
     */
    public static long retryDelayMs(int attempt,int errorCode){
        if(attempt>=MAX_RETRIES)return -1;
        if(errorCode==1002)return 0;
        boolean transientError=errorCode==1003||errorCode==2000||errorCode==2001||errorCode==2002||errorCode==2004;
        return transientError?2000L<<attempt:-1;
    }
    public static String sourceChoice(String name){
        String quality=qualityLabel(name);
        return quality.isEmpty()||name.toLowerCase(java.util.Locale.ROOT).contains(quality.toLowerCase(java.util.Locale.ROOT))?name:name+" · "+quality;
    }
}
