package com.astratv.app;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Collapses copies of the same film or series across sources and dubbed editions. Two titles with
 * different TMDB ids are never merged; without ids, the normalised base name and year must agree.
 * Audio languages and source names from every copy are kept on the surviving entry.
 */
final class CatalogDeduplication {
    private CatalogDeduplication(){}
    private static final String LANGS="telugu|tamil|hindi|malayalam|kannada|english|bengali|marathi|punjabi";
    private static final Pattern DUBBED=Pattern.compile("\\s*[-–(\\[]?\\s*(?:\\b(?:"+LANGS+")\\s+)?\\b(?:dubbed|dub)\\b\\s*(?:version)?\\s*[)\\]]?\\s*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern LANG_TAG=Pattern.compile("\\s*[-–]?\\s*[(\\[]\\s*(?:"+LANGS+")(?:\\s+version)?\\s*[)\\]]\\s*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern LANG_WORD=Pattern.compile("(?i)("+LANGS+")");

    /** Strips trailing dub/edition markers: "Pushpa 2 (Hindi)", "Leo - Telugu Dubbed", "RRR [Tamil]". */
    static String baseName(String name){
        if(name==null)return "";
        String n=name.trim();
        for(int i=0;i<2;i++){n=DUBBED.matcher(n).replaceAll("");n=LANG_TAG.matcher(n).replaceAll("");}
        return n.trim();
    }
    /** Language named in a dub marker, e.g. "Hindi" for "Pushpa 2 (Hindi Dubbed)". Empty when none. */
    static String dubLanguage(String name){
        if(name==null)return "";
        String n=name.trim(),tail=n.substring(Math.min(n.length(),baseName(n).length()));
        java.util.regex.Matcher m=LANG_WORD.matcher(tail);
        if(!m.find())return "";
        String l=m.group(1).toLowerCase(Locale.ROOT);return Character.toUpperCase(l.charAt(0))+l.substring(1);
    }
    static String nameKey(CatalogItem c){
        if(CatalogItem.CHANNEL.equals(c.kind)||CatalogItem.EVENT.equals(c.kind))return null;
        int y=c.year();String n=MetadataMatcher.normalize(baseName(c.title.name));
        if(y<=0||n.isEmpty())return null;
        return (CatalogItem.SERIES.equals(c.kind)?"series":"movie")+"|"+n+"|"+y;
    }
    static String idKey(CatalogItem c){return c.tmdbId>0?(CatalogItem.SERIES.equals(c.kind)?"series":"movie")+"#"+c.tmdbId:null;}

    static List<CatalogItem> merge(List<CatalogItem> items){
        List<List<CatalogItem>> groups=new ArrayList<>();Map<String,List<CatalogItem>> byId=new HashMap<>(),byName=new HashMap<>();
        Map<String,List<CatalogItem>> byKey=new HashMap<>();
        for(CatalogItem c:items){
            List<CatalogItem> same=byKey.get(c.key());
            if(same!=null){same.add(c);continue;}
            String id=idKey(c),name=nameKey(c);
            List<CatalogItem> g=id==null?null:byId.get(id);
            if(g==null&&name!=null){List<CatalogItem> candidate=byName.get(name);if(candidate!=null&&compatible(candidate,c))g=candidate;}
            if(g==null){g=new ArrayList<>();groups.add(g);}
            g.add(c);byKey.put(c.key(),g);
            if(id!=null)byId.putIfAbsent(id,g);
            if(name!=null)byName.putIfAbsent(name,g);
        }
        List<CatalogItem> out=new ArrayList<>(groups.size());
        for(List<CatalogItem> g:groups)out.add(combine(g));
        return out;
    }
    /** A group that already holds a different TMDB id is a different work, even with the same name and year. */
    private static boolean compatible(List<CatalogItem> group,CatalogItem c){
        if(c.tmdbId<=0)return true;
        for(CatalogItem o:group)if(o.tmdbId>0&&o.tmdbId!=c.tmdbId)return false;
        return true;
    }
    private static CatalogItem combine(List<CatalogItem> g){
        CatalogItem rep=g.get(0);
        for(CatalogItem c:g)if(better(c,rep))rep=c;
        long first=rep.firstSeen;
        for(CatalogItem c:g){
            addAudio(rep,c);
            if(c!=rep&&!c.title.providerName.isEmpty()&&!c.title.providerName.equals(rep.title.providerName)&&!rep.sources.contains(c.title.providerName))rep.sources.add(c.title.providerName);
            if(c.firstSeen>=0&&(first<0||c.firstSeen<first))first=c.firstSeen;
            rep.popularity=Math.max(rep.popularity,c.popularity);
            if(rep.tmdbId<=0&&c.tmdbId>0)rep.tmdbId=c.tmdbId;
            if(rep.releaseDate.isEmpty()&&!c.releaseDate.isEmpty())rep.releaseDate=c.releaseDate;
            if(rep.title.poster.isEmpty()&&!c.title.poster.isEmpty()){rep.title.poster=c.title.poster;if(rep.title.background.isEmpty())rep.title.background=c.title.background;}
            if(rep.title.description.isEmpty()&&!c.title.description.isEmpty())rep.title.description=c.title.description;
        }
        rep.firstSeen=first;
        return rep;
    }
    private static void addAudio(CatalogItem rep,CatalogItem c){
        String dub=dubLanguage(c.title.name);
        for(String a:c.audio)if(!rep.audio.contains(a))rep.audio.add(a);
        if(!dub.isEmpty()&&!rep.audio.contains(dub))rep.audio.add(dub);
        else if(dub.isEmpty()&&!c.title.language.isEmpty()&&!rep.audio.contains(c.title.language))rep.audio.add(c.title.language);
    }
    /** Prefer what the viewer can actually play, then verified quality, then the richest metadata. */
    static boolean better(CatalogItem a,CatalogItem b){
        if(a.playable!=b.playable)return a.playable;
        if(a.verifiedHeight!=b.verifiedHeight)return a.verifiedHeight>b.verifiedHeight;
        if((a.tmdbId>0)!=(b.tmdbId>0))return a.tmdbId>0;
        if(a.title.poster.isEmpty()!=b.title.poster.isEmpty())return !a.title.poster.isEmpty();
        return a.title.description.length()>b.title.description.length();
    }
}
