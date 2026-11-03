package com.astratv.app;

import java.util.*;

/**
 * The 37 Sun TV Network channels from the official list (https://www.sun.in/television.aspx).
 * Saandio has no authorized stream for any of them: Sun NXT, Sun TV Network's own app, carries
 * them with a subscription, so each entry opens Sun NXT (app launch only; Sun NXT publishes no
 * per-channel deep link) and is marked SUBSCRIPTION_REQUIRED. Sun News also runs an official
 * YouTube channel, offered as a free handoff whose live status is not verified by Saandio.
 * {@link #iptvIds} are iptv-org database ids (including the 2026 "Sun Gemini/Udaya/Surya" renames)
 * used only to recognize channels already in the Live TV directory.
 */
final class SunNetworkChannels implements AdditionalChannelProvider {
    static final String PROVIDER="official.sun",PROVIDER_NAME="Sun NXT (Sun TV Network)";
    static final String SUN_NEWS_YOUTUBE="UCYlh4lH762HvHt6mmiecyWQ";
    static final String SOURCE_URL="https://www.sun.in/television.aspx";

    static final class Entry{
        final String name,language,group;final boolean hd;final List<String> iptvIds;
        Entry(String name,String language,String group,boolean hd,String... iptvIds){this.name=name;this.language=language;this.group=group;this.hd=hd;this.iptvIds=Arrays.asList(iptvIds);}
        String id(){return "sun."+name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-");}
    }
    static final List<Entry> ENTRIES=Collections.unmodifiableList(Arrays.asList(
        new Entry("Sun TV","Tamil","Entertainment",false,"SunTV.in"),
        new Entry("Sun TV HD","Tamil","Entertainment",true,"SunTV.in"),
        new Entry("KTV","Tamil","Movies",false,"KTV.in"),
        new Entry("KTV HD","Tamil","Movies",true,"KTV.in"),
        new Entry("Sun Music","Tamil","Music",false,"SunMusic.in"),
        new Entry("Sun Music HD","Tamil","Music",true,"SunMusic.in"),
        new Entry("Sun News","Tamil","News",false,"SunNews.in"),
        new Entry("Adithya TV","Tamil","Comedy",false,"AdithyaTV.in"),
        new Entry("Chutti TV","Tamil","Kids",false,"ChuttiTV.in"),
        new Entry("Sun Life","Tamil","Entertainment",false,"SunLife.in"),
        new Entry("Gemini TV","Telugu","Entertainment",false,"GeminiTV.in","SunGemini.in"),
        new Entry("Gemini TV HD","Telugu","Entertainment",true,"GeminiTV.in","SunGemini.in"),
        new Entry("Gemini Movies","Telugu","Movies",false,"GeminiMovies.in","SunGeminiMovies.in"),
        new Entry("Gemini Movies HD","Telugu","Movies",true,"GeminiMovies.in","SunGeminiMovies.in"),
        new Entry("Gemini Music","Telugu","Music",false,"GeminiMusic.in","SunGeminiMusic.in"),
        new Entry("Gemini Music HD","Telugu","Music",true,"GeminiMusic.in","SunGeminiMusic.in"),
        new Entry("Gemini Comedy","Telugu","Comedy",false,"GeminiComedy.in","SunGeminiComedy.in"),
        new Entry("Kushi TV","Telugu","Kids",false,"KushiTV.in"),
        new Entry("Gemini Life","Telugu","Entertainment",false,"GeminiLife.in","SunGeminiLife.in"),
        new Entry("Udaya TV","Kannada","Entertainment",false,"SunUdaya.in"),
        new Entry("Udaya TV HD","Kannada","Entertainment",true,"SunUdaya.in"),
        new Entry("Udaya Movies","Kannada","Movies",false,"SunUdayaMovies.in"),
        new Entry("Udaya Music","Kannada","Music",false,"SunUdayaMusic.in"),
        new Entry("Udaya Comedy","Kannada","Comedy",false,"SunUdayaComedy.in"),
        new Entry("Chintu TV","Kannada","Kids",false,"ChintuTV.in"),
        new Entry("Surya TV","Malayalam","Entertainment",false,"SunSurya.in"),
        new Entry("Surya TV HD","Malayalam","Entertainment",true,"SunSurya.in"),
        new Entry("Surya Movies","Malayalam","Movies",false,"SunSuryaMovies.in"),
        new Entry("Surya Music","Malayalam","Music",false,"SunSuryaMusic.in"),
        new Entry("Surya Comedy","Malayalam","Comedy",false,"SunSuryaComedy.in"),
        new Entry("Kochu TV","Malayalam","Kids",false,"KochuTV.in"),
        new Entry("Sun Bangla","Bengali","Entertainment",false,"SunBangla.in"),
        new Entry("Sun Bangla HD","Bengali","Entertainment",true,"SunBangla.in"),
        new Entry("Sun Marathi","Marathi","Entertainment",false,"SunMarathi.in"),
        new Entry("Sun Marathi HD","Marathi","Entertainment",true,"SunMarathi.in"),
        new Entry("Sun Neo","Hindi","Entertainment",false,"SunNeo.in"),
        new Entry("Sun Neo HD","Hindi","Entertainment",true,"SunNeo.in")
    ));

    @Override public String id(){return PROVIDER;}
    @Override public String label(){return "Sun Network · official app handoff";}
    @Override public List<CatalogItem> channels(){
        List<CatalogItem> out=new ArrayList<>();
        for(Entry e:ENTRIES)out.add(item(e));
        return out;
    }
    static CatalogItem item(Entry e){
        boolean news=e.name.equals("Sun News");
        Library.Title t=new Library.Title(e.id(),e.name,
            e.name+" is a Sun TV Network channel. It streams in Sun NXT, Sun TV Network's official app, which requires a subscription."
                +(news?" Sun News also streams on its official YouTube channel.":"")+" Saandio does not stream this channel itself.",
            "","",e.hd?"HD":"",e.group);
        t.provider=PROVIDER;t.providerName=PROVIDER_NAME;t.type="tv";t.language=e.language;t.group=e.group;t.pageUrl=SOURCE_URL;
        CatalogItem c=new CatalogItem(t);c.kind=CatalogItem.CHANNEL;c.playable=false;
        if(!e.language.isEmpty())c.audio.add(e.language);c.sources.add("sun.in");
        if(news){c.accessType=CatalogItem.FREE;c.playbackType=CatalogItem.YOUTUBE_HANDOFF;c.playbackReference=SUN_NEWS_YOUTUBE;c.channelState=ChannelState.UNKNOWN.name();}
        else{c.accessType=CatalogItem.SUBSCRIPTION;c.playbackType=CatalogItem.APP_HANDOFF;c.playbackReference=OfficialAppHandoff.SUN_NXT;c.channelState=ChannelState.SUBSCRIPTION_REQUIRED.name();}
        return c;
    }
    static Entry entry(String id){for(Entry e:ENTRIES)if(e.id().equals(id))return e;return null;}
    private static final Set<String> IPTV_IDS;
    static{Set<String> ids=new HashSet<>();for(Entry e:ENTRIES)for(String id:e.iptvIds)ids.add(id.toLowerCase(Locale.ROOT));IPTV_IDS=Collections.unmodifiableSet(ids);}
    /**
     * A directory channel that restreams a Sun TV Network channel. Sun has asserted its rights over
     * these with iptv-org and Saandio holds no licence, so they are left out of Live TV; the channel
     * is offered through Sun NXT instead.
     */
    static boolean rightsAsserted(Library.Title t){
        if(t==null||!"news".equals(t.provider)||t.id==null)return false;
        int cut=t.id.indexOf('~');String tvg=(cut>0?t.id.substring(0,cut):t.id).split("@")[0];
        return IPTV_IDS.contains(tvg.toLowerCase(Locale.ROOT));
    }
}
