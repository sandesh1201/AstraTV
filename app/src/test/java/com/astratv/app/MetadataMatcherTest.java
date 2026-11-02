package com.astratv.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class MetadataMatcherTest {
    private Library.Title title(String name,String year,String language,String type){
        Library.Title t=new Library.Title("id",name,"","https://media.test/a.mp4","",year,"Drama");t.language=language;t.type=type;return t;
    }
    @Test public void exactTitleYearAndTypeIsHighConfidence(){
        MetadataMatcher.Candidate c=new MetadataMatcher.Candidate("11","Sita Ramam","Sita Ramam","2022","te","movie");
        float score=MetadataMatcher.score(title("Sita Ramam","2022","Telugu","movie"),c);
        assertEquals(0.95f,score,0.001f);
        assertTrue(MetadataMatcher.autoApply(score));
    }
    @Test public void yearMismatchIsNotAutoApplied(){
        MetadataMatcher.Candidate c=new MetadataMatcher.Candidate("11","Hello","Hello","1995","en","movie");
        float score=MetadataMatcher.score(title("Hello","2024","English","movie"),c);
        assertTrue(score<MetadataMatcher.AUTO);
        assertFalse(MetadataMatcher.autoApply(score));
    }
    @Test public void lowConfidenceMatchIsRejected(){
        Library.Title t=title("The Missing Piece","2021","English","movie");
        MetadataMatcher.Candidate other=new MetadataMatcher.Candidate("9","Unrelated Fire","Other","2010","fr","tv");
        assertNull(MetadataMatcher.best(t,Collections.singletonList(other)));
    }
}
