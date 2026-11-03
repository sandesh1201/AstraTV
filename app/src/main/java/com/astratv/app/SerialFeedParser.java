package com.astratv.app;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Episode metadata from a catch-up site's public WordPress RSS feed. Post titles look like
 * "Malli 03-11-2026 | Episode 908 | Sun Tv Serial", and the post categories name the show and
 * the broadcaster. Only the show, episode number, air date and broadcaster are kept: the post
 * link, its body, its images and any video embed are dropped here and never stored.
 */
final class SerialFeedParser {
    static final int MAX_ITEMS=60;
    static final ZoneId INDIA=ZoneId.of("Asia/Kolkata");
    private static final Pattern ITEM=Pattern.compile("<item\\b[^>]*>([\\s\\S]*?)</item>");
    private static final Pattern CATEGORY=Pattern.compile("<category\\b[^>]*>([\\s\\S]*?)</category>");
    private static final Pattern DATE=Pattern.compile("(\\d{1,2})[-./](\\d{1,2})[-./](\\d{4})");
    private static final Pattern KIND=Pattern.compile("(?i)\\b(serials?|shows?)\\s*$");
    private static final Pattern ENTITY=Pattern.compile("&(#x?[0-9a-fA-F]+|amp|lt|gt|quot|apos|nbsp);");

    private SerialFeedParser(){}

    static List<CatchupEpisode> parse(String xml,String provider,String providerName,String siteLanguage){
        List<CatchupEpisode> out=new ArrayList<>();if(xml==null)return out;
        Matcher m=ITEM.matcher(xml);
        while(m.find()&&out.size()<MAX_ITEMS){
            String item=m.group(1);List<String> categories=new ArrayList<>();
            Matcher c=CATEGORY.matcher(item);while(c.find()){String v=text(c.group(1));if(!v.isEmpty())categories.add(v);}
            CatchupEpisode e=episode(tag(item,"title"),categories,pubDate(tag(item,"pubDate")),provider,providerName,siteLanguage);
            if(e!=null)out.add(e);
        }
        return out;
    }

    /** One episode from a post title and its categories, or null when the post is not a dated show episode. */
    static CatchupEpisode episode(String title,List<String> categories,long published,String provider,String providerName,String siteLanguage){
        if(title==null||title.trim().isEmpty())return null;
        String[] parts=title.trim().split("\\s*\\|\\s*");
        String head=parts[0];Matcher d=DATE.matcher(head);long aired=0;String show=head;
        if(d.find()){aired=date(d.group(1),d.group(2),d.group(3));show=head.substring(0,d.start());}
        show=clean(show);
        String label=parts.length>1?parts[parts.length-1]:"";
        CatchupBroadcasters.Broadcaster b=CatchupBroadcasters.find(label);
        if(b==null)for(String cat:categories)if(KIND.matcher(cat).find()&&(b=CatchupBroadcasters.find(cat))!=null)break;
        if(show.isEmpty())for(String cat:categories)if(!KIND.matcher(cat).find()&&!CatchupBroadcasters.named(cat)){show=clean(cat);break;}
        if(show.isEmpty())return null;
        if(aired<=0)aired=published;
        if(aired<=0)return null;
        CatchupEpisode e=new CatchupEpisode();
        e.providerId=provider;e.seriesTitle=show;e.episodeTitle=show;
        e.episodeNumber=CatchupEpisode.episodeNumber(title);e.seasonNumber=CatchupEpisode.seasonNumber(show);
        e.airDate=aired;e.availableFrom=published>0?published:aired;
        e.genre=label.toLowerCase(Locale.ROOT).contains("show")?"TV show":"Serial";
        e.channelTitle=b==null?"":b.name;e.language=b==null?siteLanguage:b.language;
        e.seriesId=CatchupEpisode.seriesId(provider,show);
        e.episodeId=e.seriesId.isEmpty()?"":e.seriesId+"/"+(e.episodeNumber>0?"e"+e.episodeNumber:"d"+Instant.ofEpochMilli(aired).atZone(INDIA).toLocalDate());
        if(b!=null&&b.watchable()){e.accessType=CatalogItem.SUBSCRIPTION;e.playbackType=CatalogItem.APP_HANDOFF;e.playbackReference=b.app;}
        else{e.accessType=CatalogItem.UNAVAILABLE;e.playbackType="";e.playbackReference="";}
        e.description=describe(e,providerName,b);
        return e;
    }
    static String describe(CatchupEpisode e,String providerName,CatchupBroadcasters.Broadcaster b){
        String what=(e.episodeNumber>0?"Episode "+e.episodeNumber+" of ":"")+e.seriesTitle;
        String aired=b==null?"":", aired on "+b.name;
        String listing=" Listed by "+providerName+", which re-posts broadcaster episodes without a licence, so Saandio does not play its videos.";
        if(b!=null&&b.watchable())return what+aired+"."+listing+" "+b.name+" streams its serials in "+OfficialAppHandoff.appName(b.app)+", its official app.";
        return what+aired+"."+listing+(b==null?" The broadcaster could not be identified, so no official app is offered.":" "+b.name+" has no official streaming app Saandio can open.");
    }

    private static String clean(String s){
        String v=s==null?"":s.replaceAll("\\s+"," ").trim();
        v=v.replaceAll("^[\\s|:–—-]+|[\\s|:–—-]+$","").trim();
        return v;
    }
    private static long date(String day,String month,String year){
        try{return LocalDate.of(Integer.parseInt(year),Integer.parseInt(month),Integer.parseInt(day)).atStartOfDay(INDIA).toInstant().toEpochMilli();}
        catch(RuntimeException e){return 0;}
    }
    static long pubDate(String value){
        try{return ZonedDateTime.parse(value.trim(),DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();}
        catch(RuntimeException e){return 0;}
    }
    private static String tag(String xml,String name){
        Matcher m=Pattern.compile("<"+name+"\\b[^>]*>([\\s\\S]*?)</"+name+">").matcher(xml);
        return m.find()?text(m.group(1)):"";
    }
    /** CDATA unwrapped, HTML entities decoded, tags removed. */
    static String text(String raw){
        String v=raw==null?"":raw.replace("<![CDATA[","").replace("]]>","");
        v=v.replaceAll("<[^>]+>"," ");
        Matcher m=ENTITY.matcher(v);StringBuffer sb=new StringBuffer();
        while(m.find()){
            String e=m.group(1),r;
            switch(e){
                case "amp":r="&";break;case "lt":r="<";break;case "gt":r=">";break;case "quot":r="\"";break;case "apos":r="'";break;case "nbsp":r=" ";break;
                default:
                    try{int cp=e.startsWith("#x")||e.startsWith("#X")?Integer.parseInt(e.substring(2),16):Integer.parseInt(e.substring(1));r=new String(Character.toChars(cp));}
                    catch(RuntimeException x){r=m.group();}
            }
            m.appendReplacement(sb,Matcher.quoteReplacement(r));
        }
        m.appendTail(sb);
        return sb.toString().replaceAll("\\s+"," ").trim();
    }
}
