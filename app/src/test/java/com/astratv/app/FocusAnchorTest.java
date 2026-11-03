package com.astratv.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class FocusAnchorTest {
    @Test public void sameCardKeepsFocusAfterRefresh(){
        assertEquals(2,FocusAnchor.restoreIndex(Arrays.asList("new","a","b","c"),"b",1));
    }
    @Test public void removedCardFallsBackToOldPositionClamped(){
        assertEquals(1,FocusAnchor.restoreIndex(Arrays.asList("a","c","d"),"b",1));
        assertEquals(1,FocusAnchor.restoreIndex(Arrays.asList("a","c"),"z",7));
        assertEquals(0,FocusAnchor.restoreIndex(Arrays.asList("a"),"",-1));
        assertEquals(-1,FocusAnchor.restoreIndex(Collections.emptyList(),"a",0));
    }
    @Test public void tabsRememberIndependently(){
        FocusAnchor a=new FocusAnchor();
        a.focused("Home","row1","k1",3);a.focused("Live TV","grid","k2",9);a.get("All Movies").query="{}";
        assertEquals("k1",a.get("Home").key);assertEquals(3,a.get("Home").index);
        assertEquals("grid",a.get("Live TV").row);
        assertEquals("{}",a.get("All Movies").query);
        assertEquals(-1,a.get("TV Shows").index);
    }
}
