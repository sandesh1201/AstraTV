package com.astratv.app;

final class CatalogTestData {
    private CatalogTestData(){}
    static CatalogItem movie(String provider,String id,String name,String date){
        Library.Title t=new Library.Title(id,name,"","","",date.length()>=4?date.substring(0,4):"","Drama");
        t.provider=provider;t.providerName=provider;t.released=date;t.poster="https://img.example/"+id+".jpg";
        CatalogItem c=new CatalogItem(t);c.releaseDate=date;return c;
    }
    static CatalogItem tmdb(int id,String name,String date){CatalogItem c=movie("tmdb","movie:"+id,name,date);c.tmdbId=id;return c;}
    static CatalogItem channel(String id,String name){
        Library.Title t=new Library.Title(id,name,"","https://live.example/"+id+".m3u8","","","News");t.provider="news";t.providerName="iptv-org";t.type="tv";
        CatalogItem c=new CatalogItem(t);c.kind=CatalogItem.CHANNEL;return c;
    }
}
