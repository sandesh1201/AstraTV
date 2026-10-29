# Astra TV · Native edition

Android TV media client with native catalogs, artwork shelves, detail pages, source selection, watchlist, personal reviews and Media3 playback. Version **0.2.0** replaces the earlier browser-style prototype. No WebView, browser intent, external-app launcher or bundled NetMirror APK remains.

## Install

Use `dist/AstraTV-debug.apk` for local sideloading, or build with Android Studio / `gradlew.bat assembleDebug`. Android 8.0 (API 26) or newer is required. Builds use JDK 17+, SDK 35, AGP 8.11.1 and Gradle 8.14.

```powershell
adb install -r dist/AstraTV-debug.apk
```

Launch **Astra TV** from your TV's apps. Navigate with arrows, Select and Back. The existing application ID and signing key are retained for local upgrades. Legacy sample/import watchlist, reviews and positions are migrated to provider-scoped keys.

## How the native add-ons work

Providers supply **catalog data → title metadata → direct media sources**. The app owns the browsing experience and player. An unavailable source produces an error inside Astra; it never launches a website as a fallback.

- **Latest movies / MovieRulz:** parses public catalog cards, artwork, synopsis, cast and genres. Playback resolves plain public video configuration or explicit embedded player links, with bounded HTTPS requests. Interactive, protected or obfuscated hosts may be unsupported.
- **Tamil catch-up / TamilTVSerial:** parses episode cards and follows explicitly designated video sources without opening tabs. A shortlink that needs an interactive browser or an unsupported host cannot be played by this adapter.
- **Telugu catch-up / TeluguStream:** uses the same native catalog/detail/resolution pipeline with its own provider identity and stored settings.
- **Telugu news:** loads the public [IPTV-org Telugu playlist](https://iptv-org.github.io/iptv/languages/tel.m3u), retains entries categorized as News with HTTPS stream URLs, groups alternative streams, and plays them directly. This is a community directory, not a guarantee of every channel, rights, regional availability or uptime. Video-embedded broadcaster ads are retained.
- **NetMirror:** includes an independent client for the HTTP catalog, search, metadata, playlist and episode formats observed in the user-provided [unofficial Flutter repository](https://github.com/suryasarisa99/netmirror). Configure its API base, platform and a valid source-issued session under Add-ons → NetMirror → Configure → NetMirror API settings. No source login/session verification is forged or bypassed. The reference server addresses returned redirect/gate HTML during this investigation; live NetMirror catalog and playback are not verified. The existence of source code does not make those servers accessible.

Any provider can alternatively be connected to an HTTPS **Stremio-compatible manifest** under Add-ons → Configure. Supported resources are catalog, metadata, episodes exposed in metadata and direct HTTPS streams. Catalog search and skip pagination are requested when declared. This is a protocol subset; torrents, browser-only `externalUrl` streams, DRM provisioning, addon scripts, provider event callbacks and cross-addon stream matching are not implemented.

## Playback and library

Media3 ExoPlayer handles MP4, HLS and DASH, adaptive playback, device-supported codecs, embedded audio tracks, and supplied subtitle tracks. Its service-owned media session supplies notification and remote controls. Player controls hide automatically. Enable **Background audio** in Settings to keep playback running when leaving the player; it is off by default.

Watchlist, personal written reviews and continue watching work with native titles. Cast, synopsis and ratings appear only when supplied by a source; no audience reviews or ratings are fabricated. Series/season/episode browsing is available for compatible manifest metadata and the NetMirror protocol. Third-party source support is not equivalent to every feature in the original app.

Import your own direct HTTPS catalog under Settings. `catalog.example.json` shows the format; replace its placeholder URL. Imports replace the previous imported catalog and require unique IDs, at most 500 items and under 1 MB. Poster and background HTTPS URLs are optional. Two Blender Foundation open-movie trailers appear in a separate **Open cinema** shelf for native-player testing; they are not a commercial movie catalog.

## Build and verification

```powershell
gradlew.bat assembleDebug lintDebug testDebugUnitTest
```

Read [docs/VERIFICATION.md](docs/VERIFICATION.md) for checks actually performed, source limitations and remaining device checks. Build outputs, local research and emulator files are ignored by Git.

## Privacy and reference material

App-private preferences contain watchlist, reviews, history, imported metadata and optional provider sessions. Catalogs are cached in app-private storage. Source servers and artwork hosts receive normal network requests. There are no analytics or advertising SDKs. No webpages or third-party scripts execute inside the app, so their popup ads and browser redirects do not run. This does not remove advertising already embedded in a video stream.

The app is independently written. Nuvio informed the native catalog/detail/player architecture; no Nuvio or Flutter source was copied into Astra. No source APKs are redistributed.

- [Nuvio TV](https://github.com/NuvioMedia/NuvioTV) — TV reference, Kotlin/Compose/Media3.
- [Nuvio Mobile 0.6.5-beta](https://github.com/NuvioMedia/NuvioMobile/releases/tag/0.6.5-beta) — additional user-provided reference.
- [NetMirror Flutter reference](https://github.com/suryasarisa99/netmirror) — unofficial client; inspected for API formats, not bundled.
- [Stremio protocol](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md).
- [IPTV-org](https://github.com/iptv-org/iptv) — public news directory, Unlicense.
- [Blender open movies](https://www.blender.org/about/projects/) — sample film provenance; [Sintel test distribution](https://media.w3.org/2010/05/sintel/trailer.mp4).
