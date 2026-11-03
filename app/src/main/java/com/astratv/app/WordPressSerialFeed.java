package com.astratv.app;

import java.io.IOException;
import java.util.*;

/**
 * Reads a catch-up site's public RSS feed (/feed/, then ?paged=2…) for episode metadata. The
 * sites' robots.txt only excludes /wp-admin/. Requests are conditional (ETag / Last-Modified via
 * {@link CatalogHttp}), spaced apart, and paging stops at the first page with nothing new, so a
 * routine refresh is one or two requests. Episode pages and video links are never requested.
 */
final class WordPressSerialFeed implements SerialCatchupProvider.Feed {
    static final long PAGE_GAP_MS=400;
    private final String feedUrl,provider,providerName,language;
    private final int maxPages;
    WordPressSerialFeed(String feedUrl,String provider,String providerName,String language,int maxPages){
        if(!Library.https(feedUrl))throw new IllegalArgumentException("Feeds must use HTTPS");
        this.feedUrl=feedUrl;this.provider=provider;this.providerName=providerName;this.language=language;this.maxPages=Math.max(1,maxPages);
    }
    @Override public List<CatchupEpisode> episodes(CatalogHttp http,Set<String> known)throws Exception{
        List<CatchupEpisode> out=new ArrayList<>();
        for(int page=1;page<=maxPages;page++){
            if(page>1)Thread.sleep(PAGE_GAP_MS);
            CatalogHttp.Result r;
            try{r=http.get(page==1?feedUrl:feedUrl+"?paged="+page);}
            catch(IOException e){if(page==1)throw e;break;}
            List<CatchupEpisode> parsed=SerialFeedParser.parse(r.body,provider,providerName,language);
            if(parsed.isEmpty()){if(page==1)throw new IOException(providerName+" feed listed no episodes. Its format may have changed.");break;}
            out.addAll(parsed);
            boolean fresh=false;for(CatchupEpisode e:parsed)if(!known.contains(e.episodeId))fresh=true;
            if(!fresh)break;
        }
        return out;
    }
}
