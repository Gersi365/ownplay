# Final navigation and Library semantic cleanup audit — 2026-08-30

## Scope

Narrow final cross-stack remediation stacked on Draft PR #65.

Base exact head: `f5821aee1be3b36010092d618c37672b9d46a8d7`
Branch: `agent/final-nav-cleanup-no-apk`

## Findings remediated

### 1. Duplicate Movie primary navigation

The application-level authoritative primary navigation is `Live / Library / Settings`, with Movies and Series internal to Library.

`VodRoute` landscape/TV still contained a legacy rail exposing `Live / Movies / Series / Settings`, which could present a second and conflicting navigation model beside Movie catalog/details.

Remediation:
- replace the legacy `MovieNavigationRail` with `MovieCategoryRail`
- preserve real provider Movie-category browsing
- remove duplicate Live / Movies / Series / Settings destinations from the route-local rail
- keep the application-level primary navigation authoritative
- keep selected category presentation geometry stable and color-led

No Movie filtering, details, playback, progress, favorites or download behavior was changed.

### 2. Generic Library empty-state download semantics

The generic `No matching media` state used `DownloadDone`, including on TV where Offline presentation is intentionally hidden.

Remediation:
- Offline empty state retains `DownloadDone`
- non-Offline `No matching media` uses the existing neutral Search icon

No Library filtering, focus, routing, download data or storage behavior was changed.

## Static validation

- exact PR #65 head verified before branching
- final source re-read confirms Movie landscape/TV rail is category-only
- final source re-read confirms generic Library empty state is no longer download-shaped
- current compare before this evidence commit: 3 commits ahead / 0 behind
- source diff at that checkpoint: exactly two files
  - `app/src/main/java/app/ownplay/player/ui/vod/VodRoute.kt`: +14 / -55
  - `app/src/main/java/app/ownplay/player/ui/library/UnifiedLibraryRoute.kt`: +1 / -1
- an intermediate whole-file reconstruction accidentally duplicated a Library block; this was detected by diff review and removed in a forward-only correction. The current cumulative diff contains no duplicate block and only the intended +1/-1 Library change.

## Preserved product invariants

- primary navigation: Live / Library / Settings
- Movies and Series remain internal Library destinations
- mobile Library: Offline / Movies / Series
- TV Library: Movies / Series
- no visible aggregate All / All categories
- TV no Offline/Download presentation; mobile retains Offline/Download behavior
- Live Categories -> Channels -> Preview -> Fullscreen flow unchanged
- Preview/Fullscreen/Full EPG behavior unchanged
- Movie/Series playback requests and progress semantics unchanged

## Architecture boundary

No changes to Media3/ExoPlayer, decoder/surface ownership, playback engine, Room/schema, download engine/storage, source sync, auth, signing, versioning, release, deployment or publication.

## Validation not executed on this exact head

- Gradle compile
- unit tests
- lint
- AndroidTest compilation
- physical smartphone QA
- physical Android TV / TV Box QA

A skipped workflow is not a build/test PASS.

No APK is requested or authorized by this checkpoint.
