# Live TV additions, Sun Network and Catch-Up TV (2026-11-03)

This change is **additive**. The existing Live TV directory (iptv-org) is unchanged: the same 800 channels, ids, names, languages, categories, stream URLs, favorites, history, health checks and retirement rules. New entries are appended after it, and every addition sits behind a feature flag.

## 1. Baseline of the existing Live TV

- **Source:** `LiveTvSource` → `IndianTvProvider` → `PlaylistParser`. It reads `countries/in.m3u` intersected with 15 language feeds from https://iptv-org.github.io/iptv/, plus the remainder as "Other Indian languages".
- **Capture:** `tools/live-tv-audit.js` (read-only, mirrors the parser) on 2026-11-03 07:23 UTC.
  - 14 of 15 language feeds loaded; 906 directory entries.
  - **800 channels are held by the app.** The other 106 have HTTP-only streams, which `PlaylistParser.entries` drops before they reach the app.
  - The list is in [`live-tv-baseline.tsv`](live-tv-baseline.tsv): id, name, language, category, logo, stream count, hosts and types. Full URLs are not recorded.
- **Health:** stored per device in `catalog_v2.db` (table `health`), so it cannot be captured from the build machine. It is untouched by this change.
- **Regression tests:** `LiveTvRegressionTest` loads a real, trimmed subset of the feeds (`app/src/test/resources/live/`, built by `tools/make-live-fixture.js`). It checks ids, names, languages, categories, stream hosts and the HTTPS filter against the audit snapshot (`golden.tsv`), and proves:
  - With the flags off, the merged list is the existing list object itself.
  - With the flags on, every existing channel stays at the same position with the same signature and stream URL.

## 2. Sun Network inventory (official list: https://www.sun.in/television.aspx)

Sun TV Network filed a DMCA notice with iptv-org ([iptv-org/iptv#34901](https://github.com/iptv-org/iptv/issues/34901)). Twelve of these channels are on iptv-org's blocklist. **No public stream for any Sun channel is authorized**, so Saandio adds none.

All 37 channels are available in **Sun NXT** (`com.suntv.sunnxt`; its Play listing mentions Android TV) with a subscription. Sun NXT publishes no per-channel deep link, so the handoff opens the app's home screen.

These columns are the same for every row, so the table leaves them out:

- **Official provider:** Sun NXT, subscription.
- **Authorized free stream:** none, except Sun News (below).
- **Logo:** not bundled; a text placeholder is shown.
- **EPG:** none permitted.
- **Region:** whatever Sun NXT allows.

| Channel | Language | In Saandio before this change | iptv-org id(s) | iptv-org blocklist (DMCA) | Android TV route added | State shown |
|---|---|---|---|---|---|---|
| Sun TV | Tamil | Yes, as "Sun TV HD" (third-party restream) | SunTV.in | – | Hidden as duplicate; "Also on Sun NXT" on the existing channel | (existing channel) |
| Sun TV HD | Tamil | Yes (same entry) | SunTV.in | – | Same | (existing channel) |
| KTV | Tamil | No | KTV.in | Yes | Sun NXT handoff | Subscription required |
| KTV HD | Tamil | No | – | – | Sun NXT handoff | Subscription required |
| Sun Music | Tamil | No | SunMusic.in | Yes | Sun NXT handoff | Subscription required |
| Sun Music HD | Tamil | No | – | – | Sun NXT handoff | Subscription required |
| Sun News | Tamil | No | SunNews.in | Yes | **Official YouTube channel** ([@Sunnewstamil](https://www.youtube.com/channel/UCYlh4lH762HvHt6mmiecyWQ), free; live status not verified) | Unknown |
| Adithya TV | Tamil | No | AdithyaTV.in | Yes | Sun NXT handoff | Subscription required |
| Chutti TV | Tamil | No | ChuttiTV.in | Yes | Sun NXT handoff | Subscription required |
| Sun Life | Tamil | No | SunLife.in | Yes | Sun NXT handoff | Subscription required |
| Gemini TV | Telugu | Listed by iptv-org as "Sun Gemini", HTTP-only, so not in the app | GeminiTV.in, SunGemini.in | – | Sun NXT handoff | Subscription required |
| Gemini TV HD | Telugu | No | (same) | – | Sun NXT handoff | Subscription required |
| Gemini Movies | Telugu | No | GeminiMovies.in, SunGeminiMovies.in | – | Sun NXT handoff | Subscription required |
| Gemini Movies HD | Telugu | No | (same) | – | Sun NXT handoff | Subscription required |
| Gemini Music | Telugu | No | GeminiMusic.in, SunGeminiMusic.in | – | Sun NXT handoff | Subscription required |
| Gemini Music HD | Telugu | No | (same) | – | Sun NXT handoff | Subscription required |
| Gemini Comedy | Telugu | No | GeminiComedy.in, SunGeminiComedy.in | – | Sun NXT handoff | Subscription required |
| Kushi TV | Telugu | No | KushiTV.in | Yes | Sun NXT handoff | Subscription required |
| Gemini Life | Telugu | No | GeminiLife.in, SunGeminiLife.in | – | Sun NXT handoff | Subscription required |
| Udaya TV | Kannada | No | SunUdaya.in | – | Sun NXT handoff | Subscription required |
| Udaya TV HD | Kannada | No | (same) | – | Sun NXT handoff | Subscription required |
| Udaya Movies | Kannada | No | SunUdayaMovies.in | – | Sun NXT handoff | Subscription required |
| Udaya Music | Kannada | No | SunUdayaMusic.in | – | Sun NXT handoff | Subscription required |
| Udaya Comedy | Kannada | No | SunUdayaComedy.in | – | Sun NXT handoff | Subscription required |
| Chintu TV | Kannada | No | ChintuTV.in | Yes | Sun NXT handoff | Subscription required |
| Surya TV | Malayalam | Yes, as "Sun Surya HD" (third-party restream) | SunSurya.in | – | Hidden as duplicate; "Also on Sun NXT" | (existing channel) |
| Surya TV HD | Malayalam | Yes (same entry) | SunSurya.in | – | Same | (existing channel) |
| Surya Movies | Malayalam | No | SunSuryaMovies.in | – | Sun NXT handoff | Subscription required |
| Surya Music | Malayalam | No | SunSuryaMusic.in | – | Sun NXT handoff | Subscription required |
| Surya Comedy | Malayalam | No | SunSuryaComedy.in | – | Sun NXT handoff | Subscription required |
| Kochu TV | Malayalam | No | KochuTV.in | Yes | Sun NXT handoff | Subscription required |
| Sun Bangla | Bengali | No | SunBangla.in | Yes | Sun NXT handoff | Subscription required |
| Sun Bangla HD | Bengali | No | – | – | Sun NXT handoff | Subscription required |
| Sun Marathi | Marathi | No | SunMarathi.in | Yes | Sun NXT handoff | Subscription required |
| Sun Marathi HD | Marathi | No | – | – | Sun NXT handoff | Subscription required |
| Sun Neo | Hindi | No | SunNeo.in | Yes | Sun NXT handoff | Subscription required |
| Sun Neo HD | Hindi | No | – | – | Sun NXT handoff | Subscription required |

- **Playback verification:** none of the 37 was played. Saandio does not stream them, and Sun NXT was not installed on the test emulator.
- **Existing restreams:** "Sun TV HD" and "Sun Surya HD" in the directory are third-party restreams of Sun channels whose licensing Saandio has not verified, and Sun has asserted rights over its channels. They were kept in Turn 8, because that change could not remove channels. **Turn 9: removed on the owner's decision.** `SunNetworkChannels.rightsAsserted` removes any directory channel whose iptv-org id is in the Sun list. The filter applies in `IndianTvProvider.load`, when saved rows are read and in the add-on caches. The 4 Sun TV / Surya TV entries then appear as Sun NXT handoffs. The directory goes from 800 to 798 channels, and every other channel is unchanged.
- The iptv-org database renamed Gemini, Udaya and Surya to "Sun Gemini", "Sun Udaya" and "Sun Surya" in 2026. Deduplication matches both the old and new ids.

## 3. Sources reviewed

| Source | Finding | Decision |
|---|---|---|
| [iptv-org/iptv](https://github.com/iptv-org/iptv) | Public directory of stream links; no rights granted. Sun's DMCA notice is in its issues. | The existing directory is kept as is. No new streams added from it. |
| [iptv-org/database](https://github.com/iptv-org/database) | Channel metadata, owners and blocklist. | Used read-only to recognise duplicates (ids) and to audit DMCA status. |
| [iptv-org/epg](https://github.com/iptv-org/epg) | Grabbers that scrape guide sites; no hosted guide. Permission depends on each site. | Not used. |
| [mitthu786/tvepg](https://github.com/mitthu786/tvepg) | GPL-3.0. EPG and catch-up taken from JioTV, Tata Play, ZEE5, Sun NXT and SonyLIV apps. | **Rejected:** subscription-derived data with no permission. |
| [arshadakl/streamhub](https://github.com/arshadakl/streamhub) | No license. A web UI over iptv-org-style lists. | Ideas only; no code taken. |
| [david0154/david-tv](https://github.com/david0154/david-tv) | No license asserted. A desktop IPTV app claiming "800+ free Indian channels". | Rejected; no code or lists taken. |
| GitHub topics live-tv-india / indian-channel; free-codecs IPTV guide | Further aggregators of the same public lists. | Nothing authorized to add. |
| Two Reddit threads | No links were provided in the request. | Not reviewed. |
| [Sun NXT](https://www.sunnxt.com/) | Official Sun app with subscription live TV and catch-up. No public API, partner feed or channel deep links. | App-launch handoff only. |
| [Prasar Bharati WAVES](https://prasarbharati.gov.in/en/waves/) | Official OTT app (`com.prasarbharati.android` on Play). No public API or embeddable feed found. | Allowlisted for handoff. Not used for listings. |
| Doordarshan / Prasar Bharati YouTube channels | Official, verified, free; public uploads can be listed through the YouTube Data API and played in the YouTube app. | **Adopted for Catch-Up TV.** |

## 4. Architecture

- **Existing layer:** `LiveTvSource` (existing repository, unchanged). Its health probe (`CatalogSyncCoordinator.checkHealth`) and Online / Offline / Unchecked labels are unchanged. Entries without an HTTPS stream (official handoff entries) are never sent to the probe.
- **Additional layer:**
  - `AdditionalChannelProvider`: secondary sources. Today that is `SunNetworkChannels`, with 37 entries, no stream URLs, `SUBSCRIPTION_REQUIRED`, and Sun NXT or YouTube handoff.
  - `ChannelDeduplicationService`: the existing channel always wins. A duplicate is matched by iptv-org id, or by the same name (ignoring "HD" and punctuation) in the same language. It is hidden and offered as "Also on Sun NXT" on the existing channel.
  - `UnifiedLiveTv`: merges the existing directory (first, unchanged) with the de-duplicated additions. A failing provider is ignored.
- **States:** `ChannelState` has LIVE, CHECKING, TEMPORARILY_UNAVAILABLE, SUBSCRIPTION_REQUIRED, GEO_RESTRICTED, UNSUPPORTED and UNKNOWN.
  - **LIVE requires a recent playback validation:** the player reached READY and position advanced for 10 s (`ChannelValidation`), within the last 6 hours.
  - A reachable URL or a valid manifest is not LIVE.
  - Validations are stored separately (`astra_live_validation` prefs), so existing health history is not rewritten.
- **Handoff:** `OfficialAppHandoff` opens only allowlisted apps: Sun NXT, YouTube (TV), and WAVES.
  - YouTube references must be a video id or a channel id; never a media URL.
  - A missing app goes to its Play Store page. There is no browser fallback.
  - The manifest declares `<queries>` for these packages only.
- **Feature flags:** `FeatureFlags`. Defaults come from `local.properties`, and a developer can override each one at runtime in Settings → Diagnostics.
  - `ENABLE_NEW_LIVE_SOURCES` (`feature.new_live_sources`, default on)
  - `ENABLE_SUN_NETWORK_INTEGRATION` (`feature.sun_network`, default on; requires the previous flag)
  - `ENABLE_CATCHUP_TV` (`feature.catchup_tv`, default on)
  - `ENABLE_EXTENDED_EPG` (`feature.extended_epg`, default **off**)
  - **Full rollback:** turn off `ENABLE_NEW_LIVE_SOURCES`, and Live TV is exactly the original directory.

## 5. Programme guide (EPG)

- **Code in place:**
  - `Xmltv`: streaming SAX parser, external entities disabled, a 12-hour window, explicit UTC offsets.
  - `EpgMapping`: an exact iptv-org id wins. A display name is used only when it is unique on both sides; anything ambiguous stays unmapped.
  - `EpgRepository`: HTTPS only, gzip, 80 MB cap, 3-hour memory cache.
- **UI:** "Now · programme" on channel cards; now/next with times in the device time zone, a progress bar and the description on channel details; and a "TV guide" list.
- **Status: off.** No XMLTV source with permission for this use was identified. To enable it, set `epg.xmltv.url=https://…` and `feature.extended_epg=true` in `local.properties`. The screen states that guide data does not mean a stream works.

## 6. Catch-Up TV tab (seventh tab)

> **Turn 9 change:** Catch-Up TV now lists TamilTVSerial and TeluguStream only (section 7). Everything below is behind `feature.official_catchup_archives`, which is off by default. Sun Network appears only in Live TV.

- **Tab order (Turn 10):** Home, Movies, TV Shows, **Catch-Up TV**, Live TV, Sports Live. HD Movies was removed and All Movies renamed.
- **Rows:** Continue Watching, Recently Aired (last 7 days), Latest Episodes (one per series), Telugu / Tamil / Kannada / Malayalam / Hindi Serials, Doordarshan Classics, Free Catch-Up Programs, and Subscription catch-up (Sun NXT tiles).
- **Sources:** `CatchupChannels`, the official verified YouTube channels of DD Tamil, DD Saptagiri, DD Yadagiri, DD Chandana, DD Malayalam, Doordarshan National and Prasar Bharati Archives.
  - They are listed by `CatchupSource` through the backend's `/api/channel-uploads`. That route is server-side allowlisted to exactly these channels, and the YouTube key stays on the server. Debug builds can use a developer key.
  - Shorts, promos, trailers, live streams, private videos, region-blocked videos and future premieres are excluded.
- **Model:** `CatchupEpisode` has every requested field (episodeId, providerId, seriesId, seriesTitle, episodeTitle, language, genre, seasonNumber, episodeNumber, airDate, availableFrom, availableUntil, posterUrl, backdropUrl, duration, accessType, playbackType, playbackReference, lastValidatedAt).
  - Rows are newest first.
  - Artwork is the episode's own thumbnail.
- **Playback:** a free episode opens in the YouTube app (`YOUTUBE_HANDOFF`). Sun NXT tiles are `SUBSCRIPTION` and open Sun NXT.
  - Continue Watching lists the episodes opened from Saandio. The YouTube app keeps the resume position itself.
- **Not used:** the existing TamilTVSerial and TeluguStream add-ons are not part of Catch-Up TV, because they are not authorized sources.
- **Needs:** a deployed `backend/` with `YOUTUBE_API_KEY`. Without it, the tab shows "Catch-Up TV needs the Saandio Catalog API…" and only the Sun NXT tiles.

## 7. Catch-Up TV sources: TamilTVSerial and TeluguStream (Turn 9)

**Investigation (`tools/source-investigation.js`, `tools/source-investigation-2.js`, 2026-11-03, read-only):**
- Both sites answer HTTP 200 and are WordPress sites. Each has `/feed/` and `/wp-json/`, but no terms page, no DMCA page and no partner or API documentation.
- Their newest posts were same-day episodes of broadcaster serials:
  - tamiltvserial.com: "Bhairavi 03-11-2026 | Episode 8 | Sun TV Serial"
  - telugustream.com: "Chamanthi 03-11-2026 | Episode 576 | Zee Telugu Tv Serial"
- Their home pages list Sun TV, Star Vijay, Zee, Star Maa, Gemini TV and ETV. Those networks license their episodes to Sun NXT, JioHotstar, ZEE5 and ETV Win.
- A public RSS feed or REST endpoint is not a grant of redistribution rights.

**Result:**
- Neither site offers authorized access. No official external viewing option exists either, because the sites are not the rights holders.
- `TamilTvSerialProvider` and `TeluguStreamProvider` are therefore not ready. They request nothing, and their rows show the reason.
- The legacy `tamil` and `telugu` add-ons that scraped the sites are blocked in `ProviderRepository.unauthorized`, covering catalog, details, episodes and streams. Their saved lists are no longer shown.

**Implementation (ready for an authorized feed):**
- `SerialCatchupProvider.Feed` is the hook an authorized partner feed plugs into.
- Each provider has two rows:
  - **Latest … Episodes:** every episode, newest air date first, capped at 200.
  - **… Serials:** one tile per series, carrying its newest episode.
- Episodes from another provider, without an air date or dated in the future are dropped.
- **Recently Aired** merges both providers' last 7 days. **Continue Watching** lists only episodes from these two providers.
- Each item carries the series name, language, episode number, air date, thumbnail and the playback route the feed names, opened through `OfficialAppHandoff`.
- Saandio never resolves video links for these providers.
- **Tests:** `ContentSourcesTest`.

## 8. Catch-Up TV episode listing (Turn 10)

At the owner's request Catch-Up TV now lists both sites' episodes. Viewing still goes only to the broadcasters' own apps.

**Discovery (metadata only):**
- **Fetching:** `WordPressSerialFeed` reads `https://www.tamiltvserial.com/feed/` and `https://telugustream.com/feed/`, then `?paged=2…`.
  - Both sites' robots.txt disallow only `/wp-admin/`. The feeds send ETag and Last-Modified, which `CatalogHttp` uses for conditional requests.
  - Pages are 400 ms apart. Paging stops at a 404 or at the first page whose episodes are all known. The first sync reads at most 12 pages per site; later hourly refreshes usually read one.
  - Pages per site: TamilTVSerial has 18 items, TeluguStream 10.
- **Parsing:** `SerialFeedParser` reads post titles of the form "Show dd-mm-yyyy | Episode N | Broadcaster Serial|Show", plus the post categories.
  - It keeps the show name (from the title, because category spellings vary), the episode number, the air date (the title date in Asia/Kolkata, or the post date when the title has none), the broadcaster and Serial / TV show.
  - Post links, bodies, images and embedded video links are discarded.
  - Episode ID: `provider/show-slug/eN`, or `/dYYYY-MM-DD` when there is no number. A repost of the same episode collapses to one entry.
- **Retention:** `SerialCatchupProvider` keeps previously stored episodes for 14 days (up to 900 per site), newest first. Ties break on episode number, then post time.

**Broadcaster mapping (`CatchupBroadcasters`, Play listings checked with `tools/broadcaster-apps-probe.js`):**

| Broadcaster | Language | App (package) |
|---|---|---|
| Sun TV, KTV / Gemini TV / Udaya TV / Surya TV | Tamil / Telugu / Kannada / Malayalam | Sun NXT (`com.suntv.sunnxt`) |
| Star Vijay, Colors Tamil / Star Maa / Colors Kannada, Star Suvarna / Asianet | Tamil / Telugu / Kannada / Malayalam | JioHotstar (`in.startv.hotstar`) |
| Zee Tamil / Zee Telugu / Zee Kannada / Zee Keralam | per channel | ZEE5 (`com.graymatrix.did`) |
| ETV | Telugu | ETV Win (`com.etvwin.mobile`) |
| Sony Vizha | Tamil | Sony LIV (`com.sonyliv`) |
| Kalaignar, Polimer, Raj, Jaya | Tamil | none: "No official viewing option" |

- The language comes from the broadcaster, so a Zee Telugu serial on TamilTVSerial is Telugu. When the broadcaster is unknown, the site's language is used.
- All app packages are in the `OfficialAppHandoff.ALLOWED` allowlist and the manifest `<queries>`.

**Screens:**
- **Catch-Up TV tab:** Language (All / Telugu / Tamil / Kannada / Malayalam), "All shows", "Check for new episodes", and per-site status lines. Rows:
  - Continue Watching
  - TV shows · TeluguStream
  - Latest episodes · TeluguStream
  - TV shows · TamilTVSerial
  - Latest episodes · TamilTVSerial
  - Recently aired · both sites
- **All Catch-Up shows:** a grid of every show from both sites, filtered by language, most recently updated first.
- **Show page:** name, language, broadcaster, genre, latest episode and air date, "Listed by …", a "Watch in <app>" button with an explanatory note, then the episode grid, newest first.
- **Episode page:** episode, broadcaster, air date, "Watch in <app>" and "All episodes of <show>".
- **Back** returns to the previous screen and refocuses the card that was opened.
- **Watch:** opens the broadcaster's app on its home screen, because none of these apps publishes episode deep links. If the app is missing, it opens the Play Store page. Opened episodes go to Continue Watching. The note says Saandio has not checked that the episode is in the app.

**Not built, and why:** an in-app browser for the two sites, including `target="_blank"` / `window.open` new-window handling. The sites' players embed re-hosted copies of the broadcasters' episodes from third-party video hosts. Opening them in Saandio would make the app a viewer for unlicensed copies, whatever popup safeguards were added. The app's only WebView (the YouTube trailer player) keeps multiple windows disabled, refuses `onCreateWindow`, blocks top-level navigation and non-YouTube hosts, and has cookies off.

**Live check:** `tools/catchup-live-check/run.ps1`, run 2026-11-03, read 3 feed pages from each site with the app's own parser:

| | TamilTVSerial | TeluguStream |
|---|---|---|
| Unique episodes | 54 | 30 |
| Shows | 47 | 30 |
| Languages | Tamil 51, Telugu 1, Malayalam 1, Kannada 1 | Telugu 30 |
| Episodes without an official app | 0 | 0 |
| Stored items containing a URL | 0 | 0 |

**Tests:** `CatchupFeedTest` (17), `ContentSourcesTest`.
