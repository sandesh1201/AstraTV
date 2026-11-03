package com.astratv.app;

/**
 * Whether a source-confirmed movie can be watched in Saandio. Decided only by the content
 * provider's own playback API, never by the presence of a poster, a title or a web page.
 */
enum MovieAvailability {
    AVAILABLE,CHECKING,UNAVAILABLE,LOGIN_REQUIRED,UNSUPPORTED,PROVIDER_ERROR;

    /** Unknown names read as CHECKING. Rows saved before LOGIN_REQUIRED/UNSUPPORTED existed stored AUTHORIZATION_REQUIRED. */
    static MovieAvailability parse(String name){
        if(name==null||name.isEmpty())return CHECKING;
        if(name.equals("AUTHORIZATION_REQUIRED"))return UNSUPPORTED;
        try{return valueOf(name);}catch(IllegalArgumentException e){return CHECKING;}
    }
    /** Maps a partner API refusal to what the viewer is told. */
    static MovieAvailability of(LicensedProvider.ProviderException.Code code){
        switch(code){
            case UNAUTHENTICATED:return LOGIN_REQUIRED;
            case NOT_AUTHORIZED:return UNSUPPORTED;
            case RATE_LIMITED:case MALFORMED:case SERVICE_ERROR:return PROVIDER_ERROR;
            default:return UNAVAILABLE;
        }
    }
    String label(){
        switch(this){
            case AVAILABLE:return "Available";
            case CHECKING:return "Checking availability";
            case UNAVAILABLE:return "Unavailable";
            case LOGIN_REQUIRED:return "Login required";
            case UNSUPPORTED:return "Unsupported on Android TV";
            default:return "Provider error";
        }
    }
}
