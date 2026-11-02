# Third-party repository evaluation

Method: GitHub metadata API, file trees and static text search of selected source files. No
repository code was cloned, installed or executed, and none was copied into Saandio TV (formerly Astra TV).

| Repository | Language / license | Last push | What it does | Security and legal concerns | Recommendation |
| --- | --- | --- | --- | --- | --- |
| slrwin/sunnyrepo | Python / none declared | 2026-10 | Kodi repository that redistributes the Fen and Fen Light video add-ons and a maintenance script. Includes TMDB, Trakt, OMDb, debrid and scraper integrations. | No license, so code cannot be reused. Fen relies on debrid and third-party scraper sources. The maintenance script changes Kodi installs. 240 MB of redistributed binaries/zips. | **Reject** code. **Redesign** ideas only: layered metadata cache with TTLs and Trakt-style lists (Astra TV already has `metadata_v1.db`). |
| ram2141/repository.einthusan | Python / MIT | 2020-04 | Kodi add-ons that browse Einthusan by language, decade, year and rating, and play videos. | Decodes Einthusan's obfuscated stream token, sends spoofed Referer/User-Agent headers and handles premium login cookies. That is stream extraction and access-control handling. Unmaintained since 2020. | **Reject** playback code. **Redesign** the browse model (language → alphabet / decade / year / rating) for licensed catalogs. |
| asaddon/EinthusanTV | JavaScript / none declared | 2026-09 | Stremio add-on plus scheduled scraper that builds Einthusan catalogs, maps titles to IMDb/TMDB and caches them in Cloudflare KV. | No license. Scrapes Einthusan on a schedule (GitHub Actions), stores credentials for login, and serves the result to other users. | **Reject** code. **Redesign** idea: precomputed per-language catalogs with an edge cache and IMDb/TMDB mapping (Astra TV uses `MetadataMatcher`). |
| srid99/einthusan-dl | Python / none declared | 2020-03 | Command-line downloader for Einthusan movies. | Download tool; explicitly out of scope. | **Reject.** |
| robins/masala-script | JavaScript / Apache-2.0 | 2026-03 | Browser userscript that adds IMDb ratings (OMDb, Wikipedia, IMDb GraphQL) to Einthusan pages, with local caching. | Also builds an `aria2c` download command from the playing video URL on watch pages. That part is a download mechanism. | **Reject** the download helper. **Reuse concept** only (license allows): rating lookups with a persistent cache — already covered by `MetadataRepository` and OMDb. |
| coder-alpha/Einthusan.bundle | Python / none declared | 2020-03 | Plex plug-in for Einthusan that ships a headless browser (SlimerJS + GhostDriver). | Uses a headless browser and cookie handling to obtain streams; bundles a large, outdated browser runtime. | **Reject.** |

## Patterns worth keeping (re-implemented from scratch, no code copied)

- Language-first navigation with decade, year and rating sub-filters: already present through
  `BrowseFilter` and extended this turn with multi-select languages and genres.
- External-ID mapping (IMDb/TMDB) to enrich thin catalogs: present through `MetadataMatcher`.
- Precomputed catalogs with a cache: present through `catalogs-v3` and the new first-seen index.

## Test further

Nothing in these repositories qualifies as a licensed Einthusan integration method. A real adapter
needs an official API or written agreement from Einthusan (see `docs/EINTHUSAN.md`).
