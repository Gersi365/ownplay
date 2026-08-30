# OwnPlay TV Offline presentation removal — 2026-08-30

## Scope

Presentation-only follow-up stacked on the screen-by-screen UI audit.

Base:
- branch: `agent/tv-no-offline-ui-review-no-apk`
- commit: `e1fb1eb3c6ed15679dc1ef4826b95ac5e0ff8cd4`

Implementation branch:
- `agent/tv-no-offline-presentation-no-apk`
- source checkpoint before this evidence commit: `df72015e19650b7e27c1aa555612a357e0a0262a`

Authoritative product rule for this slice:
- Offline/download presentation is not part of the TV experience.
- Mobile retains its existing offline/download feature.
- Existing downloads, storage, download runtime and persistence remain untouched.
- Selected/focus state should stay geometry-stable and communicate state primarily through color.

## Implemented

### Library — TV presentation no longer consumes download decoration data

`UnifiedLibraryRoute` still observes the existing download runtime, but uses a presentation-only list:

```kotlin
val presentationDownloads = if (isTelevision) emptyList() else downloads
```

All Library catalog decoration/grouping paths that can expose offline content now derive from `presentationDownloads`:
- movie download map
- offline-series grouping
- orphaned offline movies
- orphaned offline series

Consequences on TV:
- no Offline badge derived from an existing mobile-created movie download
- no Offline episode group/action derived from an existing mobile-created series download
- no orphaned local-only entries
- no local-file/download status decoration

The actual download repository/data is not deleted or mutated. Mobile continues to use the real `downloads` list.

The existing TV guard that forces `offlineOnly = false` remains in place, and the explicit Offline filter remains hidden on TV.

### Library — remove manual focus geometry

`LibraryCatalogView` no longer adds its own 2 dp border to the focused item. Focus restoration/requester behavior remains intact. TV now relies on the shared color-first remote indication instead of adding a second outline/geometry cue.

### Movies — hide offline/download presentation on TV

`MovieDetailsPane` now resolves completed offline presentation only outside TV:

```kotlin
val offlineCopyAvailable = !isTelevision && download?.state == DownloadStates.COMPLETED
```

The complete download/offline presentation block is gated by `!isTelevision`, including:
- Download
- Pause / Resume download
- Retry download
- Downloaded / Offline copy panel
- download progress
- saving-to-Downloads copy
- download failure copy

The primary Play action remains available on TV and uses normal online-facing labels:
- `Play`
- `Resume`

It cannot resolve to `Play Offline` / `Resume Offline` on TV because `offlineCopyAvailable` is false there.

Movie navigation rail and movie category selected labels now keep a constant `FontWeight.Medium`; selected state remains expressed by surface color rather than a weight/layout change.

### Series — hide offline/download presentation on TV

`EpisodeRow` now detects television UI mode and suppresses download/offline presentation there.

On TV the row keeps:
- Play / Resume
- viewing-progress semantics such as Resume available / Watched
- Clear viewing progress

On TV it no longer renders:
- Download
- Pause download
- Resume DL
- Retry download
- Play Offline / Resume Offline
- Downloaded · Offline copy
- download progress
- download failure copy

Mobile behavior remains unchanged.

### Primary Library navigation icon

The main OwnPlay navigation now uses `VideoLibrary` for Library instead of `DownloadDone` in both top and bottom navigation bars. This removes the semantic implication that Library is primarily an Offline/download destination.

Routing and section ownership are unchanged.

## Static audit results

Base-to-source-checkpoint comparison before adding this evidence file:
- ahead: 4 commits
- behind: 0 commits
- changed source files: 4

Files:
1. `app/src/main/java/app/ownplay/player/ui/library/UnifiedLibraryRoute.kt`
2. `app/src/main/java/app/ownplay/player/ui/vod/VodRoute.kt`
3. `app/src/main/java/app/ownplay/player/ui/series/SeriesRoute.kt`
4. `app/src/main/java/app/ownplay/player/ui/OwnPlayApp.kt`

Post-mutation source review confirmed:
- Library has no remaining manual `border` call/import.
- Movie offline labels remain in source only for the non-TV path.
- Series offline labels remain in source only for the non-TV path.
- Library primary navigation uses `VideoLibrary`.

## Architecture / reliability boundary

No change to:
- Media3 / ExoPlayer implementation
- player/decoder ownership
- Live Preview / Fullscreen handoff
- download engine
- download storage paths
- existing download records/files
- Room/schema/migrations
- auth/credentials/sync
- signing/application ID/versioning
- deployment/release/publication

No APK is requested or authorized.

## Validation boundary

Completed:
- source-level implementation
- base/head drift check
- base-to-head diff inspection
- targeted post-mutation static review of TV guards, focus-border removal and Library icon semantics

Not executed on the exact implementation head:
- Gradle compilation
- unit tests
- lint
- AndroidTest compilation
- physical smartphone QA
- physical Android TV / TV Box QA

The connected GitHub toolset available in this session does not expose the manual `workflow_dispatch` action required to start the repository's no-APK validation workflow. Standard Android CI for the `-no-apk` branch is expected to skip; that skip must not be interpreted as a build/test PASS.

## Physical QA checklist

### TV
- Settings has no Downloads destination (inherited from the stacked base).
- Library has no Offline filter, badges, local-only entries or offline actions.
- Library focus changes visually without adding a manual outline.
- Movie Details shows Play/Resume and Favorite, but no Download/Offline UI.
- Series episode rows show Play/Resume and viewing-progress controls, but no Download/Offline UI.
- Library navigation uses a media-library icon.

### Mobile
- Offline filter remains available in Library.
- Movie download/offline actions remain available.
- Series episode download/offline actions remain available.
- Existing downloads remain playable/manageable.

## Remaining screen-review item outside this slice

Full EPG on TV still initially focuses `Done` rather than the current programme. The next focused UX slice should make the current programme the initial D-pad target when available, fall back to the first programme, and use Done only as the final fallback.
