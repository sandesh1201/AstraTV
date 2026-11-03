# Einthusan integration report

> **Removed in Turn 10 (2026-11-03).** At the owner's request the HD Movies tab and the whole
> Einthusan integration were deleted: `EinthusanAdapter`, `EinthusanCatalogProvider`,
> `EinthusanSimulatedProvider`, `SimulatedPartnerServer`, the Creative Commons fixture
> (`assets/simulation/einthusan_partner_v1.json`), the simulation add-on, the "Open einthusan.tv"
> button and `EinthusanContractTest`. The generic `LicensedProvider` contract and
> `SourceMovieCatalog` remain for the Movies tab and are covered by `MovieSourcesTest`. The report
> below is kept as a historical record.

## Status (historical): blocked on authorization, simulated

Einthusan publishes no third-party catalog or playback API and no partner programme that this
project could find. Every community integration reviewed (`docs/REPO_EVALUATION.md`) works by
scraping pages, reproducing the site's player requests, or downloading streams. Those methods are
not authorized by the content owner and are not used. `EinthusanAdapter.status()` reports the live
integration as unavailable, with that reason, everywhere the app mentions Einthusan.

What ships instead is the integration *shape*: a provider interface, a client for a documented
partner API, an in-process mock of that API, Creative Commons sample films, and contract tests.
If written permission and an official API are ever obtained, only the transport and credentials
change; catalog mapping, error handling, UI and tests stay.

## Integration method

| Piece | File | Role |
| --- | --- | --- |
| Provider contract | `LicensedProvider.java` | `authorization()`, `catalog(Query)`, `details(id)`, `playback(id)`; typed `ProviderException` codes |
| Partner client | `EinthusanSimulatedProvider.java` | Maps the partner JSON onto `Library.Title`; HTTPS-only streams; DRM-only titles refused |
| Mock server | `SimulatedPartnerServer.java` | Implements the API below in memory. It never opens a socket |
| Fixture | `assets/simulation/einthusan_partner_v1.json` | 7 entries: 4 Blender open movies (CC BY), plus DRM-only, region-blocked and HTTP-only edge cases |
| Wiring | `ProviderRepository`, `Library` | Add-on "Einthusan (simulation)", off by default, labelled as a simulation |
| Tests | `EinthusanContractTest` (12) | Labelling, paging, language mapping, search/details, quality from declared height (unknown is never HD), DRM-only refused, region reported not bypassed, HTTP streams dropped, typed 401/403/429 errors, malformed or missing responses, fixture provenance (no Einthusan endpoints, open licences only) |

## Hypothetical partner API (v1)

The contract the client expects from a licensed partner. All requests are HTTPS `GET` with
`Authorization: Bearer <app token>`. The token comes from the partner agreement and would be stored
with `SessionVault` (Android Keystore AES-GCM, origin-bound), never in source or BuildConfig.

| Endpoint | Response |
| --- | --- |
| `/v1/catalog?page&pageSize(≤50)&language&q` | `{page,totalPages,items:[summary]}` |
| `/v1/titles/{id}` | `summary` + `cast[]` |
| `/v1/titles/{id}/playback` | `{streams:[{url,protocol(hls/dash/mp4),maxHeight,drm,audioLanguages[]}]}` |

`summary` = `id, title, year, releaseDate, addedAt, languages[], audioLanguages[], genres[],
runtimeMinutes, synopsis, license, poster`.

| HTTP / error code | App behaviour |
| --- | --- |
| 401 | "The partner rejected the app credentials." |
| 403 | "The partner has not authorized this app." |
| 404 | "This title is no longer listed by the partner." |
| 429 | "The partner asked the app to slow down." |
| 451 or `region_unavailable` | "This title is not licensed for your region." No VPN or proxy workaround is offered |
| other / malformed | Partner unavailable or unsupported response; nothing falls back to scraping |

## Authorization required before a real integration

1. A written agreement or official API terms from the rights holder that cover a third-party
   Android TV client, the catalog territories, and display of artwork and synopses.
2. App credentials issued by the partner, plus a rotation and revocation process.
3. Approved playback: stream URLs issued by the partner. Where DRM is required, the partner's
   licence-server details and approval for Media3's Widevine path. DRM-only titles currently
   return `UNSUPPORTED_DRM`.
4. Rules for attribution, analytics and regional availability. Region errors are shown as-is and
   are never bypassed.

## Playback compatibility

Media3 plays the simulated HLS and MP4 sources. Verified on the Android 16 emulator, 2026-11-02:

- Tears of Steel HLS adapted between 224×100 and 1680×750. The Quality menu listed the real
  renditions (720p, 350p, 200p, 100p plus Auto). Fixing 720p held the 1680×750 rendition, and the
  label followed the decoded format.
- Resume restored the saved position, and the speed change to 1.5× was confirmed by the media
  session.
- A fixture that declares no height is labelled "Source · HLS", never HD.

## Blockers (current)

- No official API or permission. A live adapter is not implemented, by design.
- No DRM licence configuration. DRM-only titles cannot play.
- The fixture's language tags are simulated: the sample films are English-language and are marked
  as such in their synopses.
