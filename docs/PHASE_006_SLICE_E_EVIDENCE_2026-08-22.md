# Phase 006 Slice E evidence — 2026-08-22

## Status

Slice E player surface and focused controls are implemented on `agent/phase-006-player-surface-no-ci`, stacked directly on the exact validated Slice D head.

This document records source shape and local pre-CI validation only. Android CI has not been triggered for Slice E.

## Validated base

- Slice D validated head: `e4a0b18ba5059360e218f198327489a5edd32275`
- Slice D Android CI: run #68 / `32575422038` — success

## Implementation commit

`320b741409a27bfdb436b9339276d48f0ec9cdcf` — `Implement Phase 006 player surface and controls`

Remote compare against validated Slice D reports:

- 1 commit ahead / 0 behind;
- exactly six changed files;
- `app/build.gradle.kts`: +1 / -0;
- `OwnPlayAppRuntime.kt`: +4 / -1;
- `Media3PlaybackEngine.kt`: +44 / -5;
- `PlaybackPresentation.kt`: +55 / -0;
- `OwnPlayApp.kt`: +272 / -19;
- `PlaybackPresentationTest.kt`: +56 / -0;
- no Room entity, DAO, schema, or migration change.

## Media3 UI dependency

The existing Media3 version remains `1.11.0`. Slice E adds exactly one module:

- `androidx.media3:media3-ui:1.11.0`

No `media3-ui-compose`, `media3-ui-compose-material3`, DASH, RTSP, Cast, download, Transformer, ads, or analytics dependency is added.

Official Android documentation checked immediately before implementation confirms the Views UI module and `PlayerView` surface/resize behavior for the current Media3 line:

- <https://developer.android.com/media/media3/ui/overview>
- <https://developer.android.com/reference/androidx/media3/ui/PlayerView>
- <https://developer.android.com/reference/kotlin/androidx/compose/ui/viewinterop/package-summary>

## Video-output ownership boundary

`Media3PlaybackEngine` remains the sole owner of the underlying `ExoPlayer`.

Slice E introduces `PlaybackVideoOutput`, which exposes only:

- `bind(PlayerView)`;
- `unbind(PlayerView)`.

The Compose layer never receives the raw `Player`/`ExoPlayer`. The engine:

- attaches the current `PlayerView` on the player application looper;
- disables the built-in `PlayerView` controller because OwnPlay renders the focused Compose controls;
- detaches any previously bound view before replacing it;
- detaches the view during `unbind` and before player release.

`Media3PlaybackControllerFactory` now returns one component bundle containing the existing controller and the same engine's video-output boundary, preserving single-player ownership.

## Player surface and controls

The existing Slice D playback status route is upgraded to a phone-first player surface using `PlayerView` through Compose `AndroidView`.

Implemented controls/state behavior:

- black video surface;
- bounded loading indicator driven by existing `PlaybackState.Loading`;
- the existing controller's 30-second loading timeout remains authoritative, so the UI does not create an independent endless spinner timer;
- deterministic failure label without exception bodies or sensitive locators;
- Retry appears only when the existing `PlaybackFailure.retryable` contract permits it;
- Play appears for paused state;
- Pause appears for playing state;
- controls auto-hide after 3 seconds while playing and can be toggled by tapping the player;
- controls remain visible for non-playing/error/loading states;
- fast `Channels` return remains available and does not implicitly stop playback.

## Aspect ratio

Slice E adds a small presentation-only resize state:

1. `FIT`
2. `FILL`
3. `ZOOM`
4. back to `FIT`

These map directly to Media3 `AspectRatioFrameLayout` resize modes. The resize preference is UI session state only; no persistence or database change is introduced.

## Fullscreen

Fullscreen in Slice E is deliberately narrow:

- hides Android system bars;
- allows transient bars by swipe;
- restores system bars when fullscreen exits or the effect leaves composition;
- Back exits fullscreen first, then returns to the channel list.

Slice E does not lock or force device orientation. Broader orientation lifecycle behavior remains the later Phase 006 orientation/PiP slice.

## Presentation policy

`PlaybackPresentationPolicy` isolates deterministic control visibility from the Composable:

- Idle: no playback action;
- Loading: loading indicator only;
- Playing: Pause;
- Paused: Play;
- Failed: Retry only when the failure is retryable.

`PlaybackPresentationTest` covers resize cycling, loading/play/pause policy, and retryable versus non-retryable failure behavior.

## Security/privacy boundary

- UI source does not receive or render resolved stream URLs.
- No credentials, locator descriptors, usernames, passwords, query tokens, or secure-store values are added to presentation state.
- Provider URL construction and secure lookup remain behind the validated Slice B resolver.
- Media preparation remains behind the validated Slice C controller/engine boundary.
- The new video-output boundary exposes only a `PlayerView` bind/unbind capability.

## Local pre-CI validation

Available local checks passed:

- `PHASE_006_SLICE_E_PRESENTATION_COMPILE=PASS`
- `PHASE_006_SLICE_E_MEDIA3_ADAPTER_COMPILE=PASS`
- `PHASE_006_SLICE_E_RUNTIME_COMPILE=PASS`
- `PHASE_006_SLICE_E_UI_SYNTAX_COMPILE=PASS`
- `PHASE_006_SLICE_E_SMOKE=PASS`
- `PHASE_006_SLICE_E_MEDIA3_SCOPE=PASS`
- `PHASE_006_SLICE_E_UI_SECURITY_SCAN=PASS`

A UI syntax issue (`Box` without a content lambda) was detected and corrected locally before any source commit was created.

All six committed candidate Git blobs were verified byte-for-byte against their local Git blob SHA before the implementation tree was committed. Two earlier unattached scratch blobs that failed this integrity check were excluded from every tree and commit.

These local checks are not substitutes for Gradle dependency resolution, Android lint, JVM tests, APK assembly, androidTest compilation, Room schema verification, or runtime device/emulator playback validation.

## Not performed

- no Slice E pull request opened;
- no Slice E Android CI triggered;
- no merge of PR #7, #8, #9, or #10;
- no `main` mutation;
- no audio/subtitle track UI;
- no previous/next channel control;
- no Picture-in-Picture;
- no orientation locking;
- no Room schema/migration change;
- no release, deployment, or publication.

## Required next gate

Before Slice F, open one draft Slice E pull request stacked on `agent/phase-006-live-flow-no-ci` and run the established full Android CI gate on the exact Slice E head.