package com.astratv.app;

import java.util.*;

/** Shared filtering and bounded presentation across every native source. */
final class CatalogQuery {
    static final int PAGE_SIZE=48;
    static List<Library.Title> filter(List<Library.Title> items,String search,String language,String genre){
        List<Library.Title> out=new ArrayList<>();String q=search.toLowerCase(Locale.ROOT).trim();
        for(Library.Title t:items){
            boolean lang=language.equals("All")||language.isEmpty()||language.equalsIgnoreCase(t.language)||language.equals("Telugu + Hindi")&&(t.language.equals("Telugu")||t.language.equals("Hindi"));
            boolean category=genre.equals("All")||genre.isEmpty()||Arrays.stream((t.group.isEmpty()?t.genre:t.group).split("[;,/]")).anyMatch(g->g.trim().equalsIgnoreCase(genre));
            if(lang&&category&&(q.isEmpty()||(t.name+" "+t.cast+" "+t.genre+" "+t.language).toLowerCase(Locale.ROOT).contains(q)))out.add(t);
        }return out;
    }
    static ProviderRepository.Catalog page(List<Library.Title> items,int page,String noun){
        int start=Math.min(items.size(),Math.max(0,page-1)*PAGE_SIZE),end=Math.min(items.size(),start+PAGE_SIZE);
        return new ProviderRepository.Catalog(new ArrayList<>(items.subList(start,end)),items.size()+" "+noun,false,items.size(),page,end<items.size());
    }
}
