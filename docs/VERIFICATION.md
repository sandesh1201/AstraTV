# Verification · native edition 0.2

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
