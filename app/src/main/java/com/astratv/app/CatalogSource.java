package com.astratv.app;

import java.util.*;

/**
 * One provider adapter. Discovery only: playable streams are resolved separately by
 * {@link PlaybackSourceResolver}. A failing source never blocks the others.
 */
interface CatalogSource {
    String id();
    String label();
    long defaultTtlMs();
    /** Cheap enough (conditional requests, small payloads) to re-check on every launch and foreground. */
    boolean checkOnForeground();
    /** False when the source is not configured or not authorized; nothing is requested. */
    boolean ready();
    String notReadyReason();
    /** Rows this source owns. A row is replaced transactionally only when its fetch succeeds. */
    List<Batch> fetch(CatalogHttp http)throws Exception;

    final class Batch {
        final String rowId;final List<CatalogItem> items;final boolean notModified;
        Batch(String rowId,List<CatalogItem> items,boolean notModified){this.rowId=rowId;this.items=items;this.notModified=notModified;}
    }
}
