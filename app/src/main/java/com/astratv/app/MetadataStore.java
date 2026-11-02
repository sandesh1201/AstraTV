package com.astratv.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.JSONObject;

/** Dedicated metadata_v1 cache. Not the library JSON format. TTL rows expire; manual matches do not. */
final class MetadataStore {
    private static final long TTL=7L*24*60*60*1000;
    private final SQLiteOpenHelper db;
    MetadataStore(Context c){
        db=new SQLiteOpenHelper(c,"metadata_v1.db",null,1){
            @Override public void onCreate(SQLiteDatabase d){
                d.execSQL("CREATE TABLE matches(title_key TEXT PRIMARY KEY,tmdb_id TEXT,media_type TEXT,confidence REAL,payload TEXT,fetched_at INTEGER,manual INTEGER)");
            }
            @Override public void onUpgrade(SQLiteDatabase d,int a,int b){}
        };
    }
    synchronized JSONObject get(String key,boolean allowStale){
        try(Cursor c=db.getReadableDatabase().query("matches",null,"title_key=?",new String[]{key},null,null,null)){
            if(!c.moveToFirst())return null;
            long fetched=c.getLong(c.getColumnIndexOrThrow("fetched_at"));
            boolean manual=c.getInt(c.getColumnIndexOrThrow("manual"))==1;
            if(!allowStale&&!manual&&System.currentTimeMillis()-fetched>TTL)return null;
            return new JSONObject(c.getString(c.getColumnIndexOrThrow("payload")));
        }catch(Exception e){return null;}
    }
    synchronized void delete(String key){try{db.getWritableDatabase().delete("matches","title_key=?",new String[]{key});}catch(Exception ignored){/* nothing to remove */}}
    synchronized void put(String key,JSONObject payload,boolean manual){
        try{
            SQLiteDatabase d=db.getWritableDatabase();
            android.content.ContentValues v=new android.content.ContentValues();
            v.put("title_key",key);v.put("tmdb_id",payload.optString("tmdbId"));v.put("media_type",payload.optString("type"));
            v.put("confidence",payload.optDouble("confidence"));v.put("payload",payload.toString());
            v.put("fetched_at",System.currentTimeMillis());v.put("manual",manual?1:0);
            d.insertWithOnConflict("matches",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        }catch(Exception ignored){}
    }
}
