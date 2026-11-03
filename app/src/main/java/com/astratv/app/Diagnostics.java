package com.astratv.app;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Developer diagnostics: counters and short event histories for catalog sync, artwork, playback
 * and memory. Entries hold reasons, codes and host names only. Every string passes through
 * {@link #redact} so keys, tokens and signed playback URLs never reach this screen or a log.
 */
final class Diagnostics {
    static final int HISTORY=20;
    private static final Diagnostics INSTANCE=new Diagnostics();
    static Diagnostics get(){return INSTANCE;}

    static final class Event{
        final long at;final String what,detail;
        Event(long at,String what,String detail){this.at=at;this.what=what;this.detail=detail;}
    }
    final AtomicInteger imagesLoaded=new AtomicInteger(),imagesFailed=new AtomicInteger(),imagesSkipped=new AtomicInteger(),imageRetries=new AtomicInteger();
    final AtomicInteger networkTimeouts=new AtomicInteger(),metadataFailures=new AtomicInteger(),playbackFailures=new AtomicInteger(),rebuffers=new AtomicInteger();
    final AtomicInteger trailersStarted=new AtomicInteger(),trailerFallbacks=new AtomicInteger();
    private final Deque<Event> playback=new ArrayDeque<>(),metadata=new ArrayDeque<>(),memory=new ArrayDeque<>(),stages=new ArrayDeque<>(),trailers=new ArrayDeque<>(),providers=new ArrayDeque<>();
    private volatile Event lastMediaInit;

    /** Why a content provider's title could not be listed or played, in the terms a developer acts on. */
    enum Problem{
        CATALOG_REQUEST_FAILED("Catalog request failed"),
        PLAYBACK_UNAVAILABLE("Title discovered, playback unavailable"),
        SESSION_EXPIRED("Expired session"),
        AUTH_FAILED("Provider authentication failure"),
        UNSUPPORTED_INTEGRATION("Unsupported Android TV integration"),
        MEDIA3_INIT("Media3 initialization failure"),
        NETWORK("Network error"),
        DECODER("Decoder error");
        final String label;Problem(String label){this.label=label;}
    }
    private final Map<Problem,AtomicInteger> problems=new EnumMap<>(Problem.class);
    { for(Problem p:Problem.values())problems.put(p,new AtomicInteger()); }
    private final Set<String> reportedUnsupported=Collections.synchronizedSet(new HashSet<>());

    void provider(Problem p,String source,String detail){
        problems.get(p).incrementAndGet();
        add(providers,new Event(System.currentTimeMillis(),p.label,redact(CatalogViews.join(source,detail))));
    }
    /** A source with no usable integration is reported once per session, not on every sync window. */
    void unsupported(String source,String reason){if(reportedUnsupported.add(source))provider(Problem.UNSUPPORTED_INTEGRATION,source,reason);}
    int problemCount(Problem p){return problems.get(p).get();}
    List<Event> providerEvents(){return snapshot(providers);}

    /** A failed catalog fetch: partner API refusals keep their meaning, I/O failures are network errors. */
    static Problem ofCatalog(Throwable e){
        Throwable t=e;while(t!=null&&!(t instanceof LicensedProvider.ProviderException)&&t.getCause()!=null&&t.getCause()!=t)t=t.getCause();
        if(t instanceof LicensedProvider.ProviderException){
            LicensedProvider.ProviderException.Code code=((LicensedProvider.ProviderException)t).code;
            if(code==LicensedProvider.ProviderException.Code.UNAUTHENTICATED)return Problem.SESSION_EXPIRED;
            if(code==LicensedProvider.ProviderException.Code.NOT_AUTHORIZED)return Problem.AUTH_FAILED;
            return Problem.CATALOG_REQUEST_FAILED;
        }
        return e instanceof java.io.IOException?Problem.NETWORK:Problem.CATALOG_REQUEST_FAILED;
    }
    /** A provider's verdict on one listed title. Null when the title is playable. */
    static Problem ofAvailability(MovieAvailability a){
        switch(a){
            case LOGIN_REQUIRED:return Problem.SESSION_EXPIRED;
            case UNSUPPORTED:return Problem.UNSUPPORTED_INTEGRATION;
            case UNAVAILABLE:case PROVIDER_ERROR:return Problem.PLAYBACK_UNAVAILABLE;
            default:return null;
        }
    }
    /** A Media3 failure. Null for falling behind live, which the player recovers from by itself. */
    static Problem ofPlayback(PlaybackErrors.Category c,int errorCode){
        if(errorCode==4001)return Problem.MEDIA3_INIT;
        switch(c){
            case NETWORK:return Problem.NETWORK;
            case DECODER:case UNSUPPORTED_CODEC:return Problem.DECODER;
            case AUTHORIZATION:return Problem.AUTH_FAILED;
            case EXPIRED_URL:return Problem.SESSION_EXPIRED;
            case DRM:return Problem.UNSUPPORTED_INTEGRATION;
            case BEHIND_LIVE:return null;
            default:return Problem.PLAYBACK_UNAVAILABLE;
        }
    }

    void metadataFailure(String source,String reason,boolean timeout){
        metadataFailures.incrementAndGet();if(timeout)networkTimeouts.incrementAndGet();
        add(metadata,new Event(System.currentTimeMillis(),source,redact(reason)));
    }
    void imageResult(boolean ok,boolean timeout){if(ok)imagesLoaded.incrementAndGet();else imagesFailed.incrementAndGet();if(timeout)networkTimeouts.incrementAndGet();}
    void playbackError(String provider,PlaybackErrors.Category category,String codeName,int httpStatus){
        playbackFailures.incrementAndGet();if(category==PlaybackErrors.Category.NETWORK)networkTimeouts.incrementAndGet();
        add(playback,new Event(System.currentTimeMillis(),category.name(),redact(CatalogViews.join(provider,codeName,httpStatus>0?"HTTP "+httpStatus:""))));
    }
    void mediaReady(String provider,String type,int height,long startupMs){
        lastMediaInit=new Event(System.currentTimeMillis(),redact(provider),CatalogViews.join(type,PlaybackPolicy.heightLabel(height),startupMs>0?"ready in "+startupMs+" ms":""));
    }
    void memoryWarning(int level){add(memory,new Event(System.currentTimeMillis(),trimLabel(level),""));}

    void playbackStage(String stage,String detail){add(stages,new Event(System.currentTimeMillis(),stage,redact(detail)));}
    void trailer(String outcome,String detail){add(trailers,new Event(System.currentTimeMillis(),outcome,redact(detail)));}
    List<Event> stageEvents(){return snapshot(stages);}
    List<Event> trailerEvents(){return snapshot(trailers);}
    List<Event> playbackEvents(){return snapshot(playback);}
    List<Event> metadataEvents(){return snapshot(metadata);}
    List<Event> memoryEvents(){return snapshot(memory);}
    Event lastMediaInit(){return lastMediaInit;}

    private static void add(Deque<Event> into,Event e){synchronized(into){into.addFirst(e);while(into.size()>HISTORY)into.removeLast();}}
    private static List<Event> snapshot(Deque<Event> from){synchronized(from){return new ArrayList<>(from);}}

    private static final Pattern URL=Pattern.compile("(https?://[^/\\s?#]+)[^\\s]*",Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRET=Pattern.compile("(?i)\\b(api_key|apikey|key|token|access_token|auth|signature|sig|hdnts|policy|password)=([^&\\s]+)");
    private static final Pattern BEARER=Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._\\-]+");
    /** Keeps only scheme and host of URLs and masks credential-like parameters and bearer tokens. */
    static String redact(String text){
        if(text==null)return "";
        String out=BEARER.matcher(text).replaceAll("Bearer …");
        out=SECRET.matcher(out).replaceAll("$1=…");
        out=URL.matcher(out).replaceAll("$1/…");
        return out.length()>160?out.substring(0,160)+"…":out;
    }
    static String trimLabel(int level){
        switch(level){
            case 5:return "Running moderate";
            case 10:return "Running low";
            case 15:return "Running critical";
            case 20:return "UI hidden";
            case 40:return "Background";
            case 60:return "Moderate (cached)";
            case 80:return "Complete (cached)";
            case -1:return "Low memory";
            default:return "Trim level "+level;
        }
    }
}
