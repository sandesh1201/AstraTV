# Astra TV

A native Android TV research prototype with a dark, spacious interface, animated D-pad focus, an add-on shelf and service-based media playback. Written independently; no Nuvio source or APK is included.

## Install and run

Open this folder in Android Studio, select JDK 17 or newer, and build the `app` module, or run `gradlew.bat assembleDebug`. SDK 35 and Android Gradle Plugin 8.11.1 are used. Minimum supported Android version is 8.0 (API 26).

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. Install it using `adb install -r app/build/outputs/apk/debug/app-debug.apk`, then launch **Astra TV** from your TV's apps. Debug signing is for local research only.

Use the remote's arrows and center button to navigate. Back returns to Discover or closes a detail dialog. In the player, Play/Pause and media seek keys work; visible controls include seek, restart and back. The service supplies system media controls. Background audio is opt-in under Settings and applies to native media only.

## Implemented

- Native discovery, search, watchlist, details, local written reviews and continue watching.
- One real, playable demo: Blender Foundation's Big Buck Bunny. Demo provenance: https://peach.blender.org/ . No invented ratings or commercial film catalog.
- Import an HTTPS direct-media catalog using Android's document picker. See `catalog.example.json`; replace its placeholder URL before importing. The import replaces the prior imported catalog; the demo remains. IDs must be unique, including across the demo. Maximum 500 items / 1 MB.
- Native playback with Android MediaPlayer, MediaSession, foreground notification, audio focus and periodic progress saving. MP4 and device-supported HLS only; no DRM provisioning, DASH, offline downloads or transcoding.
- Website add-ons for NetMirror, MovieRulz, TamilTVSerial and TeluguStream, each with a separate enable toggle.
- NetMirror companion: select an already installed TV app under Settings; its APK opens separately. Astra does not install, modify, embed or redistribute NetMirror.
- Telugu News groups TV9, NTV, ETV's channel page and Sakshi's live page. These are official website links, not a guaranteed all-channel native live-feed catalog.
- Isolated website player/browser with JavaScript, user-initiated media playback, fullscreen video support, blocked new windows, blocked non-HTTPS navigation and a small known advertising-domain denylist. No JavaScript bridge, file access, TLS exception or external APK execution.

## Integration boundaries

These are **website companions**, not full catalog/stream provider integrations. No documented NetMirror SDK/API has been verified. Site video controls, login, geography, subscriptions, cookies, DRM and availability remain controlled by the provider. Third-party cookies and mixed content are blocked; this can prevent some embedded players from working. Pop-up-dependent playback may fail; the toolbar exposes an explicit external browser fallback. Ads served from the provider's own host or embedded in video may remain. The domain list is not complete ad removal. Nothing bypasses authentication, paywalls, DRM or access controls.

Cast and descriptions are provided for the demo and can be supplied in imported catalog JSON. Reviews are personal notes stored locally, not fetched audience reviews. Website videos do not automatically gain native metadata, reviews, watch history or background audio. The app does not enumerate or uninstall unwanted device apps.

## Privacy

Watchlist, reviews, imported metadata and playback positions are stored in app-private preferences. Clear app storage to reset everything. Websites and video servers receive normal network requests and may set cookies; Astra includes no analytics or advertising SDK. Website cookies are shared between the website add-ons in this app's WebView profile. Do not put sensitive authentication tokens into catalog URLs.

## Research references

- Nuvio TV reference release: https://github.com/NuvioMedia/NuvioTV/releases/tag/1.0.0 . The repository identifies GPL-3.0; this prototype contains no copied Nuvio code.
- NetMirror landing page: https://netmirror.gg/10/en-us . Advertises a separate Android TV APK.
- Android TV setup: https://developer.android.com/training/tv/get-started/create
- Android background playback guidance: https://developer.android.com/media/media3/session/background-playback . This dependency-free prototype uses platform MediaPlayer/MediaSession; a production version should evaluate Media3.

See `docs/VERIFICATION.md` for actual checks and remaining validation.
