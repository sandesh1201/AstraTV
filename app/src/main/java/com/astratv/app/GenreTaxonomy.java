package com.astratv.app;

import java.util.*;

/** Maps every provider genre onto the TMDB movie/TV taxonomy. Unknown tokens become Other; nothing is dropped. */
public final class GenreTaxonomy {
    private GenreTaxonomy(){}
    public static final String OTHER="Other";
    public static final List<String> MOVIE=Collections.unmodifiableList(Arrays.asList(
        "Action","Adventure","Animation","Comedy","Crime","Documentary","Drama","Family","Fantasy","History","Horror","Music","Mystery","Romance","Science Fiction","TV Movie","Thriller","War","Western"));
    public static final List<String> TV=Collections.unmodifiableList(Arrays.asList(
        "Action & Adventure","Animation","Comedy","Crime","Documentary","Drama","Family","Kids","Mystery","News","Reality","Sci-Fi & Fantasy","Soap","Talk","War & Politics","Western"));
    /** Browse genres shown in the UI, as canonical names. */
    public static final List<String> BROWSE=Collections.unmodifiableList(Arrays.asList(
        "Action","Comedy","Romance","Thriller","Horror","Drama","Crime","Mystery","Science Fiction","Adventure","Family","Animation","Fantasy","Documentary"));
    public static String display(String canonical){return "Science Fiction".equals(canonical)?"Sci-Fi":canonical;}
    public static final List<String> ALL;
    private static final Map<String,String> ALIASES=new LinkedHashMap<>();
    static{
        LinkedHashSet<String> all=new LinkedHashSet<>();all.addAll(MOVIE);all.addAll(TV);all.add(OTHER);ALL=Collections.unmodifiableList(new ArrayList<>(all));
        for(String g:ALL)alias(g,g);
        alias("sci-fi","Science Fiction");alias("scifi","Science Fiction");alias("science-fiction","Science Fiction");alias("sci fi","Science Fiction");
        alias("sci-fi & fantasy","Sci-Fi & Fantasy");alias("sci-fi fantasy","Sci-Fi & Fantasy");alias("scifi fantasy","Sci-Fi & Fantasy");
        alias("action & adventure","Action & Adventure");alias("action-adventure","Action & Adventure");alias("action and adventure","Action & Adventure");
        alias("war & politics","War & Politics");alias("war and politics","War & Politics");
        alias("tv movie","TV Movie");alias("tv-movie","TV Movie");
        alias("kids","Kids");alias("children","Kids");alias("children's","Kids");alias("childrens","Kids");
        alias("soap opera","Soap");alias("talk show","Talk");alias("reality tv","Reality");
        alias("movies",OTHER);alias("movie",OTHER);alias("film",OTHER);alias("episode",OTHER);alias("series",OTHER);
        alias("trailer",OTHER);alias("entertainment",OTHER);alias("general",OTHER);alias("religious",OTHER);
        alias("sports",OTHER);alias("sport",OTHER);alias("education",OTHER);alias("culture",OTHER);alias("business",OTHER);alias("live",OTHER);
    }
    private static final java.util.regex.Pattern SPACES=java.util.regex.Pattern.compile("\\s+"),SEPARATORS=java.util.regex.Pattern.compile("[;,/·|]");
    private static void alias(String from,String to){ALIASES.put(from.toLowerCase(Locale.ROOT),to);}
    public static String canonical(String token){
        if(token==null)return OTHER;
        String key=SPACES.matcher(token.trim().toLowerCase(Locale.ROOT)).replaceAll(" ");
        if(key.isEmpty())return OTHER;
        String mapped=ALIASES.get(key);if(mapped!=null)return mapped;
        for(String g:ALL)if(g.equalsIgnoreCase(token.trim()))return g;
        return OTHER;
    }
    public static List<String> normalize(String raw){
        LinkedHashSet<String> out=new LinkedHashSet<>();
        if(raw==null||raw.trim().isEmpty()){out.add(OTHER);return new ArrayList<>(out);}
        for(String part:SEPARATORS.split(raw)){
            String token=part.trim();if(token.isEmpty())continue;out.add(canonical(token));
        }
        if(out.isEmpty())out.add(OTHER);
        return new ArrayList<>(out);
    }
    public static boolean matches(Library.Title t,String genre){
        if(genre==null||genre.isEmpty()||genre.equals("All"))return true;
        String want=canonical(genre);
        List<String> have=normalize(join(t.group,t.genre));
        if(have.contains(want)||have.contains(genre))return true;
        String haystack=(t.group+" "+t.genre);
        for(String part:SEPARATORS.split(haystack))if(part.trim().equalsIgnoreCase(genre))return true;
        return false;
    }
    public static List<String> of(Library.Title t){return normalize(join(t.group,t.genre));}
    private static String join(String a,String b){
        String left=a==null?"":a.trim(),right=b==null?"":b.trim();
        if(left.isEmpty())return right;if(right.isEmpty())return left;return left+" · "+right;
    }
}
