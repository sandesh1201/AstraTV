package com.astratv.app;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;
import java.io.InputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Streaming XMLTV reader. Keeps only programmes that overlap a time window so a large guide does
 * not fill memory. Times carry their own UTC offset ("20261103180000 +0530"); a time without an
 * offset is read as UTC. External entities are disabled.
 */
final class Xmltv {
    static final class Programme{
        final String channel,title,description,icon;final long start,stop;
        Programme(String channel,String title,String description,String icon,long start,long stop){this.channel=channel;this.title=title;this.description=description;this.icon=icon;this.start=start;this.stop=stop;}
        boolean airing(long now){return start<=now&&now<stop;}
        /** Percent of the programme elapsed at {@code now}, 0..100. */
        int progress(long now){if(stop<=start)return 0;return (int)Math.max(0,Math.min(100,(now-start)*100/(stop-start)));}
    }
    static final class Guide{
        /** XMLTV channel id to display names. */
        final Map<String,List<String>> channels=new LinkedHashMap<>();
        final Map<String,String> icons=new HashMap<>();
        final Map<String,List<Programme>> programmes=new HashMap<>();
        int programmeCount(){int n=0;for(List<Programme> l:programmes.values())n+=l.size();return n;}
        /** The programme airing now and the one after it; either may be null. */
        Programme[] nowNext(String channel,long now){
            List<Programme> l=programmes.get(channel);Programme current=null,next=null;if(l==null)return new Programme[]{null,null};
            for(Programme p:l){if(p.airing(now))current=p;else if(p.start>=now&&(next==null||p.start<next.start))next=p;}
            if(current!=null){next=null;for(Programme p:l)if(p.start>=current.stop&&(next==null||p.start<next.start))next=p;}
            return new Programme[]{current,next};
        }
        void sort(){for(List<Programme> l:programmes.values())l.sort(Comparator.comparingLong(p->p.start));}
    }
    static final int MAX_PROGRAMMES=200_000;
    private static final DateTimeFormatter WITH_ZONE=DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z"),LOCAL=DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private Xmltv(){}

    static long time(String s){
        if(s==null)return 0;String v=s.trim();
        try{
            if(v.length()>14&&v.indexOf(' ')==14)return ZonedDateTime.parse(v.substring(0,Math.min(v.length(),20)),WITH_ZONE).toInstant().toEpochMilli();
            return LocalDateTime.parse(v.substring(0,Math.min(14,v.length())),LOCAL).toInstant(ZoneOffset.UTC).toEpochMilli();
        }catch(DateTimeParseException|IndexOutOfBoundsException e){return 0;}
    }
    /** @param wanted XMLTV channel ids to keep programmes for; null keeps every channel. */
    static Guide parse(InputStream in,long windowStart,long windowEnd,Set<String> wanted)throws Exception{
        SAXParserFactory f=SAXParserFactory.newInstance();f.setNamespaceAware(false);
        for(String feature:new String[]{"http://xml.org/sax/features/external-general-entities","http://xml.org/sax/features/external-parameter-entities","http://apache.org/xml/features/nonvalidating/load-external-dtd"})
            try{f.setFeature(feature,false);}catch(Exception ignored){/* not supported by this parser */}
        SAXParser p=f.newSAXParser();Guide g=new Guide();
        p.parse(new InputSource(in),new DefaultHandler(){
            String channel,progChannel,title,desc,icon;long start,stop;StringBuilder text;boolean inProgramme;int kept;
            @Override public void startElement(String uri,String local,String q,Attributes a){
                switch(q){
                    case "channel":channel=a.getValue("id");if(channel!=null)g.channels.computeIfAbsent(channel,k->new ArrayList<>());break;
                    case "programme":inProgramme=true;progChannel=a.getValue("channel");start=time(a.getValue("start"));stop=time(a.getValue("stop"));title="";desc="";icon="";break;
                    case "display-name":case "title":case "desc":text=new StringBuilder();break;
                    case "icon":{String src=a.getValue("src");if(src!=null&&Library.https(src)){if(inProgramme)icon=src;else if(channel!=null)g.icons.put(channel,src);}break;}
                    default:break;
                }
            }
            @Override public void characters(char[] ch,int s,int l){if(text!=null&&text.length()<2000)text.append(ch,s,Math.min(l,2000-text.length()));}
            @Override public void endElement(String uri,String local,String q){
                switch(q){
                    case "display-name":if(!inProgramme&&channel!=null&&text!=null){String n=text.toString().trim();if(!n.isEmpty())g.channels.get(channel).add(n);}text=null;break;
                    case "title":if(inProgramme&&text!=null&&title.isEmpty())title=text.toString().trim();text=null;break;
                    case "desc":if(inProgramme&&text!=null&&desc.isEmpty())desc=text.toString().trim();text=null;break;
                    case "channel":channel=null;break;
                    case "programme":
                        inProgramme=false;
                        if(progChannel!=null&&start>0&&stop>start&&stop>windowStart&&start<windowEnd&&!title.isEmpty()&&(wanted==null||wanted.contains(progChannel))&&kept<MAX_PROGRAMMES){
                            g.programmes.computeIfAbsent(progChannel,k->new ArrayList<>()).add(new Programme(progChannel,title,desc,icon,start,stop));kept++;
                        }
                        break;
                    default:break;
                }
            }
        });
        g.sort();return g;
    }
}
