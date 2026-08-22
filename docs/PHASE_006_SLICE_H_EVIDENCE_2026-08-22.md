# Phase 006 Slice H evidence — 2026-08-22

## Status

Slice H Picture-in-Picture and orientation/lifecycle handling is implemented on `agent/phase-006-pip-orientation-no-ci`, stacked directly on the exact Android-CI-validated Slice G head.

This document records source shape and local pre-CI validation only. No Slice H pull request has been opened and Android CI has not been triggered for Slice H.

## Validated base

- Slice G validated head: `c83542d1ec0e5d73899c891b343b0fd779bdce88`
- Slice G Android CI: run #72 / `32583382677` — success

## Source commits

1. `d7a42a2c0d23576ba3f614af3a5dd2b7b5f81a38` — `Implement Phase 006 PiP and orientation lifecycle`
2. `54cdade5ffd9ca597bebf1dd415d62d95c0c30ba` — `Track fullscreen changes from window insets`

The second commit is a focused one-file correction made before PR/CI. It replaces layout-bound assumptions for fullscreen detection with an actual window-insets callback, which is important for edge-to-edge layouts where hiding system bars need not change content bounds.

Remote compare against validated Slice G reports:

- 2 commits ahead / 0 behind;
- exactly five changed paths;
- `app/src/main/AndroidManifest.xml`: +3 / -1;
- `app/src/main/java/app/ownplay/player/MainActivity.kt`: +48 / -1;
- `app/src/main/java/app/ownplay/player/ui/PictureInPicturePlaybackSurface.kt`: +37 / -0;
- `app/src/main/java/app/ownplay/player/ui/PlaybackWindowController.kt`: +192 / -0;
- `app/src/test/java/app/ownplay/player/ui/PlaybackWindowPolicyTest.kt`: +90 / -0;
- no `OwnPlayApp.kt`, Gradle dependency, Room entity, DAO, schema, migration, locator-resolution, or personalization mutation.

The correction commit alone is exactly one file: `PlaybackWindowController.kt`, +12 / -2.

## Android API basis

Slice H uses platform Picture-in-Picture APIs already available at OwnPlay's minSdk 26 and does not add a dependency.

Official Android guidance was re-checked before implementation:

- the activity declares `android:supportsPictureInPicture="true"`;
- PiP-relevant configuration changes are handled by the activity to avoid activity recreation during PiP/layout transitions;
- Android 12+ uses `PictureInPictureParams.Builder.setAutoEnterEnabled(...)` for gesture-friendly automatic entry;
- automatic entry is enabled only while OwnPlay reports active `PlaybackState.Playing`;
- `setSeamlessResizeEnabled(true)` is used for the video surface;
- a current source-rectangle hint is provided from the activity content bounds;
- API 26–30 uses the legacy `onUserLeaveHint()` request path;
- PiP support is checked with `PackageManager.FEATURE_PICTURE_IN_PICTURE` and unsupported devices simply do not request PiP.

References:

- https://developer.android.com/develop/ui/views/picture-in-picture
- https://developer.android.com/reference/android/app/PictureInPictureParams.Builder
- https://developer.android.com/develop/ui/compose/layouts/adaptive/app-orientation-aspect-ratio-resizability

## Manifest/lifecycle boundary

`MainActivity` now declares:

- `android:supportsPictureInPicture="true"`;
- `android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"`.

Activity/runtime ownership remains stable:

- `OwnPlayAppRuntime` is still created once by the activity;
- no player/controller is created in PiP mode;
- `onUserLeaveHint`, `onPictureInPictureModeChanged`, and `onConfigurationChanged` delegate only to the narrow window controller;
- there is no automatic playback `pause`, `stop`, or Media3 release in background/PiP callbacks;
- runtime/player release still happens only from normal activity destruction.

The activity owns a small coroutine scope that observes only whether playback state is currently `Playing`; this state drives PiP eligibility and is cancelled during `onDestroy()`.

## PiP surface boundary

When the activity actually enters PiP, `MainActivity` temporarily composes `PictureInPicturePlaybackSurface` instead of the normal app navigation UI.

The PiP surface:

- is video-only and black-backed;
- creates no second `ExoPlayer`;
- binds a `PlayerView` to the existing `PlaybackVideoOutput` from the same Media3 engine;
- disables `PlayerView`'s built-in controller;
- unbinds the surface deterministically when the PiP composition is removed.

When PiP exits, the normal `OwnPlayApp` composition returns and the player surface rebinds to the same existing engine/session. `OwnPlayApp.kt` itself is byte-identical to the validated Slice G file (`433029a72ea1dcabe00be55c0ae8dd85cf77e3f0`).

## PiP eligibility and transitions

`PlaybackWindowPolicy.isPipEligible(...)` requires:

- device PiP feature support; and
- current playback state `Playing`.

Paused/loading/failed/idle playback does not enable Android 12+ auto-enter and manual legacy entry returns false.

For API 31+:

- PiP parameters are kept current;
- auto-enter follows Playing state;
- seamless resize is enabled.

For API 26–30:

- `onUserLeaveHint()` requests PiP only when eligible.

PiP mode changes are exposed through an activity-owned `StateFlow<Boolean>` so the composition can switch between normal app UI and the dedicated video-only surface without replacing playback ownership.

## Fullscreen/orientation behavior

The window controller observes actual system-bar insets on the activity content root. This makes fullscreen detection independent of whether edge-to-edge content bounds changed.

Orientation policy is deliberately narrow:

- fullscreen + not PiP + phone-size (`smallestScreenWidthDp` from 1 through 599) -> sensor landscape;
- PiP -> follow system;
- not fullscreen -> follow system;
- large-screen (`smallestScreenWidthDp >= 600`) -> follow system;
- unknown width (`0`) -> follow system.

This avoids forcing phone orientation semantics onto Android 16 large-screen/windowed environments. Because the activity handles orientation/screen-size configuration changes, these transitions do not intentionally recreate the activity, runtime, or player.

## Security/privacy

Slice H does not introduce or render:

- stream URLs;
- locator descriptors;
- `streamLocatorRef` values;
- usernames/passwords;
- authorization headers;
- bearer/query tokens;
- secure-store contents.

No provider URL construction or secure resolution moved into the activity/window/UI boundary.

## Local pre-CI validation

The final revised candidate passed:

- `PHASE_006_SLICE_H_POLICY_RECHECK=PASS`
- `PHASE_006_SLICE_H_MANIFEST_RECHECK=PASS`
- `PHASE_006_SLICE_H_LIFECYCLE_SECURITY_RECHECK=PASS`
- `PHASE_006_SLICE_H_PIP_SURFACE_RECHECK=PASS`
- `PHASE_006_SLICE_H_CONTROLLER_SYNTAX_RECHECK=PASS`
- `PHASE_006_SLICE_H_INSETS_CONTROLLER_RECHECK=PASS`
- `PHASE_006_SLICE_H_CONTROLLER_BEHAVIOR_RECHECK=PASS`
- `PHASE_006_SLICE_H_ACTIVITY_SYNTAX_RECHECK=PASS`
- `PHASE_006_SLICE_H_PIP_SURFACE_SYNTAX_RECHECK=PASS`
- `PHASE_006_SLICE_H_SECURITY_LIFECYCLE_RECHECK=PASS`
- `PHASE_006_SLICE_H_OWNPLAYAPP_UNCHANGED=PASS`

The controller behavior smoke covered:

- API 31+ Playing state enabling auto-enter and seamless resize;
- successful eligible manual PiP entry;
- paused state disabling auto-enter and rejecting manual entry;
- API 26–30 `onUserLeaveHint()` legacy entry;
- fullscreen phone orientation -> sensor landscape;
- entering PiP -> release forced orientation;
- exiting PiP while fullscreen -> reapply phone fullscreen policy;
- `sw600dp` fullscreen -> follow system;
- release -> follow system.

These are local Kotlin/stub-assisted checks. They are not substitutes for real Gradle/Android lint, APK assembly, androidTest compilation, or runtime device/emulator PiP validation.

## Git blob integrity

Final local/remote Git blob hashes:

- `AndroidManifest.xml`: `9dbde573e5df5ed446aaf9c0044bccd1a276ffdc`
- `MainActivity.kt`: `ecd6857189f6d58af105281935b74c2e2a96fde7`
- `PlaybackWindowController.kt`: `a8b64341f71dd846d6fc71aa166990561078978c`
- `PictureInPicturePlaybackSurface.kt`: `cf60c4ff67aca1f73afb01cb782a18e0e3086e82`
- `PlaybackWindowPolicyTest.kt`: `118b295105ea1ea6dbb51332cf96f7742450ea0f`

## Explicitly not included

- no Slice I hardening;
- no new dependency;
- no Media3 version change;
- no Room/schema/migration change;
- no player/navigation framework replacement;
- no persistent orientation/PiP preference;
- no custom PiP RemoteAction controls;
- no release/deployment/publication;
- no merge of existing Phase 006 PRs;
- no main mutation.

## Validation still required

Slice H is implemented but is not yet Android-CI validated and has not yet been proven on a physical device/emulator.

The final Phase 006 runtime evidence must still include real PiP/background/foreground/orientation behavior where supported, in addition to the later Slice I hardening scenarios.

## Required next gate

With separate explicit authorization, open exactly one draft Slice H pull request stacked on `agent/phase-006-previous-next-no-ci` and run exactly one established full Android CI gate on the exact current Slice H head. Do not start Slice I or merge any Phase 006 PR as part of that gate.
