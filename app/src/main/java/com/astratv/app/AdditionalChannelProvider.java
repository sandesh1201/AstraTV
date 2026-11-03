package com.astratv.app;

import java.util.List;

/**
 * A secondary Live TV source layered on top of the existing iptv-org directory. Providers only
 * add entries; {@link ChannelDeduplicationService} drops any entry that duplicates a channel the
 * directory already has, so existing channels, ids and stream URLs are never replaced.
 */
interface AdditionalChannelProvider {
    String id();
    String label();
    /** Channel entries with an authorized route (official app or official YouTube handoff); never an unverified stream. */
    List<CatalogItem> channels();
}
