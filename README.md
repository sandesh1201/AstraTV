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

- **MovieRulz:** consumes the public [movies_m3u playlist](https://github.com/thirud4a4/movies_m3u), prioritizing Telugu and Hindi. Language filters and 48-title pages keep the catalog manageable; alternate streams are grouped per title. Listed third-party proxy URLs are consumed directly; their worker implementation is not bundled or deployed. Cast/synopsis are shown only when actually supplied, not invented from playlist names.
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

**YouTube trailer exception:** the only WebView in the app loads the official YouTube IFrame player on `https://www.youtube-nocookie.com/embed/{id}`. Top-level navigation and new windows are blocked. JavaScript is enabled only so the player can run. Cookies are not accepted or persisted. Subresources are limited to youtube-nocookie, ytimg, googlevideo and gstatic HTTPS hosts required by that official embed. YouTube stream URLs are never scraped or extracted. Direct HTTPS trailers from a provider play in Media3 instead. Reduce motion and the Autoplay trailers setting disable this path. This does not remove advertising already embedded in a video stream or make the client a hardened security sandbox.

Viewers never need an API key. TMDB requests go through the **Saandio Catalog API** (`backend/`), which keeps the TMDB credential in server environment variables; the build only learns its HTTPS address from `catalog.api.base` (see below). Optional developer keys are read from `local.properties` (`tmdb.api.key` — debug builds only —, `omdb.api.key`, `trakt.client.id`, `youtube.api.key`) into BuildConfig, or entered in Settings → Developer · metadata keys. They are stored in app-private `astra_ui_v1` preferences and are never committed or written to the research log.

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
  - Licensed playback partners (only the clearly labelled Einthusan simulation today, off by default)
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
