package com.astratv.app;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative title matching. Low-confidence results are never treated as facts. */
public final class MetadataMatcher {
    private MetadataMatcher(){}
    public static final float AUTO=0.62f;
    public static final class Candidate {
        public final String id,title,originalTitle,year,language,type;
        public Candidate(String id,String title,String originalTitle,String year,String language,String type){
            this.id=id;this.title=title==null?"":title;this.originalTitle=originalTitle==null?"":originalTitle;
            this.year=year==null?"":year;this.language=language==null?"":language;this.type=type==null?"movie":type;
        }
    }
    public static String normalize(String name){
        if(name==null)return "";
        String n=name.toLowerCase(Locale.ROOT).replaceAll("\\((?:19|20)\\d{2}\\)","");
        n=n.replaceAll("[^a-z0-9\\s]"," ").replaceAll("\\s+"," ").trim();
        return n;
    }
    public static float score(Library.Title t,Candidate c){
        String want=normalize(t.name),got=normalize(c.title),orig=normalize(c.originalTitle);
        boolean titleHit= !want.isEmpty()&&(want.equals(got)||want.equals(orig));
        boolean close= !want.isEmpty()&&(got.contains(want)||want.contains(got)||orig.contains(want)||want.contains(orig));
        int ty=CatalogSort.year(t),cy=year(c.year);
        boolean yearHit=ty>0&&cy>0&&ty==cy;
        boolean typeHit=kind(t).equals(c.type);
        boolean langHit=!t.language.isEmpty()&&!c.language.isEmpty()&&(t.language.equalsIgnoreCase(c.language)||t.language.toLowerCase(Locale.ROOT).startsWith(c.language.toLowerCase(Locale.ROOT)));
        boolean yearConflict=ty>0&&cy>0&&ty!=cy;
        if(titleHit&&yearHit&&typeHit)return 0.95f;
        if(titleHit&&yearHit)return 0.88f;
        if(titleHit&&yearConflict)return 0.42f;
        if(titleHit&&typeHit&&langHit)return 0.78f;
        if(titleHit&&typeHit)return 0.70f;
        if(titleHit)return 0.62f;
        if(close&&yearHit&&typeHit)return 0.58f;
        if(close&&yearHit)return 0.50f;
        if(close)return 0.40f;
        return 0.12f;
    }
    public static Candidate best(Library.Title t,List<Candidate> candidates){
        Candidate pick=null;float best=0;
        for(Candidate c:candidates){float s=score(t,c);if(s>best){best=s;pick=c;}}
        return best>=0.55f?pick:null;
    }
    public static boolean autoApply(float score){return score>=AUTO;}
    public static String kind(Library.Title t){
        if(t.type==null)return "movie";
        if(t.type.equals("series")||t.type.equals("episode")||t.provider.equals("tamil")||t.provider.equals("telugu"))return "tv";
        if(t.type.equals("tv"))return "tv";
        return "movie";
    }
    private static int year(String value){
        Matcher m=Pattern.compile("((?:19|20)\\d{2})").matcher(value==null?"":value);
        return m.find()?Integer.parseInt(m.group(1)):0;
    }
}
