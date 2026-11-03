package com.astratv.app;

import java.util.*;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Default order is newest first. Unknown dates sort last, never invented. */
public final class CatalogSort {
    private CatalogSort(){}
    private static final Pattern YEAR=Pattern.compile("\\b((?:19|20)\\d{2})\\b"),PLAIN_YEAR=Pattern.compile("(?:19|20)\\d{2}"),ISO_DATE=Pattern.compile("\\d{4}-\\d{2}-\\d{2}"),NOT_NUMBER=Pattern.compile("[^0-9.]");
    public static final List<String> MODES=Collections.unmodifiableList(Arrays.asList("trending","newest","added","popularity","rating","language","genre","az"));

    /** External ordering evidence. Every value defaults to "unknown" (0). */
    public interface Signals{
        default int tmdbYear(Library.Title t){return 0;}
        /** TMDB daily trending rank, 1 = top. */
        default int trendDay(Library.Title t){return 0;}
        /** TMDB weekly trending rank, 1 = top. */
        default int trendWeek(Library.Title t){return 0;}
        default long firstSeen(Library.Title t){return 0;}
    }
    private static final Signals NONE=new Signals(){};

    public static List<Library.Title> newestFirst(List<Library.Title> items){return by(items,"newest",NONE);}
    public static List<Library.Title> newestFirst(List<Library.Title> items,ToIntFunction<Library.Title> tmdbYear){return by(items,"newest",tmdbYear);}
    public static List<Library.Title> by(List<Library.Title> items,String mode){return by(items,mode,NONE);}
    public static List<Library.Title> by(List<Library.Title> items,String mode,ToIntFunction<Library.Title> tmdbYear){
        if(tmdbYear==null)return by(items,mode,NONE);
        return by(items,mode,new Signals(){@Override public int tmdbYear(Library.Title t){return tmdbYear.applyAsInt(t);}});
    }
    public static List<Library.Title> by(List<Library.Title> items,String mode,Signals s){
        Signals signals=s==null?NONE:s;
        List<Library.Title> out=new ArrayList<>(items);
        Comparator<Library.Title> newest=Comparator.comparingInt((Library.Title t)->dateKey(t,signals::tmdbYear)).reversed()
            .thenComparing(Comparator.comparingDouble(CatalogSort::popularity).reversed())
            .thenComparing(t->t.name,String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Library.Title::key);
        Comparator<Library.Title> ordered;
        switch(mode==null?"":mode){
            case "trending": ordered=Comparator.comparingInt((Library.Title t)->rankKey(signals.trendDay(t))).thenComparing(newest);break;
            case "popularity": ordered=Comparator.comparingInt((Library.Title t)->rankKey(signals.trendWeek(t))).thenComparingInt(t->rankKey(signals.trendDay(t))).thenComparing(newest);break;
            case "added": ordered=Comparator.comparingLong((Library.Title t)->signals.firstSeen(t)).reversed().thenComparing(newest);break;
            case "rating": ordered=Comparator.comparingDouble(CatalogSort::popularity).reversed().thenComparing(newest);break;
            case "language": ordered=Comparator.comparingInt((Library.Title t)->languageKey(t)).thenComparing(newest);break;
            case "genre": ordered=Comparator.comparing((Library.Title t)->GenreTaxonomy.of(t).get(0),String.CASE_INSENSITIVE_ORDER).thenComparing(newest);break;
            case "az": ordered=Comparator.comparing((Library.Title t)->t.name,String.CASE_INSENSITIVE_ORDER).thenComparing(Library.Title::key);break;
            default: ordered=newest;
        }
        out.sort(ordered);return out;
    }
    public static String label(String mode){
        switch(mode==null?"":mode){
            case "trending":return "Trending";case "added":return "Recently Added";case "popularity":return "Most Popular";
            case "rating":return "Highest Rated";case "language":return "Language";case "genre":return "Genre";case "az":return "A–Z";
            default:return "Newest Release";
        }
    }
    private static int rankKey(int rank){return rank>0?rank:Integer.MAX_VALUE;}
    private static int languageKey(Library.Title t){
        String l=LanguageTaxonomy.of(t);int i=LanguageTaxonomy.ALL.indexOf(l);return l.isEmpty()?LanguageTaxonomy.ALL.size():i;
    }
    public static int year(Library.Title t){
        if(t.year!=null&&PLAIN_YEAR.matcher(t.year).matches())return Integer.parseInt(t.year);
        Matcher m=YEAR.matcher(t.name==null?"":t.name);
        return m.find()?Integer.parseInt(m.group(1)):0;
    }
    static int dateKey(Library.Title t,ToIntFunction<Library.Title> tmdbYear){
        String released=t.released==null?"":t.released.trim();
        if(ISO_DATE.matcher(released).matches())return Integer.parseInt(released.replace("-",""));
        int y=year(t);if(y>0)return y*10000;
        int matched=tmdbYear.applyAsInt(t);return matched>0?matched*10000:0;
    }
    static double popularity(Library.Title t){
        if(t.rating==null||t.rating.isEmpty())return 0;
        try{return Double.parseDouble(NOT_NUMBER.matcher(t.rating).replaceAll(""));}catch(NumberFormatException e){return 0;}
    }
}
