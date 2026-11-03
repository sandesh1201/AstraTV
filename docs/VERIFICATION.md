# Verification · research edition 0.3

## Turn 10 · Six tabs, Catch-Up episodes, Einthusan removal (2026-11-03)

**Automated checks:** `gradlew.bat testDebugUnitTest assembleDebug lintDebug` passes.
- 224 unit tests ran, 0 failed, 0 skipped (counted from the JUnit XML).
- The 17 tests in the new `CatchupFeedTest` cover:
  - the six tabs
  - RSS parsing and language mismatches
  - episodes without an official app
  - no URLs stored, entity decoding
  - the broadcaster table and date labels
  - newest-first ordering and de-duplication
  - show tiles and a show's episodes, Continue Watching
  - Sun channels kept out of Catch-Up, 14-day retention
  - feed paging, stopping at known episodes, an unreachable feed, HTTPS-only feeds
- `EinthusanContractTest` was deleted with the integration. `MovieSourcesTest`, `ContentSourcesTest`, `PlaybackPolicyTest` and `PlaybackSourceResolverTest` were updated for it.
- Backend: `node --test` passes 18 of 18.
- Lint: 0 errors, 44 warnings.
- Debug APK in `dist/SaandioTV-debug.apk`: 26,139,948 bytes, SHA-256 `935503E4B3A1ECD1B582997096FF8F9804F21A7ED20A0D13B853A7184976740D`.

**Live feed check** (`tools/catchup-live-check/run.ps1`, using the app's own parser and pager):
- TamilTVSerial: 3 pages (HTTP 200), 54 episodes, 47 shows. Every episode is mapped to an official app.
- TeluguStream: 3 pages, 30 episodes, 30 shows. Every episode is mapped to an official app.
- No stored item contains a URL.

**Emulator session:** Pixel_9a phone AVD with the display forced to 1920x1080, not an Android TV image. Screenshots are in `build/emulator/`.
- **Navigation:** the six tabs are shown in order.
- **Movies:** shows the MovieRulz "not available" reason and "No titles to show", with no TMDB titles and no Play button.
- **Catch-Up TV:**
  - Live show tiles load from both sites, with poster fallback tiles.
  - After the sync, the status lines read "Updated just now". This was fixed this turn; before, they stayed on the launch-time status.
  - A show opens its page (language, broadcaster, latest episode, aired date, Watch in <app>) and its episode grid.
  - An episode opens its details. Back returns to the show with focus kept.
  - Watch in ZEE5, with ZEE5 not installed, opened the Play Store page. **No broadcaster app was installed, so no episode was watched.**
- **Live TV:**
  - Health badges load.
  - "Jonack" played in `PlayerActivity`: the media session reported PLAYING and video frames were captured.
  - Back returned to the Live TV grid.

**Not run:**
- No Android TV image or real TV, so D-pad focus was checked only through unit tests and earlier sessions.
- Trailer previews were not re-checked; that code is unchanged this turn.
- No broadcaster app was installed, so the handoff into Sun NXT, JioHotstar, ZEE5, ETV Win or Sony LIV was not seen.

## Turn 9 · Content-source mapping, Einthusan HD Movies, Catch-Up providers (2026-11-03)

**Automated checks:** `gradlew.bat testDebugUnitTest assembleDebug lintDebug` passes.
- 222 unit tests ran, 0 failed (counted from the JUnit XML).
- The 7 new ones are in `ContentSourcesTest`. They cover:
  - both serial providers without a feed are not ready, fetch nothing and explain why; their rows map to their own sources
  - the legacy `tamil` and `telugu` scrapers are blocked
  - authorized-feed rows are newest first, with one tile per series, and drop other providers, future dates and missing dates
  - Recently Aired merges both providers; Continue Watching keeps only them
  - the archive flag is off by default
  - HD filters offer only the years, genres and extra languages the provider listed, and filter correctly; no quality without a verified rendition
  - each diagnostics category is classified correctly, unsupported sources are reported once, and URLs are redacted
- `MovieSourcesTest` is updated for UNSUPPORTED and LOGIN_REQUIRED, including legacy AUTHORIZATION_REQUIRED rows.
- `LiveTvRegressionTest` is updated for the owner-approved removal of the Sun restreams. The other golden channels are unchanged, the two restreams are absent, all 37 Sun entries appear as Sun NXT handoffs, and only Sun-owned ids are removed (1 new test).
- Backend: `node --test` passes 18 of 18.
- Lint: 0 errors and 43 warnings, none in the files changed this turn.
- Debug APK in `dist/SaandioTV-debug.apk`: 26,140,037 bytes, SHA-256 prefix `9B70DF99B90497B8`.

**Investigations run:** `tools/source-investigation.js` and `tools/source-investigation-2.js` (GitHub API, einthusan.tv pages and terms, Play Store, both serial sites). Results are in [REFERENCE_PROJECTS.md](REFERENCE_PROJECTS.md) and [LIVE_TV_AND_CATCHUP.md § 7](LIVE_TV_AND_CATCHUP.md).

**Not run:**
- No emulator or device session. The new Catch-Up rows, HD filters, Diagnostics panel and "Open einthusan.tv" button were **not checked on screen**.
- No Einthusan, TamilTVSerial or TeluguStream title was listed or played. No authorized interface exists.
- No new Sun channel was added or played. Turn 8's inventory is unchanged.
- Live TV regressions rely on the unit tests (`LiveTvRegressionTest`, passing). No channel was played this turn.

## Turn 8 · Sun Network, Live TV additions, Catch-Up TV (2026-11-03)

**Automated checks:** `gradlew.bat testDebugUnitTest assembleDebug lintDebug` passes.
- 214 unit tests ran, 0 failed (counted from the JUnit XML). The 26 new ones:
  - `LiveTvRegressionTest` (3): real feed fixture checked against the audit snapshot, flags off gives the identical list, existing channels are unchanged in place, and the Sun duplicates are hidden.
  - `LiveTvAdditionsTest` (14): 37 Sun entries with no stream URLs, dedup with renamed ids, a failing provider is ignored, LIVE needs a playback validation, continuity, signature stability, YouTube reference validation, flag dependency, XMLTV offsets, now/next and progress, mapping refuses ambiguous names, external entities.
  - `CatchupTest` (9): title parsing, Shorts/promo/live/private/region/future filtering, newest-first rows, one entry per series in Latest Episodes, the full episode model round trip, Continue Watching, subscription tiles, and the app list matching the backend allowlist.
- Backend: `node --test` passes 18 of 18, including 4 new `/api/channel-uploads` tests (allowlist, key never returned, 503 without a key, quota mapping).
- Lint: 0 errors. `compileReleaseJavaWithJavac` passes.
- Debug APK in `dist/SaandioTV-debug.apk`: 26,140,037 bytes, SHA-256 prefix `81300B1042587463`.

**Audits run:**
- `tools/live-tv-audit.js`: 906 directory entries, 800 held by the app.
- `tools/official-sources-audit.js` and `tools/yt-channel-search.js`: Play listings and verified YouTube channel ids.
- `tools/sun-ids.js`: Sun-owned iptv-org ids and DMCA blocklist.

**Not run:**
- An emulator boot with 2 GB of RAM (about 4 GB free on the host) exited before Android finished booting, so the new tab, Live TV merge and handoff screens were **not checked on screen**.
- No Sun channel was played. Saandio streams none of them, and Sun NXT needs a subscription.
- Catch-Up rows were not loaded live. That needs `YOUTUBE_API_KEY` on a deployed backend (or a debug developer key).
- No XMLTV guide was loaded. No permitted source is configured.

## Turn 7 · Movie tab sources, movie playback, background trailers (2026-11-03)

**Automated checks:** `gradlew.bat testDebugUnitTest assembleDebug lintDebug` passes. `compileReleaseJavaWithJavac` passes.
- 188 unit tests in 29 classes: all ran, 0 failed. The 20 new ones are in `MovieSourcesTest` (11) and `TrailerMatcherTest` (9). They cover:
  - MovieRulz is never ready, explains why, returns no titles, and its titles are refused before any network call
  - Einthusan without authorization is empty and says "Authorization required"
  - with the simulation: only Einthusan titles appear, and the playable row holds exactly the 4 confirmed films
  - DRM-only, region-locked and insecure titles are UNAVAILABLE, each with a reason
  - a provider of another origin is ignored, and a rejected catalog (401) propagates so the last rows are kept
  - availability mapping, the store round-trip, and Watch Movie gating
  - poster badges say "▶ Play" only for AVAILABLE
  - trailer query format; rejection of reactions, reviews, fan edits, full movies, wrong years, unofficial channels, unembeddable, private, live and over-long videos
  - movie names containing reject words ("Event Horizon", "2012")
  - YouTube duration and region-block parsing; cache lifetimes and permanent player errors; the embed page and video ID validation
- `TrailerResolverTest` is updated for the new player-host allowlist.
- Backend: `node --test` passes 14 of 14, including 4 new `/api/trailer` tests (key never returned, 503 without a key, input limits, quota mapping).
- Lint: 0 errors, 38 warnings.
- Debug APK: 26,140,037 bytes, SHA-256 prefix `25B36F3088C872BF`.

**Emulator (Pixel_9a AVD, API 37, 1280×720):**
- HD Movies, before the simulation was enabled: it showed "Authorization required…" and no titles. No TMDB or Wikidata titles were substituted. The reason was printed three times; that is fixed, and the tab now shows it once.
- Home and TV Shows rendered from the saved catalog.

**Blocked by the environment:** the host had 0.6–1.9 GB of free RAM while the emulator needed 4 GB.
- System UI stopped responding. Screen capture returned black frames.
- Saandio's window could not get focus ("Input dispatching timed out (Application does not have a focused window)"). The ANR stack shows Saandio's main thread idle in `Looper.pollOnce`, so the app was not blocked.
- A cold boot with a software GPU hit the same fault at a load average of 40–50.
- **Not run:** the end-to-end path HD Movies (simulation) → Big Buck Bunny → Watch Movie → Media3 READY, the `SaandioPlayback` logcat trace, and the on-screen trailer preview and fallback.
- The movie-playback fix is therefore **not declared done**. It needs this run on a device or emulator with enough memory.

**Not possible without credentials or agreements:**
- MovieRulz: no authorized API exists.
- Einthusan: no public API and no agreement.
- YouTube search: needs `YOUTUBE_API_KEY` on a deployed `backend/`.
- TMDB official trailers: need the catalog service, or a developer key in debug builds.

## Turn 6 · Playback reliability, posters, diagnostics, background refresh (2026-11-03)

**Automated checks:** `gradlew.bat testDebugUnitTest assembleDebug lintDebug` passes.
- 168 unit tests passed, 0 failed. The 14 new ones are in `PlaybackReliabilityTest` and `CatalogReliabilityTest`. They cover:
  - stream type detection and rejection of non-media URLs (trailer, poster, web page, metadata API)
  - checks for insecure links, unavailable titles, region locks and DRM
  - signed-URL expiry formats (CloudFront, Akamai, AWS)
  - per-category error classification and bounded backoff
  - artwork URL validation, the broken-artwork memory and its size bound
  - diagnostics redaction
  - channel retirement and its daily re-check
  - regional language verification
- Lint: 0 errors, 35 warnings, down from 54.
- Media3 is upgraded from 1.8.0 to 1.11.1, the current stable release on Google Maven, so `compileSdk` is now 36. `targetSdk` stays at 35.
- Debug APK: 26,140,037 bytes, SHA-256 prefix `0730D28F3A29BE45`.

**Emulator (API 35, 1280×720):**
- Installed over the previous build and launched. Home rendered from the saved catalog.
- Wikidata titles without artwork now show the branded Saandio title card instead of two-letter initials.
- Cold start was 24 s on the first launch right after install (post-install optimization), then 10.4 s and 7.8 s on repeat launches with an emulator load average of 8–18. The previous build measured 6.8–8.0 s under similar load in Turn 5.
- No crash or ANR from Saandio was logged.

**Blocked by the environment:**
- With ~0.3–1.9 GB of free host RAM, the emulator's `system_server` died (`DeadSystemException`). After a reboot, System UI, the launcher and the system process kept showing ANR dialogs, which captured D-pad input.
- Because of that, these were **not run**: interactive HLS playback, the network drop during playback, the walkthrough of the Diagnostics screen, and the comparison of startup time against the previous build.
- Expired-link refresh and DRM playback need a real authorized provider. Neither is configured, so they are covered by unit tests only.

**Still not verified:** TMDB rows through the catalog proxy. No TMDB credential or deployed `backend/` exists on this machine.

## Turn 5 · Catalog without user keys (2026-11-02)

**Root cause:** no build carried a TMDB credential (`local.properties` had only `sdk.dir`; BuildConfig `TMDB_API_KEY` was empty), so the TMDB sources reported not-ready, no movie or TV rows synced, and the UI asked viewers for a key. Permissions, base URLs and parsing were fine.

**Automated checks:**
- `gradlew.bat testDebugUnitTest assembleDebug lintDebug`: 154 unit tests passed, 0 failed (14 new in `TmdbRouteTest`, `OpenCatalogSourcesTest`, `PlaybackSourceResolverTest`). Lint 0 errors.
- `backend/`: `node --test` 10 passed (whitelist, parameter filtering, client `api_key` dropped, bearer vs v3 key, 503 not configured, client token, TMDB 401 → 502, 429 → 503 with Retry-After, network error → 504, ETag/304, health never echoes secrets).
- Generated BuildConfig on this machine: `CATALOG_API_BASE=""`, `TMDB_API_KEY=""` (release always `""`).

**Real requests (no credentials on this machine):**
- Local proxy, no credential: `/api/health` 200 `tmdbConfigured:false`; `/api/tmdb?path=/discover/movie…` 503 `not_configured`.
- Local proxy with a deliberately invalid key against the real TMDB API: TMDB answered 401, proxy returned 502 `upstream_auth`; the key appeared in neither the response nor the server log.
- App adapters run on the JVM against the live services: Wikidata returned 157 films (Telugu 40, Tamil 33, Hindi 56, Malayalam 23, Kannada 5), 1 with a free poster, 0 playable. TVMaze returned 1 Indian show (Bigg Boss, S20 · E58) and 60 worldwide shows, all with artwork, 0 playable.
- **Not verified:** TMDB catalog content through the proxy, because no TMDB credential or deployed catalog service exists here.

**Emulator (Android TV 1280×720, API 35):**
- Fresh install (uninstalled first): no key prompt anywhere; Home showed Recently Added and the hero from Wikidata within ~15 s; TV Shows showed "Indian TV · latest episodes" and "New episodes worldwide" with TVMaze artwork, "Updated 6 min ago".
- Offline first launch (emulator had no network after a reboot): "Not loaded yet · No internet connection" and an empty-state message; the hero fell back to the bundled open-movie sample. No placeholder titles. When the network returned, rows filled without restarting the app.
- Offline relaunch with a saved catalog: Wikidata rows and hero appeared immediately.
- Fixed while testing: the Home hero was never filled because `shell()` cleared the hero views after the header was built (pre-existing); freshness labels of TMDB rows now refresh when their open-data stand-in syncs.
- Startup, AOT-compiled, measured back to back on the same loaded emulator: previous APK 5.2–8.6 s, this build 6.8–8.0 s, i.e. no measurable regression. The Turn 4 figure (2.9–3.0 s) was taken on an idle emulator.
- **Limited:** the host had ~1 GB free RAM; the emulator repeatedly showed "System UI / system isn't responding" and once soft-rebooted. Saandio "isn't responding" dialogs also appeared, but jdb showed the app's main thread idle in `MessageQueue.nativePollOnce` each time. The details-page and Settings walkthrough could not be completed interactively; the details logic (no Play for open-data titles) is covered by unit tests.

## Turn 4 · Catalog sync, six-tab navigation, performance (2026-11-02)

**Automated checks:** `gradlew.bat testDebugUnitTest assembleDebug lintDebug` passes.
- Unit tests: 140 passed, 0 failed. 59 of them are new, in these classes:
  - `CatalogFreshnessTrackerTest`
  - `CatalogDeduplicationTest`
  - `DiscoverQueryTest`
  - `TmdbParseTest`
  - `SportsEventsTest`
  - `CatalogHttpTest`
  - `CatalogSyncCoordinatorTest`
  - `PlaybackSourceResolverTest`
  - `FocusAnchorTest`
- Lint: 0 errors and 54 warnings. All 54 are categories the project already had: hardcoded UI strings, newer dependency versions, manifest orientation, the trailer WebView, the monochrome icon and draw allocation in the shimmer.

**Emulator:** Android TV, 1280×720, API 35, host GPU emulation, timed with `am start -W`.

| Measurement | Before | After |
|---|---|---|
| Cold start, debug APK as installed | 11.5–13.4 s, ~960 skipped frames | 4.3–5.2 s, 110–132 skipped frames |
| Cold start after `cmd package compile -m speed` (similar to a store install) | — | 2.9–3.0 s |
| Hot start from the launcher | — | 0.4–1.2 s |
| Tab switch (All Movies, TV Shows, Live TV, Sports Live) | — | no skipped-frame warnings |
| Memory after browsing | — | 88 MB PSS (Java heap 20 MB, native heap 34 MB) |

For comparison, the system Settings app cold-starts in 3.6–5.5 s on the same emulator. Its stall around the first frame is the emulator's GPU setup (`HardwareRenderer`), not app code.

**Runtime checks:**
- D-pad moves between tabs and icons.
- Card focus survives row refreshes.
- Back from an event detail page restores the focused card.
- Live TV probes show Online.
- TheSportsDB events show LIVE only for HT, 1H or 2H statuses confirmed against the API.
- Without a TMDB key, movie and TV tabs show an "Add TMDB key" prompt instead of placeholder content.
- Search keeps focus in the text field while results load.

**Not verified here:**
- TMDB rows, since this emulator has no TMDB key.
- Real licensed-partner playback, since no partner is connected.
- An EPG, since none is configured.

## Turn 3 · Einthusan simulation, VPN, catalog, player, security (2026-11-02)

**Automated gate:** `assembleDebug lintDebug testDebugUnitTest` passed with **81 tests, 0 failures**,
up from a 40-test baseline:

- CatalogRanking 4, CatalogSort 3, EinthusanContract 12, GenreTaxonomy 3, LanguageTaxonomy 3,
  MetadataMatcher 3, NetMirrorProvider 5, PlaybackPolicy 5, ProviderParser 10,
  ProviderRiskScanner 8, ResearchEdition 9, SecurityDashboard 5, TrailerResolver 4, Vpn 7.
- Lint: **0 errors, 50 warnings**: 28 `SetTextI18n`, 10 `GradleDependency`, and the rest are
  pre-existing minor categories.
- CI grep gates were run locally and pass.

**Emulator** (Android 16, `sdk_gphone64_x86_64`, debug APK 20,884,299 bytes):

| Area | Result |
| --- | --- |
| Home | No crash. Order follows Phase 6. Trending and Popular This Week stay hidden without a TMDB key, so Latest Releases leads |
| Navigation | New tabs render. Settings → Add-ons & sources still reaches every add-on |
| Einthusan simulation | Enabled from Add-ons, lists 7 titles, details show language/type and a small "Source" line |
| HLS playback | MediaSession PLAYING. ABR moved 224×100 → 784×350 → 1680×750 (burned-in variant labels). Quality menu listed 720p/350p/200p/100p + Auto. Fixed 720p held; speed 1.5× confirmed by the media session; Resume restored the position |
| Decoder fault | A one-off AAC decoder init failure on the overloaded emulator showed "This source could not play (ERROR_CODE_DECODER_INIT_FAILED)" with Retry. Retry recovered playback |
| VPN | Invalid configs rejected. Consent decline handled. A tunnel to a documentation-range endpoint never showed Protected. Require VPN refused playback. Disconnect and Remove verified. Details: `docs/VPN.md` |
| Dashboard | Hidden without a tester PIN. With a temporary PIN build: wrong PIN shows attempts left, correct PIN shows 18 findings (15 pass / 0 fail / 3 not testable), screenshots are black (`FLAG_SECURE`), and provider risk flagged MovieRulz `url.wrapped` |

**Bugs found by this validation and fixed:**

1. Quality label stuck at the first rendition. It now reads the decoder's input format, published
   through session extras.
2. VPN latency was measured on the normal network before the tunnel routed traffic. The probe is
   now bound to the VPN `Network` and runs only once the tunnel is protected.
3. The IPv6 warning asked users to enable leak protection when it was already on.
4. "Telugu Movies" and the other language shelves included TV series.
5. The source picker showed a duplicate label ("1080p · HLS · 1080p").
6. A wrong tester PIN gave no feedback.
7. Tunnel statistics lingered after config removal.
8. Proxy-relayed stream URLs were not flagged by the scanner (new rule `url.wrapped`).
9. A lint `AuthLeak` warning on a test URL inside a retest.

**Environment caveat:** the host had about 200 MB of free RAM during the run, and both the
emulator's System UI and the system process reported "not responding". Two Astra ANRs had the main
thread idle in framework code, with no focused window. Launch times from this run (10–21 s) are not
representative and are not reported as benchmarks. A physical-TV performance pass (Phase 10: start
time, frame pacing, memory) remains open.

**Not verified:** Trending and Popular This Week with a real TMDB key, a real WireGuard server
(handshake, throughput, exit location), DRM playback, and any real Einthusan endpoint, which is
blocked by design.

## Phase 5 · Performance (2026-11-02)

- Same `assembleDebug` / `lintDebug` / `testDebugUnitTest` gate as Phase 4: **37 tests, 0 failures**. Lint: **0 errors, 34 warnings**.
- Artwork now has a 32 MB memory LRU plus `cache/artwork-v1` disk files, viewport-deferred decode, and next-card prefetch. Shelves restore per-row focus. Release builds enable R8 (`minifyEnabled` / `shrinkResources`). Reduce motion already disables blur, parallax and trailers.
- Physical-TV 60fps, decoder/speaker and baseline-profile collection remain device measurements. This Java/Views app has no Compose compiler reports.

## Phase 4 · Trailer autoplay (2026-11-02)

- `assembleDebug`, `lintDebug`, `testDebugUnitTest` passed. **37 tests, 0 failures, 0 errors** (previous 33 plus 4 trailer-priority / YouTube-host tests). Lint: **0 errors, 34 warnings**.
- Focusing a card or hero for the configured delay (default 1.2s) crossfades a muted looping trailer when a source exists. Priority is provider HTTPS URL → TMDB official YouTube key → YouTube Data search. YouTube plays only through `YoutubeTrailerView` (official nocookie IFrame). Direct URLs play in a separate muted Media3 player, not PlaybackService. Focus change cancels immediately.
- TV checks: Home Open cinema (provider MP4 trailer); a TMDB-matched movie with a trailer key; Settings Autoplay off / sound on / delay cycle / Reduce motion. Confirm the WebView never navigates off youtube-nocookie.com and that Back still restores the native stack.

## Phase 3 · Metadata enrichment (2026-11-02)

- `assembleDebug`, `lintDebug`, `testDebugUnitTest` passed. **33 tests, 0 failures, 0 errors** (previous 30 plus 3 metadata-matching confidence tests). Lint: **0 errors, 33 warnings**.
- New `metadata_v1.db` cache (7-day TTL, manual matches persist). Library JSON, `astra` watchlist/review keys, provider sessions and the research log were not changed. Ratings badges render only when a source actually returned a score and the TMDB match is ≥ 0.62 confidence.
- TV checks: open a title with a TMDB key configured and confirm labeled TMDB/IMDb/RT/Metacritic/Trakt badges appear or stay hidden; with no key, the details page shows “Metadata unavailable”; Correct metadata match lists scored candidates; focusing a cast member opens filmography of titles that exist in connected catalogs only.

## Phase 2 · Home, filters, sorting (2026-11-02)

- `assembleDebug`, `lintDebug`, `testDebugUnitTest` passed. **30 tests, 0 failures, 0 errors** (previous 24 plus 3 genre-normalization and 3 newest-first sorting tests). Lint: **0 errors, 32 warnings**.
- Home now uses a cinematic hero (Play / Watchlist / More Info, genre chips, rating badge) and shelves: Continue Watching, New Releases, Trending, Top Rated, per-genre. Frosted Filters rail persists in `astra_ui_v1` only. Provider 48-item pages, live language→genre browsing, and library JSON were not changed.
- TV checks: D-pad Home hero actions; long-press a card for watchlist; open Filters, combine genre+language, Apply, confirm shelves update; Search recent/voice/grouped results; MovieRulz/Live still page 48 and keep their Language/Genre selectors. Newest-first is the default sort.

## Phase 1 · tvOS theme (2026-11-02)

- `assembleDebug`, `lintDebug`, `testDebugUnitTest` passed. **24 tests, 0 failures, 0 errors.** Lint: **0 errors, 31 warnings** (dependency/target and localization; no new errors).
- In-place Java/Views theme layer: `coreui.TvTheme`, `FocusMotion`, `GlassPanel`, `ShimmerView`, `ColorExtractor`, plus `TvUi` pills/chips and MainActivity nav/hero tint. Application ID, signing, provider pipeline and `astra` preference keys were not changed.
- TV checks: D-pad the eight top tabs; confirm focused cards scale with a bright edge; scroll until the pill nav picks up backdrop artwork; refresh sources to see shimmer instead of a spinner; enable Settings → Reduce motion and confirm springs/blur/shimmer halt. Repeat on API 26 (solid glass) and API 31+ (blur). Physical-TV 60fps remains a Phase 5 measurement.

## Version 0.3 validation (2026-10-29)

- `assembleDebug`, `lintDebug`, `testDebugUnitTest` passed: **24 tests, zero failures/errors** (10 parser, 5 NetMirror contracts, 9 research-edition tests). Lint has no errors. The original 0.2 checks below remain historical evidence, not new coverage of every source.
- New fixtures cover quoted M3U metadata, movie variants/languages, country membership, unsafe credential/CRLF headers, rejected HTTP/DRM directives, multi-season episode identity, filtering/pagination, HLS behind misleading MP4 names, origin-bound AES-GCM and research-log redaction. Fixed Prime and Hotstar platform requests are tested with injected sessions; these are contract tests, not live service access.
- Supplied movie playlist inspection found 6,850 raw entries, including 3,160 Telugu and 687 Hindi tags. Grouped native catalogs deduplicate variants; counts may differ and the upstream playlist changes. Small host-side probes of one Telugu and one Hindi source returned HTTP 200 HLS content. No complete films were downloaded for testing.
- Latest APK installed on the isolated landscape Android 16 emulator. Native home artwork, title metadata, seven add-on profiles and shared navigation rendered. MovieRulz default Telugu/Hindi catalog displayed 3,884 grouped items during this run, in bounded pages.
- **New playlist playback verified:** selecting a Telugu-tagged entry through native details → source chooser → preflight rendered video in Media3. MediaSession reported PLAYING, a progressing position and buffered duration. Evidence: ignored `build/v3-playback.png`. This verifies the tested source only, not the accuracy of its label, complete content, all variants, or future availability.
- Native Telugu TV shelf loaded with channel cards; its default catalog contained 70 indexed entries during investigation. This is directory coverage, not 70 individually verified broadcasts. The India adapter reports partial language-feed coverage and retains remaining country entries under Other Indian languages.
- The final APK's Language selector changed Telugu → All and showed 796 indexed language/channel entries with explicit 14/15 feed coverage. News genre selection was exercised separately. Language duplicates and alternate feeds mean this is not a count of unique broadcasters or verified playable streams.
- Artwork now follows bounded HTTPS redirects and retains native letter placeholders on failure. Source errors remain native; no browser fallback was reintroduced.
- **Catch-up navigation verified:** TamilTVSerials rendered native series cards, including Bigg Boss Telugu. Its detail page opened Season 10 and dated Episode 54/53/52 cards inside the same Astra shell. Back returned to native details. Evidence: ignored `build/v3-episodes.png`. This is UI/metadata verification, not verification of the broken video hosts.
- Final lint: **0 errors, 27 warnings**. Packaged debug APK signature verifies with one v2 signer. A final category-hero correction selects a title from the visible provider category instead of retaining a movie when switching to Live.
- NetMirror remains inaccessible with the published reference endpoints/session state. Tested Tamil video shortlinks returned tiny non-video redirect pages. Native catalog/episode UI does not establish their playback availability.
- Team workflow and defensive test cases are in [RESEARCH_GUIDE.md](RESEARCH_GUIDE.md). Physical Android TV remote, decoder, speaker, large-library and hostile-network acceptance remains necessary; this is not a security certification.

## Earlier 0.2 evidence

The original 0.1 prototype was a browser companion. Version 0.2 removes that approach. This record distinguishes app behavior, parser coverage, external-provider availability and runtime checks; compilation alone does not establish that a third-party video can play.

## Automated checks

- `assembleDebug`, `lintDebug`, `testDebugUnitTest`: passed. Provider suites: **14 tests, 0 failures, 0 errors** (10 parser tests and 4 NetMirror API-contract tests). Lint: **0 errors, 27 warnings**. Warnings primarily cover newer dependency/platform versions and localization. Final packaging also passed after the sample URL/artwork and saved-metadata fixes.
- Parser tests cover movie catalogs, catch-up episodes, HTTPS enforcement, plain MP4/HLS configuration, excluding unrelated radio and popup links, explicit embeds, cast/synopsis/genre, duplicate catalog IDs, manifest episode metadata, and news grouping/filtering.
- NetMirror contract fixtures cover catalog/search routes, series/episode metadata, native playlists/subtitles, withholding source-session cookies from CDN playback, and rejection of gate pages. These tests do not establish live NetMirror service access.
- Source scan confirms no WebView implementation, `ACTION_VIEW` browser intent or external-app launch call remains. Only native PlayerActivity is used for viewing media.
- Code review checks include bounded responses, request timeouts, HTTPS-only redirects, stripping session headers across hosts, provider-scoped persistence, migration of old keys, stale catalog fallback, audio focus handling, background-audio opt-in, and cancellation of stale UI callbacks.

## External-source investigation

- MovieRulz, TamilTVSerial and TeluguStream public home/detail pages returned HTTP 200 to direct HTTP inspection. Native parsers target their observed article/card structures and explicit video-source links.
- A title page URL is never passed to the player as if it were a media URL. Plain direct media or explicit bounded embed resolution is required. There is no browser fallback.
- The supplied NetMirror repository describes itself as an **unofficial Flutter client**. Its published `net50.cc/mobile` and `a.netfree2.cc/mobile` home addresses returned HTTP 200 containing redirect/gate HTML rather than catalog data. The NetMirror landing page returned HTTP 403 to a direct client request. No public live catalog or playback was verified for NetMirror. The new adapter implements the observed request/response formats without copying the Flutter source or bundling its APK. It does not fake ad verification or generate session tokens.
- The public IPTV-org Telugu directory contained 26 entries categorized as News when inspected. The app retains HTTPS entries, groups alternate streams, and does not claim every Telugu broadcaster or permanent availability.
- Host-side playlist checks for **10 TV, ABN Andhra Jyoti, HMTV, NTV Telugu and Sakshi TV** each returned HTTP 200 with `#EXTM3U`. This verifies playlist access only, not decoding every channel on every device. Signed links and regions can change.

## Runtime verification

- The new APK installed and MainActivity launched on the existing Android 37 phone emulator. Native Tamil catch-up title cards appeared; the emulator's System UI repeatedly became unresponsive. This is not accepted as complete UI or playback verification.
- An isolated Android 16 emulator was created under ignored `build/test-avds/` using the installed system image, with a landscape display and D-pad enabled. It is a phone system image, not an Android TV OS image.
- Software graphics (`-gpu swiftshader`) and a 1280×720 / 213 dpi display resolved the test harness's System UI problem. The accepted compact TV-shaped home screen is `build/astra-final-home.png`; the native detail screen is `build/astra-native-details.png`.
- Observed native catalogs: MovieRulz 25 titles, TamilTVSerial 18 and TeluguStream 18. Artwork and names rendered in native shelves. The cast and synopsis on the tested MovieRulz title rendered inside Astra. No website view was used.
- The news directory initially returned a playlist MIME type, which the generic resolver mistook for a playable stream. Explicit text loading for the channel directory fixed this. The news catalog then cached successfully, native search found ABN, and its native source chooser offered its live HLS stream.
- **Live HLS: verified.** ABN Andhra Jyoti rendered video in PlayerActivity. MediaSession reported `PLAYING`, then `PAUSED` after a media pause key, and resumed with a media play key. Clean screenshot: `build/astra-native-live.png`.
- **MP4: verified.** The old Google sample returned HTTP 403. Samples were replaced with correctly labeled Blender open-movie trailers hosted by W3C, whose media and artwork URLs returned HTTP 200. Sintel's trailer rendered in PlayerActivity with a progressing position and buffered duration. Screenshot: `build/astra-native-mp4.png`.
- **Background preference: verified.** With the default off setting, pressing Home settled the media session to `PAUSED`. With the setting enabled through Astra Settings, pressing Home left the trailer `PLAYING` with an advancing position. The emulator was launched with audio output muted, so speaker audibility was not assessed.
- A D-pad direction key focused the native Home control. Full physical-TV remote acceptance remains below.
- **Failure containment: verified.** The tested MovieRulz host provided a page containing `404` rather than media; the tested Tamil shortlink redirected to a non-video site. Astra showed a native source-unavailable dialog and stayed in the app. It does not make dead third-party streams work.
- APK signing verification passed (v2 signature, one debug signer). Screenshots, downloaded research sources and test emulator data are development artifacts excluded by Git.

## Remaining acceptance checks

- Broaden live-provider coverage beyond the tested news channel, and verify source-host adapters as their links change.
- Verify seeking, track/subtitle selection, resume across process restart, speaker output and audio-focus interruptions on a physical TV. MP4/HLS decoding, media pause/play and the background preference were verified above.
- Verify watchlist/review persistence and legacy migration across APK upgrade.
- Verify D-pad focus, return-to-shelf behavior and artwork recycling on a physical Android TV, including a compact 960×540 dp layout.
- Obtain a working NetMirror API/session or compatible manifest, then verify catalog, metadata, search, platform switching, seasons, episodes and playback. No claim is made that all features/content in the original NetMirror app are available.
- Verify each website host adapter against current provider behavior. Interactive/obfuscated/protected hosts may require additional adapters; this app does not bypass their access controls.

## Scope limits

No guaranteed complete ad removal, full Nuvio feature parity, offline downloads, DRM provisioning, cross-provider stream matching, fetched audience reviews or all-channel guarantee. Personal reviews are stored on-device; cast/ratings are supplied by providers. Broadcaster ads embedded in video remain. The app cannot uninstall other TV apps or change the TV operating system.
