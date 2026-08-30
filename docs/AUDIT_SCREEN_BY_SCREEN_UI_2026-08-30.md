# OwnPlay screen-by-screen UI audit — 2026-08-30

## Scope

Audit performed on top of:

- base branch: `agent/unified-modern-ui-live-epg-no-apk`
- base head: `ac2863b7795e91511e635ad5c570ff587ff3b11d`
- audit branch: `agent/tv-no-offline-ui-review-no-apk`

The review follows the current product direction:

- modern shared visual language for smartphone/touch and TV/D-pad
- primary active/selected navigation should be communicated primarily by color rather than geometry changes or added active labels
- Live Preview has no playback buttons
- Live fullscreen is EPG-first and has no generic playback menu
- Offline/download UX is not part of the TV experience
- mobile keeps its existing offline/download feature

No Media3/ExoPlayer engine, one-decoder policy, Room/schema, auth, sync, signing, release or deployment change is in scope.

## Home / primary navigation

### Confirmed

- The new dark palette and color-only TV focus indication are present in the current base.
- The Material3 selected navigation indicator is blended into the navigation surface, so selection is intended to read through tint rather than a visibly changing pill.

### Remaining finding H1 — Library icon still implies downloads

Primary navigation still uses `DownloadDone` for Library. With Offline removed from the TV product surface, this icon is semantically misleading and should be replaced with a neutral library/media icon in a later small visual pass.

No mutation was made here in this audit checkpoint because `OwnPlayApp.kt` is a broad routing file and exact-head compilation is unavailable in this session.

## Live

### Confirmed from current stacked base

- TV first OK on a channel opens Preview.
- Second OK on the same Preview channel opens Live fullscreen.
- Preview renders no playback/navigation/fullscreen/close buttons.
- Back/ESC closes Preview first on TV and non-TV.
- Live fullscreen has no generic Play/Pause/Next/Tracks/resize/diagnostics menu.
- EPG is the transient fullscreen interaction layer.
- TV fullscreen interaction is OK -> reveal EPG, Down -> enter timeline, Left/Right -> move through available programme slots, and Full EPG -> return to Preview -> open guide.

No additional Live mutation was needed in this screen-by-screen pass.

## Full EPG

### Confirmed

- Full EPG opens over the Preview presentation through the existing one-shot handoff.
- The guide scrolls near the current programme.

### Remaining finding E1 — TV focus starts on Done, not the current programme

`EpgGuideSheet` requests TV focus on the `Done` button. This is safe, but not the most direct D-pad experience for a guide opened specifically to browse programmes.

Recommended follow-up: focus the current programme row when available, falling back to the first programme and only then to Done. This should be implemented with an explicit focus policy/test rather than an ad-hoc requester.

## Library

### Confirmed

- The explicit `Offline` filter chip is now hidden on TV.
- TV state is forced back to `offlineOnly = false`.

### Remaining finding L1 — offline metadata/actions can still surface on TV

The Library still observes existing downloads and uses them to decorate catalog items. Therefore a TV with existing mobile-created downloads can still show offline badges, offline episode actions or local-file copy even though the Offline filter itself is gone.

Recommended follow-up: for TV presentation only, use an empty download presentation list while leaving the download repository/data untouched. This keeps Library online/catalog-first on TV and preserves mobile offline behavior.

### Remaining finding L2 — manual 2 dp focus border

`LibraryCatalogView` still adds its own 2 dp focus border. This conflicts with the new global TV focus language, which is color-only and geometry-stable.

Recommended follow-up: remove the manual border and rely on the global `TvRemoteIndication` color fill.

## Movies

### Remaining finding M1 — Download / Play Offline still visible on TV

`MovieDetailsPane` detects TV for focus behavior but still renders:

- Download / Pause / Resume / Retry download
- Downloaded / Offline copy messaging
- Play Offline / Resume Offline labels when a completed local copy exists

This conflicts with the requirement that Offline is not part of the TV experience.

Recommended follow-up: keep the underlying download state untouched, but suppress offline/download presentation in `MovieDetailsPane` when `isTelevision` is true. Play/Resume should remain normal online-facing labels on TV.

### Remaining finding M2 — selected primary rail changes font weight

`MovieNavigationRail` and movie category rows change font weight between selected and unselected states. The shape itself is stable, but this is inconsistent with the newer color-only selected-state direction.

Recommended follow-up: keep a constant font weight and use tint/background only.

## Series

### Remaining finding S1 — episode download/offline UI still visible on TV

`EpisodeRow` still renders Download, Resume DL, Play Offline, Downloaded copy, progress and failure copy on TV.

Recommended follow-up: suppress these controls/statuses on TV while retaining normal Play/Resume and all mobile offline behavior.

### Series catalog selection

The main selected-series row already primarily changes surface color; no equivalent delayed playing marker was found in this review.

## Settings

### Corrected in this audit branch

TV Settings no longer exposes the Downloads destination:

- `SettingsScreen` detects television UI mode and resets a stale `DOWNLOADS` destination back to `CONTENT`.
- `LandscapeSettingsRail` omits the Downloads / Offline movies & episodes item on TV.
- `LandscapeSettingsShell` resolves a TV `DOWNLOADS` state to `CONTENT` as a second defensive guard.
- selected Settings rail labels now keep a constant `FontWeight.Medium`; selected state is communicated by color.

Mobile/portrait Settings keeps Downloads unchanged.

## Validation boundary

This is a source-level audit plus a small Settings mutation.

Not executed on the exact audit head:

- Gradle compile
- unit tests
- lint
- AndroidTest compilation
- physical smartphone QA
- physical TV / TV Box QA

The audit branch ends in `-no-apk`; no APK is requested or authorized.

## Recommended next implementation slice

The highest-value next slice is a tightly scoped `TV Offline presentation removal` pass covering only:

1. Library presentation download metadata + manual focus border
2. Movie Details offline/download UI
3. Series episode offline/download UI
4. Library primary icon semantics
5. Full EPG initial TV focus policy

The implementation should remain presentation-only: do not delete downloads, change storage, alter the download engine, or migrate data.
