package com.astratv.app;

/**
 * Turns a Media3 failure into one of the cases a viewer can act on, and decides whether a retry
 * can help. Codes are the int values of {@code androidx.media3.common.PlaybackException} so this
 * class stays testable on the JVM.
 */
final class PlaybackErrors {
    private PlaybackErrors(){}
    enum Category{NETWORK,AUTHORIZATION,EXPIRED_URL,NOT_FOUND,UNSUPPORTED_CODEC,INVALID_MANIFEST,DRM,PROVIDER_OUTAGE,DECODER,BEHIND_LIVE,INSECURE,UNKNOWN}
    static final int MAX_TRANSIENT_RETRIES=4,MAX_DECODER_RETRIES=1,MAX_URL_REFRESHES=2;
    private static final long BASE_DELAY_MS=1000,MAX_DELAY_MS=16000;

    static final class Verdict{
        final Category category;final String message;final boolean transientError;
        Verdict(Category category,String message,boolean transientError){this.category=category;this.message=message;this.transientError=transientError;}
        boolean refreshUrl(){return category==Category.EXPIRED_URL;}
    }

    /**
     * @param httpStatus response code when the failure was an HTTP status (0 otherwise)
     * @param signedUrlExpired the candidate carries an expiry that has passed (or is within the grace window)
     */
    static Verdict classify(int errorCode,int httpStatus,boolean signedUrlExpired,boolean online){
        if(errorCode==1002)return new Verdict(Category.BEHIND_LIVE,"Fell behind the live broadcast. Jumping back to live.",true);
        if(errorCode==2007)return new Verdict(Category.INSECURE,"This source uses an insecure (non-HTTPS) connection, which Saandio blocks.",false);
        if(httpStatus>0||errorCode==2004){
            if((httpStatus==401||httpStatus==403||httpStatus==410)&&signedUrlExpired)return new Verdict(Category.EXPIRED_URL,"The playback link expired. Requesting a fresh link from the provider…",false);
            if(httpStatus==401||httpStatus==403)return new Verdict(Category.AUTHORIZATION,"The provider refused access (HTTP "+httpStatus+"). The title may need a subscription or may not be offered in your region.",false);
            if(httpStatus==404||httpStatus==410)return new Verdict(Category.NOT_FOUND,"The provider no longer has this stream (HTTP "+httpStatus+").",false);
            if(httpStatus==429||httpStatus>=500)return new Verdict(Category.PROVIDER_OUTAGE,"The provider is having a temporary problem (HTTP "+httpStatus+").",true);
            if(httpStatus>0)return new Verdict(Category.NOT_FOUND,"The provider rejected the request (HTTP "+httpStatus+").",false);
        }
        if(errorCode==2001||errorCode==2002||errorCode==1003||errorCode==2000){
            if(!online)return new Verdict(Category.NETWORK,"No network connection. Playback continues automatically when you are back online.",true);
            return new Verdict(Category.NETWORK,errorCode==2002||errorCode==1003?"The connection timed out.":"The connection to the provider was interrupted.",true);
        }
        if(errorCode==2005)return new Verdict(Category.NOT_FOUND,"The stream could not be found.",false);
        if(errorCode==2006)return new Verdict(Category.AUTHORIZATION,"The stream refused access.",false);
        if(errorCode==2003||errorCode==3002||errorCode==3004)return new Verdict(Category.INVALID_MANIFEST,"The provider sent an invalid or unsupported stream manifest.",false);
        if(errorCode==3001||errorCode==3003)return new Verdict(Category.UNSUPPORTED_CODEC,"The video container is damaged or not supported by this TV.",false);
        if(errorCode==4004||errorCode==4005)return new Verdict(Category.UNSUPPORTED_CODEC,"This TV cannot decode the stream's video or audio format.",false);
        if(errorCode==4001||errorCode==4002||errorCode==4003||errorCode==4006||errorCode==5001||errorCode==5002)return new Verdict(Category.DECODER,"The TV's media decoder failed.",true);
        if(errorCode>=6000&&errorCode<7000){
            if(errorCode==6001)return new Verdict(Category.DRM,"This TV does not support the stream's DRM system.",false);
            return new Verdict(Category.DRM,"The DRM license was refused or expired. The provider must authorize this device.",false);
        }
        return new Verdict(Category.UNKNOWN,"The stream could not be played.",false);
    }

    /** Delay before the next automatic retry, or -1 to stop and show the error. Bounded exponential backoff. */
    static long retryDelayMs(Verdict v,int attempt){
        if(v==null||!v.transientError)return -1;
        if(v.category==Category.BEHIND_LIVE)return attempt<MAX_TRANSIENT_RETRIES?0:-1;
        if(v.category==Category.DECODER)return attempt<MAX_DECODER_RETRIES?1500:-1;
        if(attempt>=MAX_TRANSIENT_RETRIES)return -1;
        return Math.min(MAX_DELAY_MS,BASE_DELAY_MS<<Math.min(attempt+1,10));
    }
}
