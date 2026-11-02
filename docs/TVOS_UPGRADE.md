# Cinematic UI upgrade

The inspected application is Java/Views, not Kotlin/Compose. The user approved an in-place View upgrade and separate versioned metadata/settings stores. Existing provider contracts, JSON title serialization, library preferences, migrations, encrypted sessions, backup policy and redacted research log remain the compatibility boundary. No provider-resolution rewrite is part of this work.

## Plan and phase gates

1. **Core UI:** `coreui` package, bundled Inter/OFL license, charcoal tokens, frosted navigation, focus springs, shimmer and reduced motion. This is a theme layer within the existing app module, not a replacement application.
2. **Browse:** cinematic hero/shelves, UI genre taxonomy, newest-first ordering, composable filters, search/library paging. Preserve 48-item provider requests and live language → genre sections.
3. **Metadata:** separate Room cache and encrypted configuration, conservative matching/manual selection, source-labeled metadata and reviews. No source credentials or content URLs in the research log.
4. **Trailers:** delayed focus ownership, provider-first resolution, metadata/API lookups, native direct playback and an isolated official YouTube player. Failed or disabled previews leave artwork visible.
5. **Performance:** image/paging work, D-pad focus restoration, release optimization and repeatable profiling. Physical-TV 60fps is a measured acceptance criterion, not a claim inferred from compilation.

## Phase 1 — done

Changed: `coreui/TvTheme`, `FocusMotion`, `GlassPanel`, `ShimmerView`, `ColorExtractor`, `TvUi`, `Artwork` (optional decode callback), `MainActivity`, `styles.xml`, Inter assets and license. Top pill navigation replaces the side rail without replacing screens or provider models. API 31+ blurs a separate artwork layer so controls stay sharp; earlier/reduced-motion devices use the translucent solid fallback (≈16% white overlay on API 31+). Focus uses 1.10× springs, specular edge, soft elevation and a small parallax tilt. Palette tints the hero gradient from the focused artwork (cached). Reduced motion honors both the setting and the system animator scale.

TV checks: navigate all eight top tabs with D-pad; open a source/title/episode and Back; scroll behind navigation; refresh sources to see skeletons; enable Reduce motion and repeat. Test API 26 fallback and API 31+ rendering independently. Results: `docs/VERIFICATION.md`.

## Phase 2 — done

Cinematic Home (hero Play / Watchlist / More Info, chips, rating badge, Continue / New / Trending / Top Rated / per-genre), TMDB genre taxonomy with tests, newest-first sorting with tests, frosted combinable filters persisted in `astra_ui_v1` (not library JSON), raised grid cap, grouped/voice/recent search. Provider 48-item pages and live language → genre browsing stay. Results: `docs/VERIFICATION.md`.

## Phase 3 — done

Dedicated `metadata_v1.db` cache (not library JSON), TMDB matching with confidence tests and manual correction, source-labeled TMDB/OMDb/Trakt ratings, Settings API keys from `local.properties` / BuildConfig / `astra_ui_v1`. Unmatched or low-confidence titles never show invented scores.

## Phase 4 — done

Focus-to-trailer engine with tests for source priority and YouTube host allowlisting. Provider HTTPS URLs play in muted Media3; YouTube keys play in a locked-down official nocookie IFrame WebView. Settings: autoplay, sound, delay. Reduce motion disables trailers. Lookups are cached; the next shelf card is preloaded.

## Phase 5 — done

Artwork disk+memory cache and prefetch, per-row D-pad focus memory, long-press watchlist, R8 on release. Compose compiler reports do not apply. Physical 60fps remains a TV measurement.
