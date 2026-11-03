package com.astratv.app;

import java.util.*;

/** Local catalog database. {@link CatalogStore} is the SQLite implementation used on device. */
interface CatalogStorage extends CatalogHttp.Cache {
    /**
     * Replaces the given rows in one transaction and returns the ids whose contents changed.
     * New keys get a first-seen time of {@code now}, or 0 on the source's first successful sync (baseline).
     */
    Set<String> replaceRows(String source,List<CatalogSource.Batch> batches,long now);
    List<CatalogItem> row(String rowId,int limit);
    /** Movies or series across all rows, deduplicated by key, newest first-seen first. */
    List<CatalogItem> recent(String kind,int limit);
    List<CatalogItem> search(String text,int limit);
    /** Playable titles from authorized providers whose normalised name and year match. */
    List<CatalogItem> playable(String normalizedName,int year,String kind);
    CatalogFreshnessTracker.State state(String source);
    void saveState(String source,CatalogFreshnessTracker.State state);
    /** Availability is stored apart from metadata so a probe never rewrites catalog rows. */
    void saveHealth(String key,boolean ok,long now);
    Map<String,long[]> health();
}
