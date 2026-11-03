package com.astratv.app;

import android.os.Parcelable;
import java.util.*;

/**
 * Per-tab memory of where the viewer was: focused row and card, list scroll positions and the
 * active filters, so switching tabs or returning from details lands on the same card.
 */
final class FocusAnchor {
    static final class TabState{
        Parcelable list;Map<String,Parcelable> rows=new HashMap<>();
        String row="",key="";int index=-1;
        String query="";
    }
    private final Map<String,TabState> tabs=new HashMap<>();
    TabState get(String tab){return tabs.computeIfAbsent(tab,t->new TabState());}
    void focused(String tab,String row,String key,int index){TabState s=get(tab);s.row=row==null?"":row;s.key=key==null?"":key;s.index=index;}

    /**
     * Where focus should land after a list refresh: the same card if it is still there, otherwise
     * the card now at the old position (clamped), so focus never jumps to the start of the row.
     */
    static int restoreIndex(List<String> keys,String key,int previousIndex){
        if(keys.isEmpty())return -1;
        if(key!=null&&!key.isEmpty()){int i=keys.indexOf(key);if(i>=0)return i;}
        if(previousIndex<0)return 0;
        return Math.min(previousIndex,keys.size()-1);
    }
}
