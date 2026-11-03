package com.astratv.app;

import java.util.*;
import java.util.function.Function;

/**
 * Catch-Up TV serial episodes from one listing site (TamilTVSerial or TeluguStream). The site's
 * public feed supplies metadata only: show, episode number, air date and broadcaster. Episodes
 * are watched in the broadcaster's official app through {@link OfficialAppHandoff}; Saandio never
 * opens the site's episode pages or plays its video links, which re-host broadcaster episodes
 * without a licence. With no feed the source is not ready and its rows stay empty with {@link #notReadyReason()}.
 */
abstract class SerialCatchupProvider implements CatalogSource {
    /** Newest episodes the feed lists. Paging may stop early once every episode on a page is in {@code known}. */
    interface Feed{List<CatchupEpisode> episodes(CatalogHttp http,Set<String> known)throws Exception;}

    static final String ROW_RECENT="catchup.serials.recent",ROW_CONTINUE="catchup.serials.continue";
    static final String SHOW_TYPE="catchup-show";
    static final long RECENT_MS=7L*24*60*60_000L,RETAIN_MS=14L*24*60*60_000L;
    static final int MAX_EPISODES=900;
    private final Feed feed;
    /** Previously stored rows, so episodes that scrolled off the feed stay listed until {@link #RETAIN_MS}. */
    private Function<String,List<CatalogItem>> stored=row->Collections.emptyList();
    SerialCatchupProvider(Feed feed){this.feed=feed;}
    void retain(Function<String,List<CatalogItem>> stored){if(stored!=null)this.stored=stored;}

    /** Provider id stored on items, e.g. {@code tamiltvserial}. */
    abstract String provider();
    abstract String providerName();
    abstract String language();
    abstract String reason();
    String latestRow(){return "catchup."+provider()+".latest";}
    String serialsRow(){return "catchup."+provider()+".serials";}

    @Override public long defaultTtlMs(){return 60L*60_000L;}
    @Override public boolean checkOnForeground(){return ready();}
    @Override public boolean ready(){return feed!=null;}
    @Override public String notReadyReason(){return reason();}

    @Override public List<Batch> fetch(CatalogHttp http)throws Exception{
        if(feed==null)throw new IllegalStateException(reason());
        List<CatchupEpisode> previous=new ArrayList<>();Set<String> known=new HashSet<>();
        for(CatalogItem c:stored.apply(latestRow()))if(c.episode!=null){previous.add(c.episode);known.add(c.episode.episodeId);}
        List<CatchupEpisode> all=new ArrayList<>(feed.episodes(http,known));all.addAll(previous);
        return batches(all,System.currentTimeMillis());
    }

    /**
     * Latest Episodes: every episode, newest air date first. Shows: one tile per show, carrying its
     * newest episode. Episodes without an air date, from the future, older than {@link #RETAIN_MS},
     * from another provider or repeated (same show and episode) are dropped; the first copy wins.
     */
    List<Batch> batches(List<CatchupEpisode> episodes,long now){
        List<CatchupEpisode> sorted=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(CatchupEpisode e:episodes){
            if(e==null||!provider().equals(e.providerId)||e.airDate<=0||e.airDate>now+60_000L||now-e.airDate>RETAIN_MS||e.episodeId.isEmpty()||!seen.add(e.episodeId))continue;
            sorted.add(e);
        }
        sorted.sort(NEWEST);
        List<CatalogItem> latest=new ArrayList<>(),shows=new ArrayList<>();Map<String,Integer> counts=new HashMap<>();
        for(CatchupEpisode e:sorted)counts.merge(seriesKey(e),1,Integer::sum);
        Set<String> series=new HashSet<>();
        for(CatchupEpisode e:sorted){
            if(latest.size()>=MAX_EPISODES)break;
            latest.add(item(e));
            String key=seriesKey(e);
            if(!key.isEmpty()&&series.add(key))shows.add(show(e,counts.get(key)));
        }
        return Arrays.asList(new Batch(latestRow(),latest,false),new Batch(serialsRow(),shows,false));
    }
    /** Newest air date first; same day: later episode number, then later post. */
    static final Comparator<CatchupEpisode> NEWEST=(a,b)->{
        int c=Long.compare(b.airDate,a.airDate);if(c!=0)return c;
        c=Integer.compare(b.episodeNumber,a.episodeNumber);if(c!=0)return c;
        return Long.compare(b.availableFrom,a.availableFrom);
    };
    private String seriesKey(CatchupEpisode e){return e.seriesId.isEmpty()?CatchupEpisode.seriesId(provider(),e.seriesTitle):e.seriesId;}
    CatalogItem item(CatchupEpisode e){
        if(e.language.isEmpty())e.language=language();
        CatalogItem c=CatchupSource.item(e);c.title.provider=provider();c.title.providerName=providerName();
        c.episodeLabel=CatalogViews.join(e.episodeNumber>0?"Episode "+e.episodeNumber:"",day(e.airDate),e.channelTitle);
        return c;
    }
    /** The sites give an Indian broadcast date, not a time. Stored labels use the plain date, e.g. "Tue 3 Nov". */
    static String day(long airDate){
        return airDate<=0?"":java.time.Instant.ofEpochMilli(airDate).atZone(SerialFeedParser.INDIA).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM",Locale.ENGLISH));
    }
    /** On screen: "Today", "Yesterday" or the date, judged in India. */
    static String aired(long airDate,long now){
        if(airDate<=0)return "";
        java.time.LocalDate d=java.time.Instant.ofEpochMilli(airDate).atZone(SerialFeedParser.INDIA).toLocalDate(),today=java.time.Instant.ofEpochMilli(now).atZone(SerialFeedParser.INDIA).toLocalDate();
        if(d.equals(today))return "Today";
        if(d.equals(today.minusDays(1)))return "Yesterday";
        return d.format(java.time.format.DateTimeFormatter.ofPattern(d.getYear()==today.getYear()?"EEE d MMM":"d MMM yyyy",Locale.ENGLISH));
    }
    /** A show tile: the show's name, language and broadcaster, with its newest episode attached. */
    CatalogItem show(CatchupEpisode latest,int episodes){
        CatalogItem c=item(latest);
        Library.Title t=new Library.Title(seriesKey(latest),latest.seriesTitle,
            CatalogViews.join(latest.channelTitle,latest.language)+" · "+episodes+(episodes==1?" episode":" episodes")+" listed by "+providerName()+".","","",c.title.year,latest.genre);
        t.provider=provider();t.providerName=providerName();t.type=SHOW_TYPE;t.language=latest.language;t.group=latest.genre;t.released=c.title.released;
        CatalogItem s=new CatalogItem(t);s.kind=CatalogItem.CATCHUP;s.playable=false;s.releaseDate=c.releaseDate;s.sourceUpdatedAt=latest.airDate;
        s.episodeLabel=CatalogViews.join(latest.episodeNumber>0?"Latest: Episode "+latest.episodeNumber:"Latest",day(latest.airDate),latest.channelTitle);
        s.accessType=latest.accessType;s.playbackType=latest.playbackType;s.playbackReference=latest.playbackReference;s.episode=latest;
        s.audio.addAll(c.audio);s.sources.addAll(c.sources);
        return s;
    }
    static boolean showTile(CatalogItem c){return c!=null&&SHOW_TYPE.equals(c.title.type);}
    /** Every stored episode of one show, newest first. */
    static List<CatalogItem> episodesOf(CatalogItem show,List<CatalogItem> latestRow){
        List<CatalogItem> out=new ArrayList<>();if(show==null)return out;
        for(CatalogItem c:latestRow)if(c.episode!=null&&show.title.provider.equals(c.title.provider)&&show.title.id.equals(c.episode.seriesId))out.add(c);
        out.sort((a,b)->NEWEST.compare(a.episode,b.episode));
        return out;
    }
    /** Recently Aired across both providers: their Latest Episodes rows merged, last seven days, newest first. */
    static List<CatalogItem> recent(List<List<CatalogItem>> latestRows,long now,int limit){
        List<CatalogItem> out=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(List<CatalogItem> row:latestRows)for(CatalogItem c:row)if(c.episode!=null&&now-c.episode.airDate<=RECENT_MS&&seen.add(c.key()))out.add(c);
        out.sort((a,b)->NEWEST.compare(a.episode,b.episode));
        return out.size()>limit?new ArrayList<>(out.subList(0,limit)):out;
    }
    /** Continue Watching keeps only episodes these providers supplied. */
    static List<CatalogItem> mine(List<CatalogItem> history,int limit){
        List<CatalogItem> out=new ArrayList<>();
        for(CatalogItem c:history)if((TamilTvSerialProvider.PROVIDER.equals(c.title.provider)||TeluguStreamProvider.PROVIDER.equals(c.title.provider))&&!showTile(c)&&out.size()<limit)out.add(c);
        return out;
    }
}
