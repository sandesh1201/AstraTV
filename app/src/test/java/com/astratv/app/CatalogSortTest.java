package com.astratv.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class CatalogSortTest {
    private Library.Title title(String id,String name,String year,String released,String rating){
        Library.Title t=new Library.Title(id,name,"","https://media.test/"+id+".mp4","",year,"Drama");t.released=released;t.rating=rating;return t;
    }
    @Test public void newestFirstUsesReleaseDateThenPopularityThenTitle(){
        Library.Title old=title("a","Zulu","2019","2019-01-01","9");
        Library.Title newer=title("b","Alpha","2024","2024-06-01","1");
        Library.Title sameDayPopular=title("c","Beta","2024","2024-06-01","8");
        List<Library.Title> ordered=CatalogSort.newestFirst(Arrays.asList(old,newer,sameDayPopular));
        assertEquals("c",ordered.get(0).id);
        assertEquals("b",ordered.get(1).id);
        assertEquals("a",ordered.get(2).id);
    }
    @Test public void missingDatesUseTitleYearThenTmdbThenLast(){
        Library.Title named=title("named","Film (2018)","","","");
        Library.Title unknown=title("unk","No Date","","","");
        Library.Title tmdb=title("tmdb","Mystery","","","");
        List<Library.Title> ordered=CatalogSort.newestFirst(Arrays.asList(unknown,named,tmdb),t->"tmdb".equals(t.id)?2021:0);
        assertEquals("tmdb",ordered.get(0).id);
        assertEquals("named",ordered.get(1).id);
        assertEquals("unk",ordered.get(2).id);
    }
    @Test public void azSortIsStableByKey(){
        Library.Title a=title("2","Same","2020","","");
        Library.Title b=title("1","Same","2020","","");
        List<Library.Title> ordered=CatalogSort.by(Arrays.asList(a,b),"az");
        assertEquals("1",ordered.get(0).id);
        assertEquals("2",ordered.get(1).id);
    }
}
