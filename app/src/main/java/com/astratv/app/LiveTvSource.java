package com.astratv.app;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Indian channels from the public iptv-org directory (the same source as the TV channels add-on).
 * Saandio has not verified licensing for these streams, so the UI says so, and a channel only counts
 * as working after a real playback probe succeeds.
 */
final class LiveTvSource implements CatalogSource {
    static final String ID="live.iptv_org",ROW="live.channels";
    static final String ATTRIBUTION="Public directory: iptv-org · streams are not verified by Saandio for licensing";
    private final BooleanSupplier enabled;
    LiveTvSource(BooleanSupplier enabled){this.enabled=enabled;}

    @Override public String id(){return ID;}
    @Override public String label(){return "Live TV · iptv-org directory";}
    @Override public long defaultTtlMs(){return 6L*60*60_000L;}
    @Override public boolean checkOnForeground(){return false;}
    @Override public boolean ready(){return enabled.getAsBoolean();}
    @Override public String notReadyReason(){return "The TV channels add-on is turned off in Settings → Add-ons.";}

    @Override public List<Batch> fetch(CatalogHttp http)throws Exception{
        AtomicBoolean changed=new AtomicBoolean(false);
        IndianTvProvider.Directory d=IndianTvProvider.load(url->{CatalogHttp.Result r=http.get(url);if(!r.notModified)changed.set(true);return r.body;});
        return Collections.singletonList(new Batch(ROW,items(d.channels),!changed.get()));
    }
    static List<CatalogItem> items(List<Library.Title> channels){
        List<CatalogItem> out=new ArrayList<>();
        for(Library.Title t:channels){
            if(!Library.https(t.url))continue;
            CatalogItem c=new CatalogItem(t);c.kind=CatalogItem.CHANNEL;c.playable=false;
            if(!t.language.isEmpty())c.audio.add(t.language);c.sources.add("iptv-org");
            out.add(c);
        }
        out.sort(Comparator.comparing((CatalogItem c)->c.title.language).thenComparing(c->c.title.name,String.CASE_INSENSITIVE_ORDER));
        return out;
    }
}
