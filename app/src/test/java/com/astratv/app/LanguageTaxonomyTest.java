package com.astratv.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class LanguageTaxonomyTest {
    @Test public void codesAndNamesMapToFixedList(){
        assertEquals("Telugu",LanguageTaxonomy.canonical("te"));
        assertEquals("Tamil",LanguageTaxonomy.canonical("TAMIL"));
        assertEquals("English",LanguageTaxonomy.canonical("Hollywood"));
        assertEquals("Bengali",LanguageTaxonomy.canonical("Bangla"));
        assertEquals("Hindi",LanguageTaxonomy.canonical("Hindi Dubbed"));
    }
    @Test public void unknownBecomesOtherAndEmptyStaysEmpty(){
        assertEquals(LanguageTaxonomy.OTHER,LanguageTaxonomy.canonical("Gujarati"));
        assertEquals("",LanguageTaxonomy.canonical("  "));
        assertEquals(10,LanguageTaxonomy.ALL.size());
    }
    @Test public void indianLanguages(){
        assertTrue(LanguageTaxonomy.indian("ml"));
        assertFalse(LanguageTaxonomy.indian("English"));
    }
}
