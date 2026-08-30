# Audit — Library detail component extraction and approved visual polish

Date: 2026-08-30

## Scope

This checkpoint is stacked on Draft PR #63 exact head:

`24b4d40629653e9322a0b9f8ca23172ebebfab39`

Branch:

`agent/library-details-component-extraction-no-apk`

The purpose is to extract Movie and Series detail presentation from the large route files into dedicated components and apply the approved flat/purple visual language without changing playback, download, progress, routing or TV/mobile Offline semantics.

## Mechanical extraction

### Movies

`MovieDetailsPane` was moved from `VodRoute.kt` into:

`app/src/main/java/app/ownplay/player/ui/vod/MovieDetailsPane.kt`

The route call sites and function arguments remain unchanged.

Patch-level inspection confirms that `VodRoute.kt` removes one contiguous details block between `ContinueWatchingCard` and `VodPlaybackScreen`. The current route now transitions directly from the catalog presentation to the existing playback composable.

### Series

The following presentation hierarchy was moved from `SeriesRoute.kt` into:

`app/src/main/java/app/ownplay/player/ui/series/SeriesDetailsPane.kt`

- `SeriesDetailsPane`
- `SeriesSeasonRow`
- `SeriesSeasonHeader`
- `SeriesEpisodeDetailsPane`
- `EpisodeRow`

The route call sites and function arguments remain unchanged.

Patch-level inspection confirms that `SeriesRoute.kt` removes one contiguous details block between `SeriesCatalogPane` and `SeriesPlaybackScreen`. The current route now transitions directly from the catalog presentation to the existing playback composable.

## Movie visual polish

The extracted Movie details presentation now uses:

- flat background / 0 dp tonal elevation
- 18 dp horizontal / 12 dp vertical page insets
- 10 dp action geometry
- 184 dp 2:3 poster treatment
- purple/accent secondary `Movie` label
- flat 10 dp completed-download surface on mobile
- flat 10 dp About surface with 0 dp elevation
- fixed typography hierarchy without state-dependent geometry

## Series visual polish

The extracted Series detail hierarchy now uses:

- flat root presentation / 0 dp elevation
- 10 dp fixed surfaces for seasons and episodes
- 0 dp tonal elevation on season/episode cards
- stronger poster hierarchy for season and episode details
- fixed Medium typography where presentation state previously felt heavier
- consistent 10 dp Play/Download action geometry
- reduced divider-heavy layout
- purple/accent secondary hierarchy labels

## Functional invariants preserved

### Movie

The Movie component retains:

- `offlineCopyAvailable = !isTelevision && download?.state == COMPLETED`
- TV Back focus restoration through the existing `FocusRequester`
- Play callback through the existing `onPlay(movie)` contract
- Favorite callback through the existing `onFavoriteChanged(...)` contract
- Clear progress condition based on existing viewing progress
- Download enqueue/pause/resume/retry callback mapping
- mobile-only Download/Offline presentation
- unchanged download progress-label semantics

### Series

The Series component retains:

- existing Series → Season → Episode hierarchy
- existing TV Back focus restoration rule
- episode lookup by `SERIES_EPISODE` + `contentId`
- `offlineCopyAvailable = !isTelevision && download?.state == COMPLETED`
- mobile-only Download/Offline presentation
- Play/Download/Pause/Resume/Clear callbacks
- unchanged download progress-label semantics

## Playback / architecture boundary

No changes were made to:

- `VodPlaybackScreen` behavior
- `SeriesPlaybackScreen` behavior
- playback request creation
- Media3 / PlayerView binding
- decoder or surface ownership
- playback progress persistence
- download repository / worker / storage engine
- Room/schema
- source sync
- auth
- signing
- release/deployment

No APK is requested or authorized.

## Static validation performed

- PR #63 exact base head verified before branching
- final branch remains strictly ahead of the exact base with no behind drift
- Movie extraction patch inspected as one contiguous removal block
- Series extraction patch inspected as one contiguous removal block
- current route boundaries re-read after extraction
- new Movie component re-read after creation
- new Series component re-read after creation
- TV/mobile Offline guards re-verified in the extracted components
- playback composables remain in the route files after the extracted blocks

## Known finding discovered during audit — aggregate `All`

This checkpoint deliberately does **not** change category/filter semantics because it is scoped to presentation extraction.

However, source audit found that old standalone/embedded VOD and Series catalog panes still contain reachable aggregate entries:

- Movies portrait category strip: `All`
- Movies landscape navigation rail: `All Movies`
- Series catalog filter: `All`

These are not dead code. `OwnPlayApp` routes Library detail requests into `VodRoute` / `SeriesRoute`; on landscape/TV those catalog panes can remain visible beside the selected detail. Therefore the aggregate labels can still be presented even though the unified Library itself already follows the product decision of no visible `All`.

Recommended remediation is a separate narrow functional slice that removes those aggregate entries and resolves category selection to the selected item's real category when opening details, otherwise the first real provider category. This should also preserve deterministic TV focus when the Series `All` chip is removed.

## Validation not executed on this exact head

- Gradle compile
- unit tests
- lint
- AndroidTest compilation
- physical smartphone QA
- physical Android TV / TV Box QA

A skipped GitHub Actions workflow, if observed, must not be treated as compile/test PASS.

## Physical QA still required

### Smartphone

- Library → Movie details visual hierarchy
- Play / Resume
- Favorite
- mobile Download / Offline presentation
- Clear progress
- Library → Series → Season → Episode hierarchy
- episode Play / Resume
- mobile episode Download/Offline state

### Android TV / TV Box

- Library → Movie details Back focus
- no Movie Download/Offline presentation
- Library → Series → Season → Episode hierarchy
- Back focus at each Series hierarchy level
- no Episode Download/Offline presentation
- D-pad focus remains deterministic through extracted components

## Operational boundary

Draft only. Do not merge, mark ready, deploy, release, publish, force-push, rewrite history, alter signing or create an APK without explicit authorization.
