package com.astratv.app;

import java.util.*;

/**
 * Contract for catalog partners that grant playback rights through an official API.
 * Implementations never scrape pages, decode obfuscated tokens, or handle site logins.
 */
public interface LicensedProvider {
    String id();
    String displayName();
    /** Why this provider may or may not be used. A provider without authorization must not be queried. */
    Authorization authorization();
    Page catalog(Query query)throws ProviderException;
    Library.Title details(String titleId)throws ProviderException;
    List<Playback> playback(String titleId)throws ProviderException;

    final class Authorization {
        public final boolean granted,simulated;public final String basis;
        public Authorization(boolean granted,boolean simulated,String basis){this.granted=granted;this.simulated=simulated;this.basis=basis;}
    }
    final class Query {
        public final String language,search;public final int page,pageSize;
        public Query(String language,String search,int page,int pageSize){this.language=language==null?"":language;this.search=search==null?"":search;this.page=Math.max(1,page);this.pageSize=Math.max(1,Math.min(50,pageSize));}
    }
    final class Page {
        public final List<Library.Title> titles;public final int page,totalPages;
        public Page(List<Library.Title> titles,int page,int totalPages){this.titles=titles;this.page=page;this.totalPages=totalPages;}
        public boolean hasMore(){return page<totalPages;}
    }
    final class Playback {
        public final String url,protocol,quality,drm;public final int maxHeight;public final List<String> audioLanguages;
        public Playback(String url,String protocol,int maxHeight,String drm,List<String> audioLanguages){
            this.url=url;this.protocol=protocol;this.maxHeight=maxHeight;this.drm=drm==null?"":drm;this.audioLanguages=audioLanguages;this.quality=PlaybackPolicy.heightLabel(maxHeight);
        }
        public boolean playableWithoutLicenseServer(){return drm.isEmpty();}
    }
    final class ProviderException extends Exception {
        public enum Code{NOT_AUTHORIZED,UNAUTHENTICATED,REGION_UNAVAILABLE,RATE_LIMITED,NOT_FOUND,MALFORMED,UNSUPPORTED_DRM,UNAVAILABLE}
        public final Code code;
        public ProviderException(Code code,String message){super(message);this.code=code;}
    }
}
