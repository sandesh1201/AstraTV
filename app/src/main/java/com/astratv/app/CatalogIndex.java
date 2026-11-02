package com.astratv.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

/**
 * First-seen times per title, in its own catalog_index_v1 database. The first catalog a provider
 * delivers is recorded as a baseline (time 0) so existing titles are never shown as "recently added".
 */
final class CatalogIndex {
    private final SQLiteOpenHelper db;
    private final Map<String,Long> seen=new HashMap<>();
    private final Set<String> seededProviders=new HashSet<>();
    private boolean loaded;
    CatalogIndex(Context c){
        db=new SQLiteOpenHelper(c.getApplicationContext(),"catalog_index_v1.db",null,1){
            @Override public void onCreate(SQLiteDatabase d){
                d.execSQL("CREATE TABLE seen(title_key TEXT PRIMARY KEY,provider TEXT,first_seen INTEGER)");
                d.execSQL("CREATE TABLE providers(provider TEXT PRIMARY KEY,seeded_at INTEGER)");
            }
            @Override public void onUpgrade(SQLiteDatabase d,int a,int b){}
        };
    }
    private void load(){
        if(loaded)return;loaded=true;
        try{SQLiteDatabase d=db.getReadableDatabase();
            try(Cursor c=d.query("seen",new String[]{"title_key","first_seen"},null,null,null,null,null)){while(c.moveToNext())seen.put(c.getString(0),c.getLong(1));}
            try(Cursor c=d.query("providers",new String[]{"provider"},null,null,null,null,null)){while(c.moveToNext())seededProviders.add(c.getString(0));}
        }catch(Exception ignored){/* index is optional; Recently Added stays hidden */}
    }
    /** Call from a background thread with a provider's first catalog page. */
    synchronized void observe(String provider,List<Library.Title> titles){
        load();boolean baseline=!seededProviders.contains(provider);long now=baseline?0:System.currentTimeMillis();
        try{SQLiteDatabase d=db.getWritableDatabase();d.beginTransaction();
            try{
                for(Library.Title t:titles){String key=t.key();if(seen.containsKey(key))continue;
                    ContentValues v=new ContentValues();v.put("title_key",key);v.put("provider",provider);v.put("first_seen",now);
                    d.insertWithOnConflict("seen",null,v,SQLiteDatabase.CONFLICT_IGNORE);seen.put(key,now);}
                if(baseline){ContentValues p=new ContentValues();p.put("provider",provider);p.put("seeded_at",System.currentTimeMillis());d.insertWithOnConflict("providers",null,p,SQLiteDatabase.CONFLICT_IGNORE);seededProviders.add(provider);}
                d.setTransactionSuccessful();
            }finally{d.endTransaction();}
        }catch(Exception ignored){/* best effort */}
    }
    /** Milliseconds since epoch, or 0 when unknown or part of the baseline. */
    synchronized long firstSeen(String key){load();Long v=seen.get(key);return v==null?0:v;}
}
