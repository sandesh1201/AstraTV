# Astra TV · Native edition

Android TV media client with native catalogs, artwork shelves, detail pages, source selection, watchlist, personal reviews and Media3 playback. Version **0.3.0** is a team research edition with seven provider profiles, Telugu/Hindi movie browsing, Indian TV language/genre filters, native catch-up series and a redacted research log. No WebView, browser intent, external-app launcher or bundled NetMirror APK remains.

## Install

Use `dist/AstraTV-debug.apk` for local sideloading, or build with Android Studio / `gradlew.bat assembleDebug`. Android 8.0 (API 26) or newer is required. Builds use JDK 17+, SDK 35, AGP 8.11.1 and Gradle 8.14.

```powershell
adb install -r dist/AstraTV-debug.apk
```

Launch **Astra TV** from your TV's apps. Navigate with arrows, Select and Back. The existing application ID and signing key are retained for local upgrades. Legacy sample/import watchlist, reviews and positions are migrated to provider-scoped keys.

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

```powershell
gradlew.bat assembleDebug lintDebug testDebugUnitTest
```

Read [docs/VERIFICATION.md](docs/VERIFICATION.md) for actual checks and [docs/RESEARCH_GUIDE.md](docs/RESEARCH_GUIDE.md) for team review exercises, trust boundaries and known limitations. Settings → Research lab provides source status and an explicitly exported, bounded log containing provider, stage, outcome and time. It excludes URLs, headers, session values and response bodies. Build outputs, local research and emulator files are ignored by Git. Watchlist/search presentation is currently capped at 96 visible results.

## Privacy and reference material

App-private preferences contain watchlist, reviews, history, imported metadata and encrypted optional provider sessions. Catalogs are cached in app-private storage; backup/transfer exclusions are configured. Source servers, third-party proxies and artwork hosts receive normal network requests. There are no analytics or advertising SDKs. No webpages or third-party scripts execute inside the app, so their popup ads and browser redirects do not run. This does not remove advertising already embedded in a video stream or make the client a hardened security sandbox.

The app is independently written. Nuvio informed the native catalog/detail/player architecture; no Nuvio or Flutter source was copied into Astra. No source APKs are redistributed.

- [Nuvio TV](https://github.com/NuvioMedia/NuvioTV) — TV reference, Kotlin/Compose/Media3.
- [Nuvio Mobile 0.6.5-beta](https://github.com/NuvioMedia/NuvioMobile/releases/tag/0.6.5-beta) — additional user-provided reference.
- [NetMirror Flutter reference](https://github.com/suryasarisa99/netmirror) — unofficial client; inspected for API formats, not bundled.
- [Stremio protocol](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md).
- [IPTV-org](https://github.com/iptv-org/iptv) — public news directory, Unlicense.
- [Blender open movies](https://www.blender.org/about/projects/) — sample film provenance; [Sintel test distribution](https://media.w3.org/2010/05/sintel/trailer.mp4).
