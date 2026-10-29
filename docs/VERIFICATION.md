# Verification record

## Scope

This is an initial functional prototype, not a finished Netflix-equivalent service. Build and device checks are recorded below as they are run. A successful compile is not proof of source availability or compatibility with every TV.

## Checks performed (2026-10-29)

- `assembleDebug`: passed. Installable debug APK produced.
- `lintDebug`: passed with zero errors and 18 warnings. Warnings include localization, platform version/orientation, intentional website JavaScript, vector banner sizing and drawing allocations. The media notification permission warning is narrowly suppressed because MediaSession notifications have an explicit platform exemption.
- `apksigner verify --verbose`: passed, APK signature scheme v2, one debug signer.
- Installed successfully on the existing Pixel 9a emulator, resized to 1920×1080 at 240 dpi. This is a phone emulator, not an Android TV image.
- Main activity launch returned `Status: ok`. Visually inspected home screen and focused navigation control. Opened the sample details dialog and inspected its cast/description, review and playback controls.
- Launched native player; foreground service and active AstraTV MediaSession appeared. Sample remained buffering; actual video/audio, seeking, resume and background playback were **not verified**. A 30-second preparation timeout was added so unavailable media does not leave an endless loading state.
- Emulator initially suffered a System UI ANR; wake/unlock and relaunch recovered the home screen. No Astra app crash was observed in inspected AndroidRuntime output.
- Third-party website playback, ad coverage, NetMirror companion launch, catalog import persistence and physical remote behavior remain unverified.

Screenshot evidence from emulator inspection is stored in `build/astra-final.png`. It is a development artifact and excluded by `.gitignore`.

## Device acceptance checklist

- Launch from the Android TV launcher; navigate all controls with D-pad only.
- Open the demo, verify actual audio/video, pause, seek, return and resume.
- Enable background audio, press Home, verify notification/system media controls, then stop playback.
- Interrupt audio focus with another media app; verify pause and transient resume.
- Add/remove a watchlist item and save a review; restart the app and verify persistence.
- Import valid JSON, duplicate IDs, non-HTTPS URLs and oversized JSON; verify errors preserve the prior catalog.
- Test offline media, unavailable URLs and unsupported codecs; verify recovery.
- Open each website add-on, test fullscreen, Back, popup blocking, login and unavailable-page handling.
- Install a trusted NetMirror TV APK separately, choose it under Settings and verify companion launch.
- Test official news pages from the user's region. Verify which live embeds work on the target TV WebView.

No claims are made that all third-party site content, all ads, all Telugu news channels or NetMirror internals have been integrated or verified.
