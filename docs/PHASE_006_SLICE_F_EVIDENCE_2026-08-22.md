# Phase 006 Slice F evidence — 2026-08-22

## Status

Slice F audio/subtitle track enumeration and selection is implemented on `agent/phase-006-track-selection-no-ci`, stacked directly on the exact Android-CI-validated Slice E head.

This document records source shape and local pre-CI validation only. No Slice F pull request has been opened and Android CI has not been triggered for Slice F.

## Validated base

- Slice E validated head: `d7f7d3368c7abf476b619389c184510c37ac6839`
- Slice E Android CI: run #70 / `32578042288` — success

## Implementation commit

`17b6b57ad47dfe739ad308cbbabf9b1857e7e9a6` — `Implement Phase 006 audio and subtitle tracks`

Remote compare against validated Slice E reports:

- 1 commit ahead / 0 behind;
- exactly five changed files;
- `OwnPlayAppRuntime.kt`: +2 / -0;
- `Media3PlaybackEngine.kt`: +209 / -1;
- `PlaybackTracks.kt`: +217 / -0;
- `OwnPlayApp.kt`: +189 / -8;
- `PlaybackTracksTest.kt`: +159 / -0;
- no Gradle dependency file, Room entity, DAO, schema, or migration change.

All five candidate Git blobs were verified byte-for-byte against their local Git object hashes before the implementation tree was created.

## Media3 API basis

The current Media3 baseline remains `1.11.0`; Slice F adds no dependency.

Official Android Media3 documentation was re-checked immediately before implementation. The implementation uses the existing current-track and track-selection APIs:

- `Player.currentTracks` / `Player.Listener.onTracksChanged`;
- `Tracks.Group` for runtime track type, support, selection state and `Format` metadata;
- `TrackSelectionOverride` for a specific group/index selection;
- `clearOverridesOfType` and `setOverrideForType` on track-selection parameters;
- `setTrackTypeDisabled` for subtitle Off behavior.

References:

- <https://developer.android.com/media/media3/exoplayer/track-selection>
- <https://developer.android.com/reference/androidx/media3/common/Tracks.Group>
- <https://developer.android.com/reference/androidx/media3/common/TrackSelectionOverride>
- <https://developer.android.com/reference/androidx/media3/common/TrackSelectionParameters.Builder>

## OwnPlay track boundary

`PlaybackTracks.kt` adds a Media3-independent presentation/session contract:

- `PlaybackTrackKind`: AUDIO / SUBTITLE;
- `PlaybackTrackOption`: ephemeral opaque ID, kind, human-readable label/language, player-selected state and support state;
- `PlaybackAudioSelection`: Default / Specific;
- `PlaybackSubtitleSelection`: Default / Off / Specific;
- `PlaybackTrackState`;
- focused `PlaybackTrackController`;
- deterministic selection policy and label sanitizer.

Track option IDs are ephemeral current-stream handles. Their `toString` rendering is redacted and they are not provider/channel/locator identifiers.

## Media3 adapter behavior

The existing `Media3PlaybackEngine` remains the sole owner of the single `ExoPlayer` instance and now also implements `PlaybackTrackController`.

The engine:

- observes `onTracksChanged`;
- projects only audio/text groups into OwnPlay models;
- keeps Media3 `TrackGroup`/index handles private to the engine;
- never exposes `Player`, `Tracks.Group`, `TrackGroup`, or `Format` objects to Compose;
- applies specific selections only to current known supported ephemeral handles;
- treats stale/unknown IDs as a safe no-op;
- clears type overrides and enables the type for Default;
- disables the text track type for subtitle Off;
- enables text and applies a specific override for subtitle Specific;
- resets audio/text overrides and re-enables both types before every new media prepare;
- clears ephemeral handles/state on prepare, stop, and release.

The prepare-time reset is deliberate: Media3 track-type disabled state can otherwise persist across media. A subtitle-Off or explicit override from one channel must not leak into a later channel.

## Labels and privacy

Track labels are built only from sanitized Media3 `Format.label` / `Format.language` metadata with deterministic fallbacks such as `Audio 1` and `Subtitle 1`.

The formatter rejects sensitive-looking metadata containing URL/credential markers such as `://`, password/token/username/authorization fields, or bearer-like text. Internal ephemeral IDs are never rendered in the UI.

No stream URLs, locator descriptors, credentials, secure-store contents, headers, query tokens, or provider secrets are introduced into track state.

## Runtime composition and UI

`OwnPlayAppRuntime` exposes the same engine's focused `PlaybackTrackController`; no second player/controller is created.

The existing player surface adds one focused `Tracks` action available while Playing/Paused. The in-player panel provides:

- Audio: Default + supported current audio options;
- Subtitles: Default + Off + supported current subtitle options;
- clear empty labels: `No alternate audio tracks` and `No subtitles`;
- player-active marker for the track reported selected by Media3;
- unsupported options are disabled;
- Back closes the tracks panel before exiting fullscreen or returning to channels;
- controls do not auto-hide while the tracks panel is open.

No settings screen, global language preference, track persistence, or Room state is added.

## Local pre-CI validation

Available local checks passed:

- `PHASE_006_SLICE_F_TRACK_MODEL_COMPILE=PASS`
- `PHASE_006_SLICE_F_TRACK_MODEL_SMOKE=PASS`
- `PHASE_006_SLICE_F_MEDIA3_ADAPTER_COMPILE=PASS`
- `PHASE_006_SLICE_F_RUNTIME_COMPILE=PASS`
- `PHASE_006_SLICE_F_UI_SYNTAX_COMPILE=PASS`
- `PHASE_006_SLICE_F_MEDIA3_BEHAVIOR_SMOKE=PASS`
- `PHASE_006_SLICE_F_UI_SECURITY_SCAN=PASS`
- `PHASE_006_SLICE_F_DEPENDENCY_SCOPE=PASS`

The stronger Media3 behavior smoke covered:

- audio/text enumeration;
- current player-selection projection;
- specific audio override;
- stale-ID rejection;
- subtitle Off;
- new-media prepare resetting overrides/type-disable state and returning session selections to Default.

These checks use local Kotlin plus narrow Android/Media3 API-shape stubs where required. They are not substitutes for real Gradle dependency resolution, Android lint, JVM tests, debug APK assembly, androidTest compilation, Room schema verification, or runtime device playback.

## Explicitly not included

- no previous/next channel navigation;
- no PiP/orientation lifecycle work;
- no persistent track preference or global language setting;
- no VOD/series work;
- no new dependency;
- no Room/schema/migration change;
- no broad player redesign;
- no analytics, ads, or tracking;
- no merge, release, deployment, or publication.

## Required next gate

Before Slice G, open one draft Slice F pull request stacked on `agent/phase-006-player-surface-no-ci` and run exactly one established full Android CI gate on the exact current Slice F head. A separate explicit authorization is required before that PR/Actions step.
