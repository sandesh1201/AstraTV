package com.astratv.app;

import org.json.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/**
 * Match schedules from TheSportsDB. Schedule data only: Saandio holds no sports broadcast rights,
 * so events are never playable, and an event is "live" only when the API reports an in-play status.
 */
final class SportsSource implements CatalogSource {
    static final String ID="sports.thesportsdb",ROW="sports.events";
    static final String[] SPORTS={"Cricket","Soccer","Tennis","Basketball","Motorsport","Rugby"};
    static final String LIVE="live",UPCOMING="upcoming",FINISHED="finished",POSTPONED="postponed",CANCELLED="cancelled",UNCONFIRMED="unconfirmed";
    private static final Set<String> LIVE_CODES=new HashSet<>(Arrays.asList("1h","2h","ht","et","bt","live","in progress","in play","q1","q2","q3","q4","ot","1st half","2nd half","halftime","innings break","lunch","tea","drinks"));
    private static final Set<String> FINISHED_CODES=new HashSet<>(Arrays.asList("ft","aet","pen","aot","match finished","finished","ended","final","after over time","after penalties","result"));
    private static final Set<String> UPCOMING_CODES=new HashSet<>(Arrays.asList("ns","not started","scheduled","tbd","time to be defined",""));
    private final Supplier<String> key;private final Supplier<Clock> clock;
    SportsSource(Supplier<String> key,Supplier<Clock> clock){this.key=key;this.clock=clock;}

    @Override public String id(){return ID;}
    @Override public String label(){return "Sports schedules · TheSportsDB";}
    @Override public long defaultTtlMs(){return 10L*60_000L;}
    @Override public boolean checkOnForeground(){return true;}
    @Override public boolean ready(){return !key.get().isEmpty();}
    @Override public String notReadyReason(){return "Add a TheSportsDB key in Settings → Metadata API keys.";}

    @Override public List<Batch> fetch(CatalogHttp http)throws Exception{
        Clock c=clock.get();LocalDate today=LocalDate.now(c.withZone(ZoneOffset.UTC));
        Map<String,CatalogItem> events=new LinkedHashMap<>();boolean notModified=true;Exception first=null;int ok=0;
        for(LocalDate day:days(today,key.get()))for(String sport:SPORTS){
            String url="https://www.thesportsdb.com/api/v1/json/"+key.get()+"/eventsday.php?d="+day+"&s="+sport;String body;
            try{CatalogHttp.Result r=http.get(url);notModified&=r.notModified;body=r.body;ok++;}
            catch(Exception e){
                if(first==null)first=e;
                CatalogHttp.Entry saved=http.cached(url);if(saved==null||saved.body.isEmpty())continue;
                body=saved.body;
            }
            try{for(CatalogItem e:parse(new JSONObject(body),c.millis()))events.putIfAbsent(e.key(),e);}catch(org.json.JSONException e){if(first==null)first=e;}
        }
        if(ok==0&&first!=null)throw first;
        return Collections.singletonList(new Batch(ROW,order(new ArrayList<>(events.values()),c.millis()),notModified));
    }

    /**
     * The shared free key allows only a few requests per minute, so it covers today and tomorrow;
     * a personal key also fetches yesterday for late results.
     */
    static List<LocalDate> days(LocalDate today,String key){
        List<LocalDate> out=new ArrayList<>(Arrays.asList(today,today.plusDays(1)));
        if(!ApiKeys.SPORTSDB_FREE.equals(key))out.add(today.minusDays(1));
        return out;
    }
    static List<CatalogItem> parse(JSONObject body,long now){
        List<CatalogItem> out=new ArrayList<>();JSONArray a=body.optJSONArray("events");if(a==null)return out;
        for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i);if(o==null)continue;
            String id=str(o,"idEvent"),name=str(o,"strEvent");if(id.isEmpty()||name.isEmpty())continue;
            long start=start(o);
            String league=str(o,"strLeague"),sport=str(o,"strSport"),venue=str(o,"strVenue"),channel=str(o,"strTVStation");
            Library.Title t=new Library.Title(id,name,description(league,venue,channel),"","",start>0?String.valueOf(Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).getYear()):"",sport);
            t.provider="thesportsdb";t.providerName="TheSportsDB";t.type="event";t.group=channel;
            String art=str(o,"strThumb");if(art.isEmpty())art=str(o,"strPoster");if(art.isEmpty())art=str(o,"strBanner");
            if(Library.https(art)){t.poster=art;t.background=art;}
            CatalogItem c=new CatalogItem(t);c.kind=CatalogItem.EVENT;c.sport=sport;c.league=league;c.startsAt=start;c.sourceUpdatedAt=start;c.playable=false;
            c.releaseDate=str(o,"dateEvent");c.status=status(str(o,"strStatus"),str(o,"strPostponed"),start,now);
            c.sources.add("TheSportsDB");
            out.add(c);
        }
        return out;
    }
    /** Conservative mapping: unknown in-progress codes become "unconfirmed", never "live". */
    static String status(String raw,String postponed,long start,long now){
        String s=raw==null?"":raw.trim().toLowerCase(Locale.ROOT);
        if("yes".equalsIgnoreCase(postponed)||s.equals("postponed")||s.equals("pst"))return POSTPONED;
        if(s.startsWith("cancel")||s.equals("canc")||s.startsWith("abandon")||s.equals("abd"))return CANCELLED;
        if(FINISHED_CODES.contains(s)||s.contains("won by")||s.contains("match drawn"))return FINISHED;
        if(LIVE_CODES.contains(s))return LIVE;
        if(UPCOMING_CODES.contains(s))return start>now||start==0?UPCOMING:UNCONFIRMED;
        return UNCONFIRMED;
    }
    static long start(JSONObject o){
        String ts=str(o,"strTimestamp");
        try{if(!ts.isEmpty())return (ts.endsWith("Z")||ts.matches(".*[+-]\\d\\d:?\\d\\d$")?OffsetDateTime.parse(ts.replaceAll("([+-]\\d\\d)(\\d\\d)$","$1:$2")).toInstant():LocalDateTime.parse(ts).toInstant(ZoneOffset.UTC)).toEpochMilli();}catch(Exception ignored){/* fall back to date and time */}
        String date=str(o,"dateEvent"),time=str(o,"strTime");
        try{if(date.isEmpty())return 0;LocalTime lt=time.isEmpty()?LocalTime.MIDNIGHT:LocalTime.parse(time.length()>8?time.substring(0,8):time);return LocalDateTime.of(LocalDate.parse(date),lt).toInstant(ZoneOffset.UTC).toEpochMilli();}catch(Exception e){return 0;}
    }
    static final long LIVE_TRUST_MS=30L*60_000L;
    /**
     * Statuses age: once the last successful sync is older than {@link #LIVE_TRUST_MS}, "live" is no longer
     * claimed, and an "upcoming" match whose start time has passed is no longer known to be upcoming.
     */
    static List<CatalogItem> reconcile(List<CatalogItem> events,long syncedAt,long now){
        boolean trusted=syncedAt>0&&now-syncedAt<=LIVE_TRUST_MS;
        for(CatalogItem e:events){
            if(LIVE.equals(e.status)&&!trusted)e.status=UNCONFIRMED;
            else if(UPCOMING.equals(e.status)&&e.startsAt>0&&e.startsAt<=now)e.status=UNCONFIRMED;
        }
        return order(events,now);
    }
    /** Live first, then upcoming by start time, then recent results. Results older than 12 hours drop out. */
    static List<CatalogItem> order(List<CatalogItem> events,long now){
        List<CatalogItem> out=new ArrayList<>();
        for(CatalogItem e:events)if(!(FINISHED.equals(e.status)||CANCELLED.equals(e.status))||e.startsAt>now-12*3_600_000L)out.add(e);
        out.sort(Comparator.comparingInt((CatalogItem e)->rank(e.status)).thenComparingLong(e->FINISHED.equals(e.status)?-e.startsAt:e.startsAt));
        return out;
    }
    private static int rank(String status){switch(status){case LIVE:return 0;case UPCOMING:return 1;case UNCONFIRMED:return 2;case POSTPONED:return 3;default:return 4;}}
    static String statusLabel(CatalogItem e){
        switch(e.status){case LIVE:return "Live now (per schedule)";case UPCOMING:return "Upcoming";case FINISHED:return "Finished";case POSTPONED:return "Postponed";case CANCELLED:return "Cancelled";default:return "Status not confirmed";}
    }
    private static String description(String league,String venue,String channel){
        List<String> parts=new ArrayList<>();if(!league.isEmpty())parts.add(league);if(!venue.isEmpty())parts.add(venue);
        parts.add(channel.isEmpty()?"Broadcaster not listed by the schedule source":"Broadcaster: "+channel);
        parts.add("Schedule only · not playable in Saandio");
        return String.join(" · ",parts);
    }
    private static String str(JSONObject o,String key){return o==null||o.isNull(key)?"":o.optString(key,"").trim();}
}
