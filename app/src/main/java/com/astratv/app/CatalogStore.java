package com.astratv.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.JSONObject;
import java.util.*;

/**
 * catalog_v2 database: items, row membership, per-source freshness, conditional-HTTP cache and
 * channel health. WAL lets the UI read while a sync writes. Never opened on the main thread.
 */
final class CatalogStore implements CatalogStorage {
    static final int VERSION=1;
    private static final long ORPHAN_TTL_MS=30L*24*60*60*1000;
    private final SQLiteOpenHelper helper;
    CatalogStore(Context c){
        helper=new SQLiteOpenHelper(c.getApplicationContext(),"catalog_v2.db",null,VERSION){
            @Override public void onConfigure(SQLiteDatabase d){d.enableWriteAheadLogging();}
            @Override public void onCreate(SQLiteDatabase d){create(d);}
            @Override public void onUpgrade(SQLiteDatabase d,int from,int to){migrate(d,from,to);}
            @Override public void onDowngrade(SQLiteDatabase d,int from,int to){reset(d);}
        };
    }
    private static void create(SQLiteDatabase d){
        d.execSQL("CREATE TABLE IF NOT EXISTS items(key TEXT PRIMARY KEY,kind TEXT,provider TEXT,lang TEXT,year INTEGER,release TEXT,popularity REAL,rating REAL,height INTEGER,playable INTEGER,norm TEXT,first_seen INTEGER,updated_at INTEGER,payload TEXT)");
        d.execSQL("CREATE INDEX IF NOT EXISTS items_norm ON items(norm)");
        d.execSQL("CREATE INDEX IF NOT EXISTS items_recent ON items(kind,first_seen,release)");
        d.execSQL("CREATE TABLE IF NOT EXISTS row_items(row_id TEXT,pos INTEGER,key TEXT,PRIMARY KEY(row_id,pos))");
        d.execSQL("CREATE INDEX IF NOT EXISTS row_items_key ON row_items(key)");
        d.execSQL("CREATE TABLE IF NOT EXISTS rows(row_id TEXT PRIMARY KEY,source TEXT,signature TEXT,synced_at INTEGER)");
        d.execSQL("CREATE TABLE IF NOT EXISTS sources(source TEXT PRIMARY KEY,synced_at INTEGER,attempt_at INTEGER,next_at INTEGER,failures INTEGER,items INTEGER,message TEXT)");
        d.execSQL("CREATE TABLE IF NOT EXISTS http(key TEXT PRIMARY KEY,etag TEXT,last_modified TEXT,body TEXT,fetched_at INTEGER)");
        d.execSQL("CREATE TABLE IF NOT EXISTS health(key TEXT PRIMARY KEY,ok_at INTEGER,fail_at INTEGER,fails INTEGER)");
    }
    /** Schema steps run in order. Version 1 is the first catalog_v2 schema; later versions add steps here. */
    private static void migrate(SQLiteDatabase d,int from,int to){
        if(from<1){reset(d);return;}
        create(d);
    }
    private static void reset(SQLiteDatabase d){
        for(String t:new String[]{"items","row_items","rows","sources","http","health"})d.execSQL("DROP TABLE IF EXISTS "+t);
        create(d);
    }
    private SQLiteDatabase db(){return helper.getWritableDatabase();}

    @Override public synchronized Set<String> replaceRows(String source,List<CatalogSource.Batch> batches,long now){
        Set<String> changed=new LinkedHashSet<>();SQLiteDatabase d=db();
        d.beginTransaction();
        try{
            boolean baseline=count(d,"SELECT COUNT(*) FROM rows WHERE source='"+source.replace("'","")+"' AND signature<>''")==0;
            for(CatalogSource.Batch b:batches){
                if(b.notModified){touchRow(d,b.rowId,source,now);continue;}
                String signature=signature(b.items),old=string(d,"SELECT signature FROM rows WHERE row_id=?",b.rowId);
                if(signature.equals(old)){touchRow(d,b.rowId,source,now);continue;}
                for(CatalogItem c:b.items)upsert(d,c,baseline?0:now,now);
                d.delete("row_items","row_id=?",new String[]{b.rowId});
                int pos=0;
                for(CatalogItem c:b.items){ContentValues v=new ContentValues();v.put("row_id",b.rowId);v.put("pos",pos++);v.put("key",c.key());d.insertWithOnConflict("row_items",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
                ContentValues r=new ContentValues();r.put("row_id",b.rowId);r.put("source",source);r.put("signature",signature);r.put("synced_at",now);
                d.insertWithOnConflict("rows",null,r,SQLiteDatabase.CONFLICT_REPLACE);
                changed.add(b.rowId);
            }
            if(!changed.isEmpty())d.execSQL("DELETE FROM items WHERE updated_at<? AND key NOT IN (SELECT key FROM row_items)",new Object[]{now-ORPHAN_TTL_MS});
            d.setTransactionSuccessful();
        }finally{d.endTransaction();}
        return changed;
    }
    private static void touchRow(SQLiteDatabase d,String rowId,String source,long now){
        ContentValues r=new ContentValues();r.put("synced_at",now);
        if(d.update("rows",r,"row_id=?",new String[]{rowId})==0){r.put("row_id",rowId);r.put("source",source);r.put("signature","");d.insertWithOnConflict("rows",null,r,SQLiteDatabase.CONFLICT_IGNORE);}
    }
    private static void upsert(SQLiteDatabase d,CatalogItem c,long firstSeen,long now){
        String payload;try{payload=c.json().toString();}catch(Exception e){return;}
        ContentValues v=new ContentValues();
        v.put("kind",c.kind);v.put("provider",c.title.provider);v.put("lang",c.languageCode.isEmpty()?c.title.language:c.languageCode);v.put("year",c.year());v.put("release",c.releaseDate);
        v.put("popularity",c.popularity);v.put("rating",c.rating);v.put("height",c.verifiedHeight);v.put("playable",c.playable?1:0);
        v.put("norm",MetadataMatcher.normalize(CatalogDeduplication.baseName(c.title.name)));v.put("updated_at",now);v.put("payload",payload);
        if(d.update("items",v,"key=?",new String[]{c.key()})==0){v.put("key",c.key());v.put("first_seen",firstSeen);d.insertWithOnConflict("items",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    }
    static String signature(List<CatalogItem> items){
        StringBuilder s=new StringBuilder();for(CatalogItem c:items)s.append(c.key()).append('=').append(c.signature()).append('\n');
        return PlaylistParser.stable(s.toString());
    }
    @Override public synchronized List<CatalogItem> row(String rowId,int limit){
        return items("SELECT i.payload,i.first_seen,h.ok_at,h.fail_at FROM row_items r JOIN items i ON i.key=r.key LEFT JOIN health h ON h.key=i.key WHERE r.row_id=? ORDER BY r.pos LIMIT "+Math.max(1,limit),rowId);
    }
    @Override public synchronized List<CatalogItem> recent(String kind,int limit){
        return items("SELECT i.payload,i.first_seen,NULL,NULL FROM items i WHERE i.kind=? AND EXISTS(SELECT 1 FROM row_items r WHERE r.key=i.key) ORDER BY i.first_seen DESC,i.release DESC LIMIT "+Math.max(1,limit),kind);
    }
    @Override public synchronized List<CatalogItem> search(String text,int limit){
        String n=MetadataMatcher.normalize(text);if(n.isEmpty())return new ArrayList<>();
        return items("SELECT i.payload,i.first_seen,h.ok_at,h.fail_at FROM items i LEFT JOIN health h ON h.key=i.key WHERE i.norm LIKE ? ORDER BY i.popularity DESC LIMIT "+Math.max(1,limit),"%"+n.replace("%","")+"%");
    }
    @Override public synchronized List<CatalogItem> playable(String normalizedName,int year,String kind){
        return items("SELECT i.payload,i.first_seen,NULL,NULL FROM items i WHERE i.playable=1 AND i.norm=? AND i.kind=? AND (i.year=? OR ?=0 OR i.year=0)",normalizedName,kind,String.valueOf(year),String.valueOf(year));
    }
    private List<CatalogItem> items(String sql,String...args){
        List<CatalogItem> out=new ArrayList<>();
        try(Cursor c=db().rawQuery(sql,args)){
            while(c.moveToNext()){
                try{CatalogItem item=CatalogItem.from(new JSONObject(c.getString(0)));item.firstSeen=c.getLong(1);
                    if(!c.isNull(2)||!c.isNull(3)){long ok=c.isNull(2)?0:c.getLong(2),fail=c.isNull(3)?0:c.getLong(3);item.health=ok>=fail&&ok>0?"ok":fail>0?"failed":"";item.healthCheckedAt=Math.max(ok,fail);}
                    out.add(item);}
                catch(Exception ignored){/* skip a corrupt row rather than lose the catalog */}
            }
        }catch(Exception ignored){/* store unavailable: callers show their empty state */}
        return out;
    }
    @Override public synchronized CatalogFreshnessTracker.State state(String source){
        try(Cursor c=db().rawQuery("SELECT synced_at,attempt_at,next_at,failures,items,message FROM sources WHERE source=?",new String[]{source})){
            if(!c.moveToFirst())return null;
            CatalogFreshnessTracker.State s=new CatalogFreshnessTracker.State();
            s.syncedAt=c.getLong(0);s.attemptAt=c.getLong(1);s.nextAttemptAt=c.getLong(2);s.failures=c.getInt(3);s.items=c.getInt(4);s.message=c.isNull(5)?"":c.getString(5);return s;
        }catch(Exception e){return null;}
    }
    @Override public synchronized void saveState(String source,CatalogFreshnessTracker.State s){
        ContentValues v=new ContentValues();v.put("source",source);v.put("synced_at",s.syncedAt);v.put("attempt_at",s.attemptAt);v.put("next_at",s.nextAttemptAt);v.put("failures",s.failures);v.put("items",s.items);v.put("message",s.message);
        try{db().insertWithOnConflict("sources",null,v,SQLiteDatabase.CONFLICT_REPLACE);}catch(Exception ignored){/* freshness is advisory */}
    }
    @Override public synchronized CatalogHttp.Entry get(String key){
        try(Cursor c=db().rawQuery("SELECT etag,last_modified,body,fetched_at FROM http WHERE key=?",new String[]{key})){
            return c.moveToFirst()?new CatalogHttp.Entry(c.getString(0),c.getString(1),c.getString(2),c.getLong(3)):null;
        }catch(Exception e){return null;}
    }
    @Override public synchronized void put(String key,CatalogHttp.Entry e){
        ContentValues v=new ContentValues();v.put("key",key);v.put("etag",e.etag);v.put("last_modified",e.lastModified);v.put("body",e.body);v.put("fetched_at",e.fetchedAt);
        try{db().insertWithOnConflict("http",null,v,SQLiteDatabase.CONFLICT_REPLACE);}catch(Exception ignored){/* cache is optional */}
    }
    @Override public synchronized void saveHealth(String key,boolean ok,long now){
        try{SQLiteDatabase d=db();
            d.execSQL("INSERT OR IGNORE INTO health(key,ok_at,fail_at,fails) VALUES(?,0,0,0)",new Object[]{key});
            if(ok)d.execSQL("UPDATE health SET ok_at=?,fails=0 WHERE key=?",new Object[]{now,key});
            else d.execSQL("UPDATE health SET fail_at=?,fails=fails+1 WHERE key=?",new Object[]{now,key});
        }catch(Exception ignored){/* advisory */}
    }
    @Override public synchronized Map<String,long[]> health(){
        Map<String,long[]> out=new HashMap<>();
        try(Cursor c=db().rawQuery("SELECT key,ok_at,fail_at,fails FROM health",null)){while(c.moveToNext())out.put(c.getString(0),new long[]{c.getLong(1),c.getLong(2),c.getLong(3)});}
        catch(Exception ignored){/* none */}
        return out;
    }
    /** Release years present in the synced catalog, newest first; drives the Year filter instead of a fixed list. */
    synchronized List<Integer> years(String kind){
        List<Integer> out=new ArrayList<>();
        try(Cursor c=db().rawQuery("SELECT DISTINCT year FROM items WHERE kind=? AND year>1900 ORDER BY year DESC LIMIT 80",new String[]{kind})){while(c.moveToNext())out.add(c.getInt(0));}
        catch(Exception ignored){/* empty filter list */}
        return out;
    }
    /** Original-language codes present in the synced catalog with their title counts. */
    synchronized Map<String,Integer> languages(String kind){
        Map<String,Integer> out=new LinkedHashMap<>();
        try(Cursor c=db().rawQuery("SELECT lang,COUNT(*) n FROM items WHERE kind=? AND lang<>'' GROUP BY lang ORDER BY n DESC",new String[]{kind})){while(c.moveToNext())out.put(c.getString(0),c.getInt(1));}
        catch(Exception ignored){/* empty filter list */}
        return out;
    }
    /** Trims the conditional-request cache; keeps it from growing without bound across years of use. */
    synchronized void trimHttp(long now){try{db().execSQL("DELETE FROM http WHERE fetched_at<?",new Object[]{now-14L*24*60*60*1000});}catch(Exception ignored){/* best effort */}}
    private static long count(SQLiteDatabase d,String sql){try(Cursor c=d.rawQuery(sql,null)){return c.moveToFirst()?c.getLong(0):0;}}
    private static String string(SQLiteDatabase d,String sql,String arg){try(Cursor c=d.rawQuery(sql,new String[]{arg})){return c.moveToFirst()&&!c.isNull(0)?c.getString(0):"";}}
}
