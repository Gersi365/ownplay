# Phase 006 Slice D evidence — 2026-08-22

## Status

Slice D reachable Live playback composition is implemented on `agent/phase-006-live-flow-no-ci`, stacked directly on the exact validated Slice C head.

This evidence records source shape and local pre-CI validation only. Android CI has not been triggered for Slice D yet.

## Validated base

- Slice C validated head: `5d4cb163d23efcffd7283a60609a14ddb47b354e`
- Slice C Android CI: run #67 / `32573639344` — success

## Implementation commit

`0145a1aa1cc4ab52422669ba35762e0e35e8cb29` — `Implement Phase 006 reachable Live playback flow`

Remote compare against validated Slice C reports:

- 1 commit ahead / 0 behind
- exactly five changed files
- `MainActivity.kt`: +8 / -56
- `OwnPlayAppRuntime.kt`: +44 / -0
- `LivePlaybackFlow.kt`: +50 / -0
- `OwnPlayApp.kt`: +416 / -0
- `LivePlaybackFlowTest.kt`: +105 / -0
- no `build.gradle.kts` change
- no Room entity/DAO/schema/migration change
- no Media3 dependency/version change

## Runtime composition

The static placeholder-only `MainActivity` is replaced by a narrow activity-owned composition root.

`OwnPlayAppRuntime` owns:

- the existing Room database instance;
- the existing Live catalog repository;
- the existing secure-value and credential stores;
- the existing Room playback-resolution lookup;
- the existing Live playback resolver;
- one Media3-backed `PlaybackController`.

The runtime is created once by `MainActivity` and closed from `onDestroy()`, so player/controller ownership does not depend on normal Compose recomposition.

## Minimal reachable flow

`OwnPlayApp` intentionally uses a small in-memory sealed route state rather than adding a navigation framework:

1. `Sources`
2. `Live(sourceId)`
3. `Playback(selection)`

The source screen observes existing `PlaylistSourceEntity` records. Enabled sources can open their Live catalog; the original “No playlists yet” messaging remains when no sources exist.

The Live route reuses `LiveCatalogRepository`, `LiveBrowseSession`, and the existing `LiveBrowseScreen` rather than replacing the Phase 004/005 browse UI.

## Opaque channel selection

`LivePlaybackFlow.kt` adds a narrow selection boundary:

- normal channel selection creates `LivePlaybackSelection` containing an existing opaque `PlaybackRequest` plus a display name;
- the request carries only source/channel identity and LIVE media kind;
- no raw stream URL, locator descriptor, credential, provider password, or resolved Media3 URI is added to the browse/UI model;
- selection rendering inherits the existing redacted `PlaybackRequest.toString()` behavior;
- blank display names fall back to `Live channel` without changing identity.

When a normal Live row is selected, the composition root:

1. resolves the selected `LiveChannelItem` from the current browse state;
2. converts it to the opaque playback selection;
3. calls the existing activity-owned controller with `start(selection.request)`;
4. moves the UI route to the playback status screen.

Secure descriptor lookup, credential retrieval, provider URL construction, and Media3 preparation remain behind the existing Slice B/C boundaries.

## Fast return to list and stable identity

The Playback route stores the selected `LivePlaybackSelection`, whose `PlaybackRequest` captures the source/channel identity at selection time.

Returning from Playback to Live changes only the UI route. It does not call `PlaybackController.stop()`. Therefore controller/player ownership can survive the fast return to the list, and a `Now playing` action can reopen the current playback status route while the controller remains active.

The source screen can also surface the active selection while playback state is non-idle.

## Personalization/edit separation

Channel selection behavior is explicitly routed by edit state:

- normal mode -> `StartPlayback`;
- edit mode -> `ToggleEditSelection` only.

This prevents an edit-mode row tap from accidentally starting playback.

The composition root wires the existing local edit-state reducer for enter/exit, toggle, select-visible, clear-selection, and retain-available behavior.

Slice D intentionally does not expand into broad Phase 005 personalization mutation composition. Existing bulk/customization mutation callbacks that already have defaults on `LiveBrowseScreen` are not replaced by a new framework here. This keeps the playback-selection path independent and avoids conflating runtime reachability with a broader personalization/application architecture change.

## Player-surface boundary

Slice D does not add the video surface or full playback controls. The reachable Playback route is a state/status surface that proves composition and controller activation while leaving Media3 video surface, retry UI, fullscreen, and aspect controls to Slice E.

## Local validation

Available pre-CI checks:

1. Pure `LivePlaybackFlow` production/test syntax compile against narrow signature-compatible stubs: `PHASE_006_SLICE_D_FLOW_COMPILE=PASS`.
2. Pure flow behavior smoke covering normal selection, stable opaque identity, redacted rendering, and edit-mode separation: `PHASE_006_SLICE_D_SMOKE=PASS`.
3. `OwnPlayAppRuntime.kt` syntax compile against narrow Android/domain stubs: `PHASE_006_SLICE_D_RUNTIME_COMPILE=PASS`.
4. `OwnPlayApp.kt` + `MainActivity.kt` syntax compile against signature-compatible Activity/Compose/Flow/domain stubs, including RowScope/ColumnScope weight behavior: `PHASE_006_SLICE_D_APP_SHELL_COMPILE=PASS`.
5. Sensitive-string scan across the new runtime/UI production files for raw URL/credential/locator field indicators: `PHASE_006_SLICE_D_SENSITIVE_SCAN=PASS`.
6. Remote compare confirms only the five intended implementation/test files changed in the source commit.
7. No pull-request-triggered workflow run is associated with implementation commit `0145a1aa...`.

These checks are not substitutes for Gradle dependency resolution, Android lint, JVM tests, APK assembly, androidTest compilation, Room schema verification, or runtime device/emulator playback validation.

## Not performed

- no Slice D pull request opened;
- no Slice D Android CI triggered;
- no merge of PR #7, #8, or #9;
- no main mutation;
- no Navigation dependency/framework;
- no Media3 UI module;
- no video surface/full player controls;
- no Room schema or migration change;
- no release, deployment, or publication.

## Required next gate

Before Slice E, open one draft Slice D pull request stacked on `agent/phase-006-media3-controller-no-ci` and run the existing full Android CI gate on the exact Slice D head.
