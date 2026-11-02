# Existing application audit (baseline, branch `feature/einthusan-vpn-security`)

Recorded before any Turn 3 change. Numbers come from the last green build on this branch.

## Baseline

| Metric | Value |
| --- | --- |
| Debug APK | 6,468,029 bytes |
| Java sources (`app/src/main/java`) | 2,250 lines |
| Unit tests | 40 passing, 0 failing |
| Lint | 0 errors, 34 warnings (13 `SetTextI18n`, 10 `GradleDependency`, rest minor) |
| Exported components | `MainActivity` only (launcher). `PlayerActivity` and `PlaybackService` are not exported. |
| Manifest hardening | `allowBackup=false`, data-extraction rules, `usesCleartextTraffic=false` |

## Architecture

- Java + Android Views, single `MainActivity` shell that swaps screens; `PlayerActivity` is a thin
  Media3 `PlayerView` bound to `PlaybackService` (a `MediaSessionService` that owns one ExoPlayer).
- `ProviderRepository` is the only network path for catalogs: catalog → details → streams. It
  allows HTTPS only, at most 5 redirects, 4 MB bodies, and strips custom headers on cross-host redirects.
- Providers: Stremio-style manifests (user configured), NetMirror x3 (session tokens sealed by
  `SessionVault` with an Android Keystore AES-GCM key bound to the origin), MovieRulz public M3U
  playlist, Tamil/Telugu catch-up pages (jsoup), Indian live TV (IPTV-org), library JSON import and
  Blender sample titles.
- Metadata: TMDB, OMDb and Trakt through `MetadataRepository`, cached in `metadata_v1.db`.
- Trailers: provider HTTPS URL, otherwise the official YouTube embed in a locked-down WebView.
- Storage: `astra` preferences (library, positions, provider config), `astra_ui_v1` (UI settings and
  API keys), `metadata_v1.db`, and file caches `catalogs-v3` and `artwork-v1`.

## Existing functionality

Home hero and shelves, filters and sorting (newest first by default), details with metadata,
search with voice input, watchlist, continue watching, add-on management, source health research
log, background audio, VPN status screen and an optional "Require VPN before playback" gate.

## Bugs and security weaknesses found

| # | Finding | Severity | Component | Action this turn |
| --- | --- | --- | --- | --- |
| A1 | Provider-controlled redirects and manifest URLs may point at loopback, link-local or private IP literals (device-side SSRF into the LAN). | Medium | `ProviderRepository.request`, `Library.https` | Fixed: private and loopback hosts are refused; regression tests added. |
| A2 | "Trending Now" shelf is ranked by provider rating, which is not trending data. | Medium (misleading UI) | `MainActivity.renderHomeShelves` | Fixed: renamed to Top Rated; Trending uses TMDB trending only when a key exists. |
| A3 | No in-app VPN tunnel; the gate only observes an OS VPN. | Info | `NetworkStatus` | Added an optional WireGuard tunnel using a user-imported config (see `docs/VPN.md`). |
| A4 | The player has no quality picker, speed control or automatic retry; a network error stops playback. | Low | `PlayerActivity` | Added quality from real tracks, speed, and bounded retry backoff. |
| A5 | Generic embed following for Tamil/Telugu catch-up pages is a scraping path. | Medium (legal/ToS) | `ProviderRepository.resolvePage` | Kept (existing feature, rule 2) but documented; new providers must not use it. |
| A6 | MovieRulz and NetMirror sources have unknown licensing. | High (legal) | Providers | Not changed; assessed offline with the risk scanner; decision stays with the owner. |
| A7 | No CI. | Low | Repo | Added a GitHub Actions workflow for build, lint and unit tests. |
| A8 | No separate security tooling; findings lived only in docs. | Low | — | Added the PIN-gated Security Research Dashboard. |

## Recommended improvements (priority order)

1. Einthusan: obtain written permission and an official API before any real integration; until then
   use the simulated adapter (`docs/EINTHUSAN.md`).
2. Keep the WireGuard tunnel opt-in and config-only; never bundle servers.
3. Replace remaining scraping providers with licensed catalogs when available.
4. Move user-visible strings to resources (clears the `SetTextI18n` warnings, enables localisation).
5. Update dependencies flagged by lint after a separate compatibility pass.
