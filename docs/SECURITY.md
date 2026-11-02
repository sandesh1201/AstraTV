# Security notes · Saandio TV 0.3

Defensive review of this app only. No third-party streaming service was attacked. The public repository `slrwin/sunnyrepo` exposed no README or add-on source during review, so none of its code was copied or executed.

## Findings

| Finding | Severity | Component | Status |
|---|---|---|---|
| Research export is an allowlist (provider, stage, success, time) | Info | `ResearchLog` | Mitigated. Regression: `ResearchEditionTest.researchExportUsesAnAllowlistInsteadOfSanitizingSecrets` |
| Playback headers drop Cookie, Authorization, and CRLF | Info | `PlaylistParser.safeHeaders` | Mitigated. Regression: `ResearchEditionTest.headersDropCredentialsAndCrLfInjection` |
| Catalog and artwork requests are HTTPS-only with redirect and size limits | Info | `ProviderRepository`, `Artwork` | Mitigated. HTTP and credentialed URLs fail `Library.https` |
| HTML and gate pages are not treated as media | Info | `StreamProbe` | Mitigated. Regression: `ResearchEditionTest.probeRecognizesHlsBehindMisleadingMp4ContentType` |
| Provider sessions are origin-bound AES-GCM | Info | `SessionVault` | Mitigated. Regression: `ResearchEditionTest.encryptedSessionsAreOriginBoundAndUseUniqueNonces` |
| YouTube trailer WebView is limited to the official nocookie embed | Info | `YoutubeTrailerView` | Mitigated. Regression: `TrailerResolverTest.youtubeEmbedAcceptsOnlyNocookieHttps` |
| Einthusan has no official API | Info | `EinthusanAdapter` | Not integrated. Regression: `PlaybackPolicyTest.einthusanStaysUnavailableWithoutAnOfficialApi` |
| Playback can be required to wait for an OS VPN | Info | `PlaybackPolicy`, `NetworkStatus` | App gate only. Not a kernel kill switch. Regression: `PlaybackPolicyTest.vpnRequirementBlocksPlaybackUntilATunnelExists` |
| Unknown stream names are not labeled HD | Info | `PlaybackPolicy.qualityLabel` | Mitigated. Regression: `PlaybackPolicyTest.qualityIsLabeledOnlyWhenTheSourceNameStatesIt` |
| Any configured HTTPS catalog host can be requested | Residual | `ProviderRepository.request` | By design for user-supplied manifests. Not an open redirect into the player: non-media responses fail the probe |

## Security Research Dashboard (2026-11-02)

`SecurityDashboardActivity` is a separate, non-exported activity, outside the movie UI. Only
authorized testers can open it:

- The entry appears in Settings only when the build has a tester PIN.
- Set `security.tester.pin` in `local.properties` (or the `ASTRA_TESTER_PIN` environment variable)
  and rebuild. Only a PBKDF2-HMAC-SHA256 hash (120k iterations, salt `security.tester.salt`) is
  compiled in. The PIN itself never enters the APK or Git.
- Five wrong PINs lock the dashboard for 5 minutes. The window sets `FLAG_SECURE`, so screenshots
  come out black, which was confirmed on the emulator.

Each finding has a title, severity, component, reproduction, impact, remediation, status,
detection date and a live retest. Retests run inside the app against its own code, settings and
offline fixtures. **No external system is probed.** The dashboard also maps findings onto the 20
requested test areas and runs the provider risk scanner over cached catalogs, using URL checks
only.

| ID | Severity | Finding | Component | Status |
|---|---|---|---|---|
| AST-01 | Medium | Provider redirects could reach local network addresses | `ProviderRepository.request` | Fixed |
| AST-02 | Medium | "Trending Now" was ranked by provider rating | Home shelves | Fixed |
| AST-03 | Medium | API keys compiled into BuildConfig are extractable from the APK | `app/build.gradle` | Mitigated by process |
| AST-04 | Low | OMDb key travels in the URL query string | `MetadataRepository.fillRatings` | Accepted risk |
| AST-05 | High | Cleartext HTTP must stay disabled | Manifest | Verified control |
| AST-06 | High | Only the launcher activity may be exported | Manifest | Verified control |
| AST-07 | High | Security dashboard must be gated and non-exported | `SecurityDashboardActivity` | Verified control |
| AST-08 | Medium | Trailer WebView must stay locked to the YouTube embed | `YoutubeTrailerView` | Verified control |
| AST-09 | Info | CSRF does not apply; cookie state must stay empty | Networking | Not applicable |
| AST-10 | Medium | Local databases must use parameterised queries (live injection probe) | `MetadataStore`, `CatalogIndex` | Verified control |
| AST-11 | Medium | Provider sessions are sealed and bound to their origin | `SessionVault` | Verified control |
| AST-12 | High | Stream URLs must be HTTPS, credential-free and not installers | `Library.https`, scanner | Verified control |
| AST-13 | High | VPN must not leak DNS or IPv6, or claim protection early | `VpnController` | Verified control |
| AST-14 | High | Platform TLS validation is used without overrides | Networking | Verified control (CI) |
| AST-15 | Low | Dependencies need routine updates | `app/build.gradle` | Open (Dependabot) |
| AST-16 | Info | Debug builds are debuggable | Build | Expected for debug |
| AST-17 | Medium | App backups must stay disabled | Manifest | Verified control |
| AST-18 | High | Piracy-style mirror pages carry ads, pop-unders and installers | MovieRulz (assessment) | Open (owner decision) |

Emulator run (debug build, no VPN config): 15 pass, 0 fail, 3 not testable here (AST-13 has no
config, AST-15 is checked in CI, AST-16 is a debug build). `SecurityDashboardTest` covers field
completeness, coverage of all 20 areas, a hardened environment with no failures, regressions
failing their own retests, the AST-13 config and live paths, and the PIN gate with lockout.

## Provider risk scanner

`ProviderRiskScanner` scores pages, playlists and URLs with severity-weighted rules for:

- URLs: insecure, private host, IP literal, userinfo, punycode, shortener, executable, and
  `url.wrapped`, a stream relayed through a proxy that fetches another URL.
- Redirects: chains, downgrades, cross-host hops.
- Page content: content-type mismatch, ad networks, pop-unders, obfuscated or cryptominer
  scripts, hidden iframes, meta refresh.
- Quality: claims above what the stream offers.

It runs only on offline fixtures (`app/src/test/resources/risk/`, synthetic `.invalid`/`.test`
domains) and on catalogs the app already cached. It never fetches a site to scan it. On the
emulator, the cached MovieRulz catalog scored Low risk for `url.wrapped`, because its streams pass
through a third-party relay.

## CI

`.github/workflows/android.yml` runs Gradle wrapper validation and grep gates first:

- no custom TrustManager or HostnameVerifier, and no `onReceivedSslError`;
- no `+` dependency versions;
- no Google API keys, private keys or WireGuard private keys in `app/src`, `docs` or the README;
- `local.properties` is not tracked, and cleartext stays off.

It then runs `assembleDebug lintDebug testDebugUnitTest` and uploads the reports.
`.github/dependabot.yml` opens weekly Gradle and Actions updates. The grep gates were run locally
with Git Bash and pass. A planted `1.+` version was caught.

## Not claimed

- The WireGuard tunnel is real but only as good as the user's server. No provider infrastructure
  was used and no real server was tested. See `docs/VPN.md`.
- The app-level kill switch covers playback only. The device-wide kill switch is Android's
  Always-on + Block connections without VPN.
- Einthusan is simulated, not integrated (`docs/EINTHUSAN.md`). Kodi add-ons are not downloaded or
  executed, and repository scripts were reviewed statically and never run.
