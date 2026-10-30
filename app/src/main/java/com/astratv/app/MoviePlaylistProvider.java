package com.astratv.app;

import java.util.*;

final class MoviePlaylistProvider {
    static final String PLAYLIST="https://thirud4a4.github.io/movies_m3u/playlist.m3u";
    private List<Library.Title> cached=new ArrayList<>();private long loaded=0;
    synchronized List<Library.Title> all()throws Exception{
        if(cached.isEmpty()||System.currentTimeMillis()-loaded>15*60*1000){
            List<Library.Title> next=PlaylistParser.movies(ProviderRepository.requestText(PLAYLIST));
            if(next.isEmpty())throw new java.io.IOException("The movie playlist contained no supported HTTPS entries");cached=next;loaded=System.currentTimeMillis();
        }return new ArrayList<>(cached);
    }
    ProviderRepository.Catalog catalog(String search,int page,String language,String genre)throws Exception{
        List<Library.Title> all=all(),selected=CatalogQuery.filter(all,search,language,genre);
        // Both priority languages appear on the first page rather than one hiding the other.
        if(language.equals("Telugu + Hindi")){
            List<Library.Title> tel=new ArrayList<>(),hin=new ArrayList<>();for(Library.Title t:selected)(t.language.equals("Telugu")?tel:hin).add(t);
            selected=new ArrayList<>();for(int i=0;i<Math.max(tel.size(),hin.size());i++){if(i<tel.size())selected.add(tel.get(i));if(i<hin.size())selected.add(hin.get(i));}
        }
        return CatalogQuery.page(selected,page,"playlist entries");
    }
}
