package com.astratv.app;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class CatalogRankingTest {
    private static Library.Title t(String provider,String id,String name,String year,String language){
        Library.Title t=new Library.Title(id,name,"","https://example.com/"+id+".mp4","",year,"Drama");t.provider=provider;t.providerName=provider;t.language=language;return t;
    }

    @Test public void sameFilmFromTwoSourcesMergesAndKeepsTheOtherAsAlternate(){
        Library.Title a=t("movierulz","1","Kalki 2898 AD","2024","Telugu"),b=t("einthusan_sim","x","Kalki 2898 AD (2024)","2024","Telugu");b.poster="https://img.example.com/p.jpg";
        CatalogRanking.Merged m=CatalogRanking.dedupe(Arrays.asList(a,b));
        assertEquals(1,m.titles.size());assertSame(b,m.titles.get(0));
        assertEquals(Collections.singletonList(a),m.alternates.get(b.key()));
    }
    @Test public void differentYearsUndatedLiveAndSameProviderAreNotMerged(){
        Library.Title a=t("p1","1","Hero","2019",""),b=t("p2","2","Hero","2007",""),c=t("p1","3","Drishyam","",""),d=t("p2","4","Drishyam","","");
        Library.Title e=t("news","5","News 24","2024","");e.type="tv";Library.Title f=t("news","6","News 24","2024","");f.type="tv";
        Library.Title g=t("p3","7","Jailer","2023",""),h=t("p3","8","Jailer","2023","");
        CatalogRanking.Merged m=CatalogRanking.dedupe(Arrays.asList(a,b,c,d,e,f,g,h));
        assertEquals(8,m.titles.size());assertTrue(m.alternates.isEmpty());
    }
    @Test public void trendsMatchByNameAndYearOnly(){
        Library.Title a=t("p","1","Pushpa 2: The Rule","2024","Telugu"),b=t("p","2","Dune","1984","English"),c=t("p","3","Unrelated","2024","");
        List<CatalogRanking.Trend> trends=Arrays.asList(new CatalogRanking.Trend("Dune: Part Two","",2024,1),new CatalogRanking.Trend("Pushpa 2: The Rule","Pushpa 2 - The Rule",2024,2),new CatalogRanking.Trend("Dune","",2021,3));
        Map<String,Integer> ranks=CatalogRanking.matchTrends(Arrays.asList(a,b,c),trends);
        assertEquals(Integer.valueOf(2),ranks.get(a.key()));
        assertFalse("1984 Dune must not inherit the 2021 film's trend",ranks.containsKey(b.key()));
        assertFalse(ranks.containsKey(c.key()));
    }
    @Test public void newSortModesUseSignalsAndFallBackToNewest(){
        Library.Title old=t("p","1","Old","2001","Hindi"),mid=t("p","2","Mid","2015","Telugu"),fresh=t("p","3","Fresh","2025","English");
        List<Library.Title> all=Arrays.asList(old,mid,fresh);
        CatalogSort.Signals s=new CatalogSort.Signals(){
            @Override public int trendDay(Library.Title t){return t==old?1:0;}
            @Override public long firstSeen(Library.Title t){return t==mid?5000:0;}
        };
        assertSame(old,CatalogSort.by(all,"trending",s).get(0));
        assertSame(fresh,CatalogSort.by(all,"trending",s).get(1));
        assertSame(mid,CatalogSort.by(all,"added",s).get(0));
        assertSame(mid,CatalogSort.by(all,"language",s).get(0));
        assertSame(fresh,CatalogSort.by(all,"popularity",s).get(1));
        assertSame(fresh,CatalogSort.by(all,"newest",s).get(0));
        assertEquals("Recently Added",CatalogSort.label("added"));
    }
}
