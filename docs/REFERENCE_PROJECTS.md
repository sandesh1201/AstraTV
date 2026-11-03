# Reference projects review (2026-11-03)

These are the projects and pages the product brief listed. GitHub metadata comes from the GitHub API on 2026-11-03: last push, license, archived flag and README. The other pages were checked for reachability, and no content was copied from any of them. A page marked "not opened" was assessed from its title and public description only.

Adoption rule: Saandio takes ideas or code only where the license allows it, and only for metadata or playback the app is authorized to use. Scrapers, downloaders and stream extractors for sites without distribution rights are not adopted, however recently they were updated.

## TMDB and metadata

| Project | Last push · license | Assessment | Decision |
|---|---|---|---|
| [TMDB application authentication](https://developer.themoviedb.org/docs/authentication-application) | Official docs (HTTP 200) | Defines the v4 Read Access Token (Bearer) and the v3 API key. | **Adopted.** The `backend/` proxy sends the developer's Bearer token, or the v3 key as a fallback, from server environment variables. Viewers never enter a key. |
| [collectionsdb tmdb-lib docs](https://collectionsdb.com/en/docs/tmdb-lib) | Docs page (HTTP 200) | A TMDB client library for web stacks. Not an Android dependency. | **Not installed.** Its endpoint coverage (trending, popular, discover, credits) matches what `TmdbCatalogSource` already calls. |
| [Medium: get a TMDB key without a live project](https://medium.com/@ozyurek.aydanil/how-to-get-a-tmdb-api-key-even-if-your-project-doesnt-exist-yet-fae8845f00c6) | Article, not opened | Describes the normal developer sign-up process. | **Applies to the developer only.** It's documented in `backend/README.md`. The key stays in backend secrets, never in the APK. |
| [publicapis.io · Movie DB API](https://publicapis.io/movie-db-api) | Directory page (HTTP 200) | A listing of the TMDB API. No code. | Nothing to adopt. |
| [projectai.in task page](https://projectai.in/projects/e38b435b-2ddf-4515-9c5b-c68875b91abe/tasks/bd01fcf1-6724-42b0-a9f3-70c57258cf4c) | Not opened (project workspace page) | No public code identified. | Nothing to adopt. |
| [tushar-2223/BlueBird-Movies](https://github.com/tushar-2223/BlueBird-Movies) | 2025-06-18 · **no license** | React TMDB website with trending, genre browse and bookmarks. Under the default copyright, no code can be reused. | **UI ideas only.** Separate Trending and Upcoming rows and a genre filter, all of which Saandio already has. No code taken. |
| [leonardogilrodriguez/mcp-tmdb](https://github.com/leonardogilrodriguez/mcp-tmdb) | 2025-06-22 · MIT | TypeScript MCP server for AI assistants that wraps TMDB. Not an app runtime component. | **Not installed.** It confirms the same read-only endpoint set the proxy whitelists. |
| [TVMaze API](https://www.tvmaze.com/api) | Official docs (HTTP 200) | Free, keyless, CC BY-SA, rate-limited. The schedule endpoints include air dates. | **Adopted in Turn 5.** `TvMazeSource` provides keyless TV episode rows with attribution. |

## Ratings

| Project | Last push · license | Assessment | Decision |
|---|---|---|---|
| [Reddit: IMDb ratings on Einthusan](https://www.reddit.com/r/SideProject/comments/1rq8s60/see_imdb_ratings_directly_on_einthusan_movie/) → [siddharthmr/einthusan-plus](https://github.com/siddharthmr/einthusan-plus) | 2025-05-21 · **no license** | Chrome extension (Manifest V3) that overlays IMDb ratings on einthusan.tv pages. | **Idea only:** show a rating next to the title, with its source. Saandio already shows the TMDB vote average and an optional OMDb rating, with the source named. No code taken (no license), and Saandio doesn't scrape IMDb. |

## Movie-source architecture (MovieRulz)

| Project | Last push · license | Assessment | Decision |
|---|---|---|---|
| [irfancharania/plugin.video.india4movie (movierulz.py)](https://github.com/irfancharania/plugin.video.india4movie/blob/master/resources/lib/sites/movierulz.py) | 2019-07-28 · MIT · **archived** | Kodi scraper for a piracy site. Unmaintained, and its selectors are long outdated. | **Rejected.** It scrapes a site without distribution rights. |
| [iseshu/movierulz](https://github.com/iseshu/movierulz) | 2025-09-18 · no license | "Unofficial API" that scrapes movierulz and returns magnet and hoster links. | **Rejected.** It serves piracy and torrent links. Saandio's `ProviderRiskScanner` already flags MovieRulz-style sources. |
| [thirud4a4/movies_m3u](https://github.com/thirud4a4/movies_m3u) | audited 2026-11-03 | The playlist behind the old MovieRulz add-on. Its entries are copies of recent theatrical films taken from MovieRulz pages and served through a third-party `workers.dev` proxy. Many HLS entries are tagged `ext=.mp4`, which caused the player's media-type mismatches. | **Retired (Turn 7).** It is unlicensed content, so the add-on, its parser and the cached playlist are gone. All Movies stays empty and states the reason (`MovieRulzCatalogProvider.REASON`). |

## Einthusan

| Project | Last push · license | Assessment | Decision |
|---|---|---|---|
| [aban-waleed/einthubot](https://github.com/aban-waleed/einthubot) | 2026-07-18 · MIT | Self-hosted tool that **downloads** Einthusan Premium films into Jellyfin. | **Rejected.** A downloader that copies subscription video. Saandio has no Einthusan agreement. |
| [gsbmk007/Einthusan-Stremio-Addon-](https://github.com/gsbmk007/Einthusan-Stremio-Addon-) | 2025-12-21 · NOASSERTION | Stremio addon that extracts Einthusan streams for playback in Stremio. | **Rejected.** It is stream extraction without authorization. |
| [srid99/einthusan-dl](https://github.com/srid99/einthusan-dl/blob/master/einthusan/einthusan_dl.py) | 2020-03-13 · no license | Python downloader, unmaintained. | **Rejected.** A downloader. |
| [ram2141/repository.einthusan](https://github.com/ram2141/repository.einthusan) | audited 2026-11-03 | Kodi add-on repository. The add-on scrapes einthusan.tv HTML for language, category and search listings, logs in with the user's Premium credentials, and decodes the site's obfuscated stream links. | **Ideas only:** language-first browsing, "recently added" ordering and per-title quality labels, all already present in Saandio. Its playback path decodes protected links, which bypasses the provider's access control, so it is not used. A Kodi add-on also can't run on Android TV as an app component. |

**Turn 9 review (2026-11-03, GitHub API and repository pages, read-only):**

| Project | Last push · license | Assessment | Decision |
|---|---|---|---|
| [Gujal00/GujalKodiWork](https://github.com/Gujal00/GujalKodiWork) | 2026-10-07 · GPL-2.0 | Gujal's Kodi add-on collection. The repository tree has no Einthusan source file today; historic Einthusan add-ons in this family scrape einthusan.tv pages and decode player links. | **Not used.** GPL-2.0 would also bind Saandio's code, and the approach is automated access that Einthusan's terms forbid. |
| [knagaraju/GujalKodiWork](https://github.com/knagaraju/GujalKodiWork) | 2017-02-22 · Apache-2.0 | Unmaintained 2017 fork of the same collection. | **Not used.** Same approach, and its page selectors are long stale. |
| [RupGautam/einthusan-cli](https://github.com/RupGautam/einthusan-cli) | 2020-04-25 · no license | "Einthusan CLI tool to extra[ct] streaming url." | **Rejected.** It extracts stream URLs from the protected player. With no license, it couldn't be reused anyway. |
| [ram2141/einthusan](https://github.com/ram2141/einthusan) | 2020-04-20 · no license | Kodi add-on with Hindi, Tamil, Telugu, Malayalam, Kannada, Bengali, Marathi and Punjabi sections, plus search. | **Ideas only:** the language list, which matches the HD Movies language filter. Its playback decodes protected links, so it is not used. |
| [ram2141/repository.einthusan](https://github.com/ram2141/repository.einthusan) | 2020-04-20 · MIT (repository wrapper) | The Kodi repository that ships the add-on above. | Same as above. |

**Einthusan's own channels (checked 2026-11-03):**
- `einthusan.tv` redirects to `/intro/`. The intro page says "We support iOS, Android, and Gaming Consoles. Stay tuned for our upcoming SmartTV support."
- `/api/`, `/premium/`, `/faq/`, `/apps/` and `/devices/` all return the site's "Page Not Found" page.
- No Play Store app exists under `tv.einthusan`, `com.einthusan`, `com.einthusan.app`, `tv.einthusan.app` or `com.einthusan.android`. The only search match, "Einthusan AI" (`com.einthusanai.einthusan`, developer LAMPORT), is a third-party app.
- The Terms of Use:
  - prohibit "accessing the Einthusan Services (including the Content) through any automated means, including robots, spiders";
  - prohibit "disabling, reverse engineering, modifying, interfering with or otherwise circumventing the Video Player";
  - permit viewing only on Einthusan's site, on sites where Einthusan offers an embed, and in "Einthusan authorized applications".
- No embed option or partner API is published.

**Conclusion:**
- There is no authorized catalog feed, player integration or Android TV handoff to integrate.
- HD Movies stays empty with `EinthusanCatalogProvider.unauthorizedReason()` and offers "Open einthusan.tv", which needs a web browser on the TV.

Saandio keeps Einthusan behind the `LicensedProvider` contract, as a clearly labelled simulation (`EinthusanSimulatedProvider`) built on Creative Commons samples. A real integration needs an official partner API and an agreement.

**Turn 10 update (2026-11-03):** the HD Movies tab, `EinthusanCatalogProvider`, the simulation and its assets were removed at the owner's request. Nothing in Saandio contacts einthusan.tv now. The findings above are kept as the record of why no integration was possible.

## Catch-Up TV sources (Turn 10)

| Source | What Saandio reads | What it does not do |
|---|---|---|
| `https://www.tamiltvserial.com/feed/` and `https://telugustream.com/feed/` (standard WordPress RSS) | Post titles, categories and dates, paged with `?paged=N` at most 12 pages, 400 ms apart, conditional requests. These give the show name, episode number, air date and broadcaster. | It does not open post pages, run their players, follow video links or store any URL from the feed. These sites re-post broadcaster episodes without a licence, so Watch hands off to the broadcaster's official app (Sun NXT, JioHotstar, ZEE5, ETV Win, Sony LIV). |

## Live TV

| Project | Last push · license | Assessment | Decision |
|---|---|---|---|
| [kananinirav/Indian-IPTV-App](https://github.com/kananinirav/Indian-IPTV-App) | 2026-06-24 · MIT · 336★ | Flutter M3U player for public Indian playlists. Its own disclaimer says the streams come from third parties. | **Patterns only:** language and category browsing, and a TV-specific build, which Saandio already has. No Dart code (different stack). Like it, Saandio states that directory streams are not verified for licensing. |
| [GitHub topic: live-tv-india-app](https://github.com/topics/live-tv-india-app), [topic: indian-channel](https://github.com/topics/indian-channel) | Topic listings, not opened individually | Mostly M3U players built on public playlists. | No dependency adopted. The channel directory stays iptv-org, labelled as unverified, with health checks and retirement of channels that keep failing. |

## OTT discovery and extensions

| Project | Last push · license | Assessment | Decision |
|---|---|---|---|
| [Reddit: IndiaStreams Stremio addon](https://www.reddit.com/r/StremioAddons/comments/1n19ysf/indiastreams_the_ultimate_stremio_addon_for/) | Post, not opened | An addon that aggregates Indian OTT streams. | **Not adopted.** For "where to watch", Saandio uses TMDB watch providers (JustWatch data, attributed), which point viewers to the legal service. |
| [CloudStream extension list](https://cloudstream.miraheze.org/wiki/List_of_extensions) | Wiki (HTTP 200) | Community repositories, many scraping unlicensed hosts. | **Architecture idea only:** a pluggable provider interface, already present as `LicensedProvider` and the add-on manifests. No extensions installed. |

## Live TV, EPG and catch-up (Turn 8)

The full review table is in [LIVE_TV_AND_CATCHUP.md § 3](LIVE_TV_AND_CATCHUP.md#3-sources-reviewed). In short:
- **Used read-only:** iptv-org/database (duplicate detection and DMCA audit).
- **Rejected:** mitthu786/tvepg (EPG and catch-up taken from subscription apps), and david0154/david-tv and arshadakl/streamhub (no license, unverified lists).
- **Adopted for Catch-Up TV:** the official Doordarshan / Prasar Bharati YouTube channels, through the YouTube Data API on the backend, with playback in the YouTube app.
- **Official app handoff only:** Sun NXT and WAVES. Neither has a public API or deep links.

## Result

- **No third-party code was imported.** No reviewed repository is both usefully licensed and authorized for content access.
- **Adopted:** TMDB application authentication (server-side), TVMaze and Wikidata open data, and the UI patterns listed above. They are implemented in Saandio's own code.
