# Audit — Library details approved visual polish

Date: 2026-08-30

## Scope

This checkpoint is stacked on exact Draft PR #62 head:

`0ee7bc44a1d5e6f542a6288a209373d032d1f015`

Branch:

`agent/library-details-approved-visual-polish-no-apk`

The goal is to continue the approved dark/purple/flat visual system into media detail presentation while preserving the reliability and TV/mobile presentation boundaries already established.

## Source audit before mutation

### Movie details

`VodRoute.kt` owns Movie details and playback. The current details path already preserves the TV/mobile offline boundary:

- `offlineCopyAvailable` is gated by `!isTelevision`.
- all download/offline presentation is inside `if (!isTelevision)`.
- TV playback labels therefore remain normal Play/Resume semantics.
- the Movie playback request and playback screen are outside this visual checkpoint.

The Movie details route file is large and also contains catalog, playback, progress, download and TV focus logic. This checkpoint deliberately does not perform a whole-file rewrite solely for visual polish.

### Series details / episodes

`SeriesRoute.kt` owns Series hierarchy, season lists, episode lists, episode details and playback. The current episode row already preserves the TV/mobile offline boundary:

- `offlineCopyAvailable` is gated by `!isTelevision`.
- download buttons/progress/failure/offline labels are non-TV only.
- TV retains normal Play/Resume and viewing-progress semantics.

The Series route file is also large and combines catalog, hierarchy, downloads, progress, focus and playback. This checkpoint deliberately avoids an atomic whole-file replacement solely to adjust row geometry.

## Implemented visual changes

### Shared poster presentation

File:

`app/src/main/java/app/ownplay/player/ui/vod/RemotePoster.kt`

Changes:

- poster corners aligned from 12 dp to the approved 10 dp geometry;
- placeholder surface uses a flatter neutral `surfaceVariant` treatment;
- placeholder letter typography is reduced from `headlineMedium` to `titleLarge` with fixed Medium weight;
- placeholder text contrast is softened to match the approved visual language.

Preserved:

- poster URL handling;
- shared `SourceHttpClient` usage;
- 8 MiB response bound;
- decode/downsample policy;
- IO dispatcher usage;
- `ContentScale.Crop`;
- byte-reading and sample-size helpers.

Because `RemotePoster` is the shared poster primitive, this visual alignment propagates to Movie details, Series details, seasons, selected episode details and catalog artwork without changing data or navigation behavior.

### Series information summary

File:

`app/src/main/java/app/ownplay/player/ui/series/SeriesInfoSummary.kt`

Changes:

- poster width increased slightly from 96 dp to 104 dp for stronger detail hierarchy;
- metadata is rendered as one compact dot-separated block instead of one Text row per field;
- rating uses the same star-first convention as Movie metadata;
- spacing is aligned to the current Library/Live visual system;
- About surface is fixed 10 dp geometry, 0 dp tonal elevation and a flatter neutral fill;
- About heading uses fixed Medium weight;
- content insets increased slightly for a cleaner detail card.

Preserved metadata:

- rating;
- release date;
- genre;
- country;
- season count;
- episode count;
- director;
- cast;
- description.

No Series domain/data transformation was changed.

## Deliberately unchanged in this checkpoint

The following route-local presentation remains unchanged:

- Movie Play/Favorite/Clear progress action row;
- mobile Movie Download/Offline status block;
- Series season row geometry;
- Series episode row geometry;
- Series episode action row;
- playback screens;
- playback requests;
- progress persistence;
- download enqueue/pause/resume behavior;
- TV focus/back hierarchy.

This is intentional. The connector exposes whole-file replacement for these large route files, and a broad rewrite would add disproportionate reliability risk for a cosmetic-only change.

## Architecture boundary

No changes to:

- Media3/player engine;
- decoder/surface ownership;
- playback request resolution;
- Room/schema;
- downloads/storage engine;
- source sync;
- auth;
- signing;
- release/deployment.

No APK is requested or authorized.

## Static validation completed

- exact PR #62 head verified before branching;
- Movie details offline/TV guards inspected;
- Series episode offline/TV guards inspected;
- modified `RemotePoster.kt` re-read after mutation;
- modified `SeriesInfoSummary.kt` re-read after mutation;
- compare before evidence: 2 commits ahead / 0 behind and exactly 2 source files changed.

## Validation not executed

- Gradle compile;
- unit tests;
- lint;
- AndroidTest compilation;
- physical smartphone QA;
- physical Android TV / TV Box QA.

## Physical QA required before UX completion claims

Smartphone:

1. Open Library → Movies → Movie details and verify poster geometry/placeholder styling.
2. Verify Play/Resume, Favorite and Clear progress still work.
3. Verify Download/Offline presentation remains available and unchanged on mobile.
4. Open Library → Series → Series details and verify poster, metadata and About hierarchy.
5. Open seasons/episodes and verify playback/download/progress behavior remains unchanged.

Android TV / TV Box:

1. Open Movie details and verify no Download/Offline presentation is visible.
2. Open Series → season → episode and verify no Download/Offline presentation is visible.
3. Verify Back/focus hierarchy remains usable with the remote.
4. Verify Play/Resume still enters the existing playback pipeline.

## Status

Static/source checkpoint only. Do not treat skipped CI as a build/test pass and do not claim physical UX validation until smartphone and TV/TV Box QA are completed.
