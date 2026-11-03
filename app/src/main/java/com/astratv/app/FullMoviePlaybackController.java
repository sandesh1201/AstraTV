package com.astratv.app;

import java.io.IOException;
import java.util.*;

/**
 * Watch Movie: catalog record → provider-specific resolver → authorized, validated media
 * descriptor → {@link PlayerActivity} (Media3). It never plays a trailer, and for the two movie
 * tabs it never starts a title its provider has not confirmed. Trailers live in
 * {@link TrailerPreviewController}; the two share no state.
 */
@androidx.media3.common.util.UnstableApi
final class FullMoviePlaybackController {
    private final ProviderRepository repository;
    FullMoviePlaybackController(ProviderRepository repository){this.repository=repository;}

    /** What is known before asking the provider: unauthorized sources are refused outright. */
    static MovieAvailability known(Library.Title t,CatalogItem item){
        if(t==null)return MovieAvailability.UNAVAILABLE;
        if(ProviderRepository.unauthorized(t.provider))return MovieAvailability.UNSUPPORTED;
        if(!SourceMovieCatalog.sourceTitle(t))return MovieAvailability.AVAILABLE;
        return item!=null&&item.key().equals(t.key())?item.availability():MovieAvailability.CHECKING;
    }
    /** Watch Movie is enabled only for AVAILABLE. CHECKING asks the provider first; the others explain why not. */
    static boolean canWatch(MovieAvailability a){return a==MovieAvailability.AVAILABLE;}
    /** Saandio cannot change these by asking again: it has no sign-in flow and no integration for the provider. */
    static boolean definitive(MovieAvailability a){return a==MovieAvailability.UNAVAILABLE||a==MovieAvailability.UNSUPPORTED||a==MovieAvailability.LOGIN_REQUIRED;}
    static String explain(MovieAvailability a,String reason){
        String why=reason==null?"":reason.trim();
        switch(a){
            case UNSUPPORTED:return why.isEmpty()?"This provider offers no integration Saandio may use on Android TV, so its titles cannot be played here.":why;
            case LOGIN_REQUIRED:return why.isEmpty()?"The provider requires a signed-in account, or the session expired. Saandio does not sign in to providers on your behalf.":why;
            case UNAVAILABLE:return why.isEmpty()?"The provider reports that this title cannot be streamed right now.":why;
            case PROVIDER_ERROR:return (why.isEmpty()?"The provider did not answer correctly.":why)+" Try again shortly.";
            case CHECKING:return "Saandio is still asking the provider whether this title can be played.";
            default:return why;
        }
    }

    static final class Verdict{
        final MovieAvailability availability;final String reason;
        Verdict(MovieAvailability availability,String reason){this.availability=availability;this.reason=reason==null?"":reason;}
    }
    /** Background thread. Availability straight from the provider's playback API, for a title with no saved verdict. */
    Verdict check(Library.Title t,String country){
        if(ProviderRepository.unauthorized(t.provider))return new Verdict(MovieAvailability.UNSUPPORTED,ProviderRepository.unauthorizedReason(t.provider));
        try{
            String first=null;long now=System.currentTimeMillis();
            for(ProviderRepository.Stream s:repository.streams(t)){
                PlaybackCandidate c=PlaybackCandidate.of(t.provider,s);
                String problem=c.problem(now,country,c.drmScheme.isEmpty()||PlaybackService.widevineSupported());
                if(problem==null)return new Verdict(MovieAvailability.AVAILABLE,"");
                if(first==null)first=problem;
            }
            return new Verdict(MovieAvailability.UNAVAILABLE,first==null?"The provider returned no stream for this title.":first);
        }catch(Exception e){
            Throwable cause=e.getCause();
            if(cause instanceof LicensedProvider.ProviderException)return new Verdict(MovieAvailability.of(((LicensedProvider.ProviderException)cause).code),cause.getMessage());
            return new Verdict(MovieAvailability.PROVIDER_ERROR,ProviderRepository.message(e));
        }
    }

    /**
     * Background thread. Asks the title's provider for playback, keeps only descriptors that pass the
     * player's checks, and throws with the first reason when none do. Every stage is logged.
     */
    List<ProviderRepository.Stream> resolve(Library.Title t,MovieAvailability availability,String country)throws Exception{
        String key=t.key();
        PlaybackTrace.log(PlaybackTrace.Stage.SELECTED,key,t.providerName);
        PlaybackTrace.log(PlaybackTrace.Stage.RECORD,key,"provider "+t.provider+" · "+availability.name());
        if(definitive(availability)){String m=explain(availability,"");PlaybackTrace.log(PlaybackTrace.Stage.ERROR,key,availability.name());throw new IOException(m);}
        PlaybackTrace.log(PlaybackTrace.Stage.RESOLVER,key,t.provider);
        List<ProviderRepository.Stream> streams;
        try{streams=repository.streams(t);}
        catch(Exception e){PlaybackTrace.log(PlaybackTrace.Stage.ERROR,key,"resolver: "+ProviderRepository.message(e));throw e;}
        List<ProviderRepository.Stream> ok=new ArrayList<>();String first=null;long now=System.currentTimeMillis();
        for(ProviderRepository.Stream s:streams){
            PlaybackCandidate c=PlaybackCandidate.of(t.provider,s);
            String problem=c.problem(now,country,c.drmScheme.isEmpty()||PlaybackService.widevineSupported());
            if(problem==null)ok.add(s);else if(first==null)first=problem;
        }
        PlaybackTrace.log(PlaybackTrace.Stage.DESCRIPTOR,key,ok.size()+" of "+streams.size()+" descriptors valid");
        if(ok.isEmpty()){String m=first==null?"The provider returned no playable stream for this title.":first;PlaybackTrace.log(PlaybackTrace.Stage.ERROR,key,m);throw new IOException(m);}
        return ok;
    }
    /**
     * Background thread. Re-validates the chosen descriptor (it may have expired while the viewer chose)
     * and probes it once, so a 403/404 or a web page is reported here instead of inside the player.
     */
    PlaybackCandidate descriptor(Library.Title t,ProviderRepository.Stream s,String country)throws IOException{
        String key=t.key();PlaybackCandidate c=PlaybackCandidate.of(t.provider,s);
        String problem=c.problem(System.currentTimeMillis(),country,c.drmScheme.isEmpty()||PlaybackService.widevineSupported());
        if(problem!=null){PlaybackTrace.log(PlaybackTrace.Stage.ERROR,key,problem);throw new IOException(problem);}
        try{StreamProbe.inspect(s);}catch(IOException e){PlaybackTrace.log(PlaybackTrace.Stage.ERROR,key,"probe: "+ProviderRepository.message(e));throw e;}
        PlaybackTrace.log(PlaybackTrace.Stage.PLAYER,key,"launch "+c.label());
        return c;
    }
}
