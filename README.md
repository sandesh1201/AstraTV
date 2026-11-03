# Saandio TV · Native edition

*Formerly Astra TV. The display name and icon changed; the application ID (`com.astratv.app`), signing key and stored data are unchanged, so existing installs upgrade in place.*

Android TV media client with native catalogs, artwork shelves, detail pages, source selection, watchlist, personal reviews and Media3 playback. Version **0.3.0** is a team research edition with seven provider profiles, Telugu/Hindi movie browsing, Indian TV language/genre filters, native catch-up series and a redacted research log. There is no browser intent, external-app launcher or bundled NetMirror APK. A single locked-down WebView exists only for official YouTube trailer embeds (see Privacy).

## Install

Use `dist/SaandioTV-debug.apk` for local sideloading, or build with Android Studio / `gradlew.bat assembleDebug`. Android 8.0 (API 26) or newer is required. Builds use JDK 17+, SDK 35, AGP 8.11.1 and Gradle 8.14.

```powershell
adb install -r dist/SaandioTV-debug.apk
```

Launch **Saandio TV** from your TV's apps. Navigate with arrows, Select and Back. The existing application ID and signing key are retained for local upgrades. Legacy sample/import watchlist, reviews and positions are migrated to provider-scoped keys.

## How the native add-ons work

Providers supply **catalog data → title metadata → direct media sources**. The app owns the browsing experience and player. An unavailable source produces an error inside Astra; it never launches a website as a fallback.

- **MovieRulz:** retired (Turn 7). MovieRulz has no authorized catalog or playback API. Its sites and the [movies_m3u playlist](https://github.com/thirud4a4/movies_m3u) built from them list unlicensed copies of films, served through third-party proxies. Saandio no longer reads or plays them. The Movies tab stays empty and explains why.
- **Tamil catch-up / TamilTVSerial:** parses episode cards and follows explicitly designated video sources without opening tabs. A shortlink that needs an interactive browser or an unsupported host cannot be played by this adapter.
- **Telugu catch-up / TeluguStream:** uses the same native catalog/detail/resolution pipeline with its own provider identity and stored settings.
- **TV channels:** intersects the [IPTV-org India directory](https://iptv-org.github.io/iptv/countries/in.m3u) with 15 language feeds, retains unclassified country entries, groups alternate HTTPS streams, and provides language then genre browsing. Default language is Telugu; choose All + News for news across languages. Partial feed coverage is explicit. This community directory does not guarantee every channel, free entitlement, regional availability or uptime. Video-embedded broadcaster ads remain.
- **Netflix (NetMirror), Prime (NetMirror), Hotstar (NetMirror):** three separate profiles use the formats observed in the user-provided [unofficial Flutter repository](https://github.com/suryasarisa99/netmirror). Configure a shared API base and valid source-issued session under Add-ons → Configure → NetMirror API settings; the selected profile fixes its platform. Sessions are encrypted using Android Keystore AES-GCM and bound to the API origin. No source login/session verification is forged or bypassed. Published reference servers returned gates or connection failures; live NetMirror catalog and playback remain unverified.

Any provider can alternatively be connected to an HTTPS **Stremio-compatible manifest** under Add-ons → Configure. Supported resources are catalog, metadata, episodes exposed in metadata and direct HTTPS streams. Catalog search and skip pagination are requested when declared. This is a protocol subset; torrents, browser-only `externalUrl` streams, DRM provisioning, addon scripts, provider event callbacks and cross-addon stream matching are not implemented.

## Playback and library

Media3 ExoPlayer handles MP4, HLS and DASH, adaptive playback, device-supported codecs, embedded audio tracks, and supplied subtitle tracks. Its service-owned media session supplies notification and remote controls. Player controls hide automatically. Enable **Background audio** in Settings to keep playback running when leaving the player; it is off by default.

Watchlist, personal written reviews and continue watching work with native titles. Cast, synopsis and ratings appear only when supplied by a source; no audience reviews or ratings are fabricated. Catch-up posts are grouped into native series, seasons and dated episodes with newer/older pages. Back navigation restores the prior native page. Compatible manifests and NetMirror metadata can also provide episodes. Tested Tamil shortlinks returned non-video redirects, so their playback remains unavailable. Third-party source support is not equivalent to every feature in the original app.

Import your own direct HTTPS catalog under Settings. `catalog.example.json` shows the format; replace its placeholder URL. Imports replace the previous imported catalog and require unique IDs, at most 500 items and under 1 MB. Poster and background HTTPS URLs are optional. Two Blender Foundation open-movie trailers appear in a separate **Open cinema** shelf for native-player testing; they are not a commercial movie catalog.

## Build and verification

The cinematic UI upgrade is implemented in place using the existing **Java/Android Views** stack (not a Compose rewrite). See [TVOS_UPGRADE.md](docs/TVOS_UPGRADE.md) for the phased plan and TV acceptance steps. Phases 1–5 cover the tvOS theme, cinematic Home/filters/newest-first sorting, TMDB metadata overlay, focus-to-trailer autoplay, and a performance pass (artwork disk cache, prefetch, per-row focus memory, R8 on release). Provider contracts and existing library JSON remain unchanged. Put optional metadata keys in `local.properties`; they are never committed.

```powershell
gradlew.bat assembleDebug lintDebug testDebugUnitTest
```

Read [docs/VERIFICATION.md](docs/VERIFICATION.md) for actual checks and [docs/RESEARCH_GUIDE.md](docs/RESEARCH_GUIDE.md) for team review exercises, trust boundaries and known limitations. Settings → Research lab provides source status and an explicitly exported, bounded log containing provider, stage, outcome and time. It excludes URLs, headers, session values and response bodies. Build outputs, local research and emulator files are ignored by Git. Watchlist, search and provider grids no longer cap at 96 visible results; provider requests stay at 48-title pages. Default catalog order is newest first.

## Privacy and reference material

App-private preferences contain watchlist, reviews, history, imported metadata and encrypted optional provider sessions. Catalogs are cached in app-private storage; backup/transfer exclusions are configured. Source servers, third-party proxies, artwork hosts and optional metadata APIs (TMDB / OMDb / Trakt / YouTube Data) receive normal network requests when keys are configured. There are no analytics or advertising SDKs.

**YouTube trailer exception:** the only WebView in the app runs YouTube's official IFrame Player API (`youtube.com/iframe_api`, `youtube-nocookie.com` host), loaded with the app's identity (`https://com.astratv.app/`) as origin and referrer, as YouTube's embed rules require.
- Top-level navigation and new windows are blocked. JavaScript is enabled only so the player can run, and cookies are not accepted or persisted.
- Requests may go only to the HTTPS Google hosts the official player needs, including its ad and measurement hosts. YouTube's API terms forbid blocking those.
- YouTube stream URLs are never scraped or extracted. Direct HTTPS trailers from a provider play in Media3 instead.
- Reduce motion and the Autoplay trailers setting disable this path. This is not a hardened security sandbox.

Viewers never need an API key. TMDB requests go through the **Saandio Catalog API** (`backend/`), which keeps the TMDB credential in server environment variables; the build only learns its HTTPS address from `catalog.api.base` (see below). Optional developer keys are read from `local.properties` (`tmdb.api.key` — debug builds only —, `omdb.api.key`, `trakt.client.id`, `youtube.api.key`) into BuildConfig, or entered in Settings → Developer · metadata keys. They are stored in app-private `astra_ui_v1` preferences and are never committed or written to the research log.

## Six tabs, Catch-Up TV episodes and Einthusan removal (Turn 10)

- **Navigation:** exactly six tabs: Home, Movies, TV Shows, Catch-Up TV, Live TV and Sports Live (`Navigation.TABS`).
  - "All Movies" is now **Movies**. Its saved filters keep the old preference key, so nothing resets.
  - **HD Movies is removed**, together with every Einthusan class, the Creative Commons simulation fixture, the simulation add-on and the "Open einthusan.tv" button. Watch history, favorites and preferences are untouched.
- **Movies (MovieRulz only):** unchanged and still empty, with the reason shown. MovieRulz has no authorized catalog or playback API, so Saandio does not read its pages. The tab never shows TMDB or any other catalog, and it shows no TMDB key prompt. Its Language, Year, Genre, Quality and Sort filters work on whatever an authorized MovieRulz provider would list.
- **Catch-Up TV:** language → show → episodes → episode → official app.
  - **Where the data comes from:** each site's public WordPress RSS feed (`/feed/`, `?paged=N`; robots.txt only excludes `/wp-admin/`), read by `WordPressSerialFeed` and `SerialFeedParser`.
    - Only the show, episode number, air date (from the post title, in India time) and broadcaster are kept.
    - Post links, bodies, thumbnails and embedded video links are dropped and never stored.
    - Requests are conditional (ETag / Last-Modified) and 400 ms apart, and paging stops at the first page with nothing new. A routine hourly refresh is one or two requests; the first sync reads up to 12 pages per site.
    - Episodes stay listed for 14 days. Repeated posts of the same show and episode are merged.
  - **Broadcaster → app** (`CatchupBroadcasters`):

    | Broadcasters | Official app |
    |---|---|
    | Sun TV, KTV, Gemini, Udaya, Surya | Sun NXT |
    | Star Vijay, Star Maa, Colors Tamil/Kannada, Asianet, Star Suvarna | JioHotstar |
    | Zee Tamil/Telugu/Kannada/Keralam | ZEE5 |
    | ETV | ETV Win |
    | Sony Vizha | Sony LIV |

    The language follows the broadcaster, so a Telugu serial on TamilTVSerial is filed as Telugu. A broadcaster with no app (Kalaignar, Polimer, Raj, Jaya) or an unrecognised one gets "No official viewing option" instead of a Play button.
  - **Screens:**
    - The tab has a Language filter, an "All shows" grid, and rows for TV shows and latest episodes per site, Recently aired, and Continue Watching.
    - A **show page** shows the name, language, broadcaster, latest episode and air date, a "Watch in <app>" button, and every saved episode, newest first.
    - An **episode page** has Watch in <app> and "All episodes of <show>".
    - Back returns to the same focused card.
  - **What Watch does:** it opens the broadcaster's app on its home screen. None of these apps publishes episode deep links, so the viewer searches for the show there. Saandio has not checked that each episode is in the app.
  - **What Saandio does not do:** it never opens the two sites' pages and never plays their videos, which re-host broadcaster episodes without a licence. The legacy scraping add-ons for both sites stay blocked.
  - Sun Network channels stay only in Live TV.
- **Web pages:** Saandio has no in-app browser. Its only WebView is the YouTube trailer player, which disables multiple windows, refuses `onCreateWindow`, blocks top-level navigation and non-YouTube hosts, and turns cookies off. A source-site browser for these catch-up sites was not built: it would turn Saandio into a viewer for unlicensed re-hosted episodes.
- **Developer check:** `tools/catchup-live-check/run.ps1` runs the app's own parser against the live feeds and prints metadata only.

## Content sources and Einthusan HD Movies (Turn 9)

*Superseded by Turn 10: HD Movies and Einthusan were removed, and Catch-Up TV now lists episodes from the sites' RSS feeds.*

Each tab reads only its own sources:

| Tab | Source |
|---|---|
| Home | Aggregate of the other tabs |
| All Movies | MovieRulz only |
| HD Movies | Einthusan only |
| TV Shows | Unchanged (TMDB / TVMaze) |
| Catch-Up TV | TamilTVSerial and TeluguStream only |
| Live TV, Sports Live | Unchanged |

- **HD Movies is empty in release builds, and says why.** Einthusan has no catalog or playback API (`/api/` is a 404 page), no partner programme and no Android TV app. Its Terms of Use forbid access "through any automated means" and circumventing its video player's access controls. The Kodi add-ons and CLI tools that read Einthusan do exactly that, so none of their code is used (see [docs/REFERENCE_PROJECTS.md](docs/REFERENCE_PROJECTS.md)). An "Open einthusan.tv" button hands off to a web browser.
- **HD Movies filters:** Language (Telugu, Tamil, Hindi, Malayalam, Kannada, Bengali, Marathi, Punjabi, plus any other language the provider lists), Year and Genre, both built from the provider's own titles, plus Verified quality. Sorting is newest release or provider-added date first. 1080p and 4K appear only from verified stream renditions.
- **Availability states:** AVAILABLE, CHECKING, UNAVAILABLE, LOGIN_REQUIRED, UNSUPPORTED and PROVIDER_ERROR. Watch Movie appears only for AVAILABLE. Rows saved with the old AUTHORIZATION_REQUIRED read as UNSUPPORTED.
- **Catch-Up TV:**
  - Rows: Latest Telugu Episodes, Latest Tamil Episodes, Telugu Serials, Tamil Serials, Recently Aired and Continue Watching.
  - `TamilTvSerialProvider` and `TeluguStreamProvider` fill them only from an authorized feed. None exists: both sites re-post same-day episodes that the broadcasters license to their own apps. The rows therefore stay empty with that reason.
  - The legacy add-ons that scraped the two sites are now blocked, like MovieRulz.
  - The Doordarshan archive rows and Sun NXT tiles from Turn 8 moved behind `feature.official_catchup_archives`, which is off by default. Sun Network stays in Live TV.
- **Live TV:** the owner decided to remove the two third-party Sun restreams, "Sun TV HD" (`SunTV.in~Tamil`) and "Sun Surya HD" (`SunSurya.in~Malayalam`). `SunNetworkChannels.rightsAsserted` removes any directory channel whose iptv-org id belongs to a Sun channel. Sun TV, Sun TV HD, Surya TV and Surya TV HD now appear only as Sun NXT entries. All other directory channels are unchanged.
- **Diagnostics:** a "Content providers" panel separates these cases: catalog request failed, title discovered but playback unavailable, expired session, provider authentication failure, unsupported Android TV integration, Media3 initialization failure, network error and decoder error.

## Live TV additions, Sun Network and Catch-Up TV (Turn 8)

Details, the 37-channel Sun Network audit and the source review are in [docs/LIVE_TV_AND_CATCHUP.md](docs/LIVE_TV_AND_CATCHUP.md).

- **Existing Live TV unchanged.** The iptv-org directory keeps the same 800 channels, ids, stream URLs, favorites, health checks and labels. `LiveTvRegressionTest` locks this against a real feed fixture, and [docs/live-tv-baseline.tsv](docs/live-tv-baseline.tsv) records the baseline.
- **Sun Network:**
  - All 37 official channels are listed with their authorized route: **Sun NXT** (subscription, app handoff). Sun News also offers its official YouTube channel.
  - No Sun stream is added. Sun has asserted rights over its channels with iptv-org.
  - Channels already in the directory (Sun TV HD, Sun Surya HD) were not duplicated; they showed "Also on Sun NXT". Turn 9 removed those two restreams.
- **Channel state:** LIVE means the channel actually played in Saandio within the last 6 hours, not just that its URL answered. Official entries show "Subscription · Sun NXT" or "Free · YouTube".
- **Catch-Up TV tab**, between TV Shows and Live TV (since Turn 9, these rows show only with `feature.official_catchup_archives=true`):
  - Episodes and programmes from the official Doordarshan / Prasar Bharati YouTube channels, newest first, in serial, classics and free-programme rows. They open in the YouTube app.
  - Sun NXT catch-up tiles are marked as subscription.
  - Needs the backend's `/api/channel-uploads` with `YOUTUBE_API_KEY`.
- **Programme guide:** XMLTV support exists but ships **off**, because no permitted guide source was found. To turn it on, set `epg.xmltv.url` and `feature.extended_epg=true`.
- **Rollback:** the `feature.new_live_sources`, `feature.sun_network`, `feature.catchup_tv` and `feature.extended_epg` entries in `local.properties`, or the toggles in Settings → Diagnostics → Feature flags.

## Movie tabs, movie playback and background trailers (Turn 7)

- **All Movies = MovieRulz only. HD Movies = Einthusan only.** Each tab reads one provider's own catalog (`MovieRulzCatalogProvider`, `EinthusanCatalogProvider`). TMDB, Wikidata, IMDb and OMDb never add titles there; metadata can only enrich a title the provider listed. A failing provider keeps its last confirmed titles. Nothing is filled in from another catalog.
- **Availability:** every title carries `AVAILABLE`, `UNAVAILABLE`, `CHECKING`, `AUTHORIZATION_REQUIRED` or `PROVIDER_ERROR`, taken from the provider's playback API and the same checks the player applies.
  - Tabs show only AVAILABLE titles by default. "Unavailable titles · Shown" reveals the rest with a status badge. "Check provider now" re-syncs.
  - Watch Movie is enabled only for AVAILABLE. Otherwise the button says why ("Authorization required", "Not available to watch", "Provider error · Check again").
- **Current status:**
  - **MovieRulz:** not available. It has no authorized API, and Saandio will not use its pirated listings.
  - **Einthusan:** authorization required. It has no public API and Saandio has no agreement with it.
  - Debug builds can turn on the labelled Einthusan *simulation* (Creative Commons films behind the partner contract) to test the whole path.
- **Playback trace:** `FullMoviePlaybackController` logs each stage under logcat tag `SaandioPlayback` and in Settings → Diagnostics → Playback stages. The stages are selected, record, resolver, descriptor, player, ready and error. Lines are redacted: no links, keys or tokens.
- **Background trailers:** `TrailerPreviewController`, kept separate from movie playback.
  - On focus the backdrop shows at once. After 700 ms a muted trailer plays in a 16:9 window at the top right, clear of the title text.
  - Only one preview plays at a time. It stops on focus change, scrolling, dialogs or leaving the screen.
  - Lookup order: provider trailer file, cached ID, TMDB official trailer, then a YouTube Data API search through the catalog service's `/api/trailer`, picked by `TrailerMatcher`.
  - `TrailerMatcher` rejects reactions, reviews, fan edits, full movies, live, private, unembeddable or region-blocked videos and unofficial channels.
  - Results are cached for 30 days (misses for 3). A player error, an 8-second start timeout or no match falls back to the backdrop. No black box, no spinner.

## Playback reliability, posters and diagnostics (Turn 6)

- **Playback candidates:** `PlaybackCandidate` carries everything known about a stream: provider, link, stream type (HLS, DASH or progressive), audio, subtitles, declared quality, availability, signed-link expiry, Widevine DRM settings, region list and approved headers.
  - It is checked before the player starts. Non-media links (trailers, posters, web pages, metadata APIs), insecure links, expired links, unavailable titles, region locks and unsupported DRM are refused with a clear reason.
- **Error handling:** `PlaybackErrors` sorts each Media3 failure into one category: network, authorization, expired link, not found, unsupported codec, invalid manifest, DRM, provider outage, decoder, or behind the live window.
  - Only temporary failures retry, with bounded exponential backoff (1–16 s, at most 4 tries; decoder faults retry once).
  - While offline, playback waits for the network and resumes from the saved position.
  - An expired signed link is replaced by asking the provider again, at most twice.
  - Repeated buffering stalls show a "connection is slower than this stream" hint.
  - A paused player with no player screen is released after 10 minutes.
- **Posters:** the `Poster` component has four states. Loading shows a skeleton, loaded shows the image, and missing or failed shows a branded Saandio card with the title and year.
  - `Artwork` validates image URLs and retries temporary failures twice.
  - URLs that keep failing are remembered and skipped: for a day after an HTTP 4xx, or 10 minutes after a temporary failure. The list holds at most 512 URLs.
- **Catalog:** TMDB rows keep their sort modes distinct: Trending This Week (`/trending`), Popular Movies (`/movie/popular`), New & Popular in India, the Latest rows per language by release date, and Recently Added (first seen by Saandio).
  - Regional rows drop titles whose original language doesn't match.
  - A JobScheduler job refreshes due sources every 6 hours. Launch still syncs on its own, so first-run content never waits for the job.
- **Live TV:** a channel that failed 3 or more checks and hasn't worked for 3 days is hidden from the main listing. It is re-checked daily, its record is kept, and a toggle shows it again.
- **Diagnostics:** Settings → Diagnostics (debug builds, or builds with a tester PIN) shows:
  - provider status and the saved-catalog size
  - image and network failure counts
  - recent metadata and playback errors
  - the last successful playback start
  - memory warnings

  Entries keep reasons, codes and host names only.
- **Reference projects:** see [docs/REFERENCE_PROJECTS.md](docs/REFERENCE_PROJECTS.md).

## Catalog without user keys (Turn 5)

- **Root cause of the empty catalog:** movie and TV rows were built only when a TMDB key existed on the device. No build shipped one (`local.properties` had none), so `TmdbCatalogSource.ready()` was false, no rows synced, and Home/TV Shows asked the viewer to add a key.
- **Now:** `App → Saandio Catalog API → TMDB`. The app picks a route (`TmdbRoute`): the catalog service when `catalog.api.base` is set at build time, a developer key in debug builds, otherwise none. Release builds never contain a TMDB key.
- **Keyless open data** keeps a fresh install useful when no catalog service is configured, or before TMDB rows have ever loaded:
  - Recent Telugu, Tamil, Hindi, Malayalam and Kannada films from the Wikidata Query Service (CC0), refreshed every 6 hours.
  - Recently aired episodes from the TVMaze schedule (CC BY-SA 4.0), refreshed hourly.
  - Both are metadata only and never playable. Release dates are the sources' publication dates, not the date Saandio saw them. Films without a free poster show their title on the card.
- **Fallback:** an empty TMDB row with a same-meaning open row (for example Latest Telugu Movies) shows the open row until TMDB data arrives. Trending and Hollywood have no stand-in, because the title would no longer be true.
- **Developer setup:** deploy `backend/` (see [backend/README.md](backend/README.md)), then add `catalog.api.base=https://<project>.vercel.app` to `local.properties` (or set `SAANDIO_CATALOG_API_BASE`) and rebuild. Settings shows which catalog route the build uses.

Home shelves use language and genre titles. Provider names stay on Add-ons and in a small "Source" line on each title. Einthusan is not connected: there is no official third-party API, and the app does not scrape it. A clearly labelled **Einthusan (simulation)** add-on (off by default) exercises the integration contract with Creative Commons films; see [docs/EINTHUSAN.md](docs/EINTHUSAN.md). See [docs/SECURITY.md](docs/SECURITY.md).

## Catalog sync, navigation and performance (Turn 4)

- **Navigation:** six tabs (Home, All Movies, HD Movies, TV Shows, Live TV, Sports Live) plus Search, Watchlist and Settings icons. Each tab remembers its focused card, scroll position and filters. Back from a detail page returns to the same card.
- **Sources:** these are synced into a local SQLite catalog (`catalog_v2.db`, WAL mode):
  - TMDB movies and series, through the Saandio Catalog API (Turn 5), with Wikidata films and TVMaze episodes as keyless open data
  - Licensed playback partners (only the clearly labelled Einthusan simulation today, debug builds, off by default)
  - The iptv-org public channel directory
  - TheSportsDB match schedules (the shared free key works; a personal key adds yesterday's results)
- **Sync:** the saved catalog shows immediately. Each source then refreshes in the background on launch, when the app returns to the foreground, and every 5 minutes while open. Requests use ETag / Last-Modified, so unchanged sources cost a 304.
  - Rows are replaced in one transaction, only when a fetch succeeds.
  - A failing source keeps its last catalog and retries with increasing delays.
  - Duplicates across sources and dubbed editions are merged.
  - Every row shows when it was last updated. Settings → Catalog sync & freshness lists each source's status, has Sync now buttons, and lets you change its refresh interval.
- **Honesty rules:** Play appears only for user-owned titles, the open-movie samples, or titles an authorized partner streams with verified renditions. TMDB titles otherwise show "Where to watch" (JustWatch data via TMDB, region IN).
  - HD means a partner's stream reported 720p or more. A title or web page saying "HDRip" never counts.
  - Channels show Online, Offline or Unchecked from a real playback probe.
  - Matches are never playable. LIVE appears only while TheSportsDB reports a match in play, from a sync less than 30 minutes old.
- **Performance:**
  - No database or network work runs on the main thread.
  - Home rows load in on-screen order, and card lists update through DiffUtil without losing focus.
  - Image requests are cancelled when a card is recycled. Posters decode at display size, with RGB_565 for opaque JPEGs, and the memory cache is trimmed when Android asks.
  - The legacy add-ons no longer refresh at startup.
  - Measured results are in [docs/VERIFICATION.md](docs/VERIFICATION.md).

## Browsing, player and VPN (Turn 3)

- **Navigation (superseded by Turn 4):** Search, Home, Movies, Trending, Latest Releases, Languages, Genres, Watchlist, Shows, Live TV, Settings. Add-ons stay reachable from Settings → Add-ons & sources.
- **Home order (superseded by Turn 4):** Trending Now, Latest Releases, Recently Added, Popular This Week, Telugu/Tamil/Hindi/Malayalam/Kannada/… Movies, Hollywood Movies, Top Rated, Browse by Genre, then the existing shelves. A shelf appears only when the data supports it. Trending and Popular This Week need a TMDB key (daily and weekly charts matched to titles you actually have). Recently Added uses a local first-seen index (`catalog_index_v1.db`). Popularity is never invented.
- **Filters and sorts:** 10 language buckets, 14 genres (multi-select). Sorts: Trending, Newest Release, Recently Added, Most Popular, Highest Rated, Language, Genre, A–Z. The same film from different sources is merged only when name and known year match; other copies stay under "Other sources".
- **Player:** Quality menu built from the stream's real renditions (Auto or fixed), speed 0.75–2×, and automatic retry with backoff for network errors. Labels follow the decoded rendition. Unknown quality is never called HD.
- **VPN:** optional WireGuard tunnel from a config you import (Settings → VPN). It shows live status, traffic, tunnel latency, exit-address check, IPv6 leak protection and auto-reconnect. "Protected" is shown only when Android reports the VPN, a recent handshake exists, and the tunnel is full. No servers are bundled. Details and limits: [docs/VPN.md](docs/VPN.md).
- **Security Research Dashboard:** for authorized testers only and hidden unless the build has a tester PIN. Add `security.tester.pin=<digits>` to `local.properties` (optionally `security.tester.salt`) and rebuild. Only a PBKDF2 hash is compiled in. See [docs/SECURITY.md](docs/SECURITY.md).

**Permissions:** unchanged: `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. The WireGuard library adds a non-exported `VpnService` guarded by the system `BIND_VPN_SERVICE` permission. Android asks for VPN consent the first time you connect. **APK size:** the WireGuard native library raises the debug APK from 6.5 MB to about 21 MB (four ABIs; the Saandio icon and banner add about 0.7 MB). Use an AAB or ABI splits for distribution.

Audit, repository evaluation and validation: [docs/AUDIT.md](docs/AUDIT.md), [docs/REPO_EVALUATION.md](docs/REPO_EVALUATION.md), [docs/VERIFICATION.md](docs/VERIFICATION.md). CI: `.github/workflows/android.yml`.

The app is independently written. Nuvio informed the native catalog/detail/player architecture; no Nuvio or Flutter source was copied into Saandio TV. No source APKs are redistributed.

- [Nuvio TV](https://github.com/NuvioMedia/NuvioTV) — TV reference, Kotlin/Compose/Media3.
- [Nuvio Mobile 0.6.5-beta](https://github.com/NuvioMedia/NuvioMobile/releases/tag/0.6.5-beta) — additional user-provided reference.
- [NetMirror Flutter reference](https://github.com/suryasarisa99/netmirror) — unofficial client; inspected for API formats, not bundled.
- [Stremio protocol](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md).
- [IPTV-org](https://github.com/iptv-org/iptv) — public news directory, Unlicense.
- [Blender open movies](https://www.blender.org/about/projects/) — sample film provenance; [Sintel test distribution](https://media.w3.org/2010/05/sintel/trailer.mp4).
