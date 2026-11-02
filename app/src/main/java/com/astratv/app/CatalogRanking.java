package com.astratv.app;

import java.util.*;

/** Cross-source merging and external trending matches. Pure logic; no network, no invented scores. */
public final class CatalogRanking {
    private CatalogRanking(){}
    public static final class Trend{
        public final String title,originalTitle;public final int year,rank;
        public Trend(String title,String originalTitle,int year,int rank){this.title=title;this.originalTitle=originalTitle==null?"":originalTitle;this.year=year;this.rank=rank;}
    }
    public static final class Merged{
        public final List<Library.Title> titles;
        /** Representative key → other copies of the same film from different sources. */
        public final Map<String,List<Library.Title>> alternates;
        Merged(List<Library.Title> titles,Map<String,List<Library.Title>> alternates){this.titles=titles;this.alternates=alternates;}
    }

    /**
     * Merges copies of the same film across providers. Only movies and series with the same
     * normalised title and the same known year are merged; live channels, episodes and undated
     * titles are left alone so distinct works are never collapsed.
     */
    public static Merged dedupe(List<Library.Title> items){
        Map<String,Library.Title> best=new LinkedHashMap<>();Map<String,List<Library.Title>> groups=new HashMap<>();List<Object> order=new ArrayList<>();
        for(Library.Title t:items){
            String id=identity(t);
            if(id==null){order.add(t);continue;}
            List<Library.Title> g=groups.get(id);
            if(g==null){g=new ArrayList<>();groups.put(id,g);order.add(id);}
            boolean sameProvider=false;for(Library.Title o:g)if(o.provider.equals(t.provider))sameProvider=true;
            if(sameProvider&&!g.isEmpty()){order.add(t);continue;}
            g.add(t);
        }
        List<Library.Title> out=new ArrayList<>();Map<String,List<Library.Title>> alternates=new HashMap<>();
        for(Object o:order){
            if(o instanceof Library.Title){out.add((Library.Title)o);continue;}
            List<Library.Title> g=groups.get((String)o);Library.Title rep=g.get(0);
            for(Library.Title t:g)if(richness(t)>richness(rep))rep=t;
            out.add(rep);
            if(g.size()>1){List<Library.Title> others=new ArrayList<>();for(Library.Title t:g)if(t!=rep)others.add(t);alternates.put(rep.key(),others);}
        }
        return new Merged(out,alternates);
    }
    static String identity(Library.Title t){
        String type=t.type==null?"movie":t.type;
        if(type.equals("tv")||type.equals("episode"))return null;
        int year=CatalogSort.year(t);if(year<=0)return null;
        String name=MetadataMatcher.normalize(t.name);if(name.isEmpty())return null;
        return (type.equals("series")?"series":"movie")+"|"+name+"|"+year;
    }
    private static int richness(Library.Title t){
        int score=0;if(!t.poster.isEmpty())score+=4;if(!t.description.isEmpty())score+=2;if(!t.released.isEmpty())score+=1;if(t.videos.length()>0)score+=1;return score;
    }

    /** Maps catalog keys to TMDB trending ranks (1 = top). A title needs a matching name and year. */
    public static Map<String,Integer> matchTrends(List<Library.Title> pool,List<Trend> trends){
        Map<String,List<Trend>> byName=new HashMap<>();
        for(Trend tr:trends){
            for(String n:new String[]{MetadataMatcher.normalize(tr.title),MetadataMatcher.normalize(tr.originalTitle)})
                if(!n.isEmpty())byName.computeIfAbsent(n,k->new ArrayList<>()).add(tr);
        }
        Map<String,Integer> out=new HashMap<>();
        for(Library.Title t:pool){
            List<Trend> hits=byName.get(MetadataMatcher.normalize(t.name));if(hits==null)continue;
            int year=CatalogSort.year(t);
            for(Trend tr:hits){
                boolean yearOk=year>0&&tr.year>0?Math.abs(year-tr.year)<=1:hits.size()==1;
                if(yearOk){Integer prev=out.get(t.key());if(prev==null||tr.rank<prev)out.put(t.key(),tr.rank);}
            }
        }
        return out;
    }
}
