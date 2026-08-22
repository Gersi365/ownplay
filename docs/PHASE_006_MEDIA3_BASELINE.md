# Phase 006 — Media3 dependency baseline

**Date:** 2026-08-22  
**Status:** Planning evidence only; no playback dependency or production-code change is authorized by this document.  
**Branch:** `agent/phase-006-planning-reconciled-no-ci`  
**Phase 005 base:** `c57e4eae24d0857c5c5d2337e7c44fcd86eba249`

## Purpose

Record the verified AndroidX Media3 baseline required by the Phase 006 entry conditions before any playback dependency is pinned.

This document supports `docs/PHASE_006_PLAN.md`. It does not replace the authoritative product specification and does not relax the requirement that the reconciled Phase 005 head pass the established Android validation gate before Phase 006 production implementation starts.

## Verified upstream baseline

Official Android documentation re-checked on 2026-08-22:

- AndroidX Media3 stable release: **1.11.0**, released **2026-08-05**.
- official Media3/ExoPlayer getting-started documentation uses Media3 `1.11.0` and states that Media3 modules used together must use the same version.
- official supported-device guidance lists API 23 as the minimum for core video playback and HLS without DRM.
- official HLS documentation confirms ExoPlayer support for HLS with MPEG-TS and fMP4/CMAF containers, subject to device codec/sample-format support.
- Media3 requires at least Java 8 language compatibility in modules using ExoPlayer.

Official references:

- https://developer.android.com/jetpack/androidx/releases/media3
- https://developer.android.com/media/media3/exoplayer/hello-world
- https://developer.android.com/media/media3/exoplayer/supported-devices
- https://developer.android.com/media/media3/exoplayer/hls
- https://developer.android.com/develop/ui/views/picture-in-picture

## OwnPlay compatibility check

Current reconciled OwnPlay Android configuration:

- `minSdk = 26`
- `compileSdk = 36`
- `targetSdk = 36`
- Java source/target compatibility: 17
- Compose enabled

Result:

- Media3 1.11.0's documented API-level baseline is compatible with the current OwnPlay minimum SDK.
- HLS's documented API-level baseline is compatible with the current OwnPlay minimum SDK.
- OwnPlay's Java 17 configuration exceeds Media3's minimum Java 8 requirement.
- OwnPlay's `minSdk = 26` is compatible with phone Picture-in-Picture platform availability from Android 8.0/API 26, while runtime capability/lifecycle handling still requires implementation and device validation.
- this compatibility check does not prove a Media3 build because no Media3 dependency has been added and the Phase 005 Android validation gate remains closed.

## Candidate minimal dependency set

When the Phase 006 implementation gate opens, evaluate this minimal initial dependency set:

```kotlin
val media3Version = "1.11.0"
implementation("androidx.media3:media3-exoplayer:$media3Version")
implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
implementation("androidx.media3:media3-ui-compose-material3:$media3Version")
```

Constraints:

- keep every explicitly declared Media3 module on the same version;
- re-check the stable release immediately before the dependency-pinning commit;
- add only modules required by evidence from the Phase 006 scope;
- do not add DASH, RTSP, Cast, download, Transformer, ads, analytics, or unrelated modules by default;
- do not treat HLS container support as proof that every provider stream codec is playable on every device;
- do not introduce a generalized playback framework before VOD/series requirements demonstrate a concrete need.

## Existing OwnPlay boundary relevant to dependency adoption

Media3 should receive a resolved locator only after OwnPlay has:

1. identified the selected channel through its opaque local/source identity;
2. retrieved the versioned locator descriptor through the existing `SensitiveValueStore` reference;
3. resolved an Xtream descriptor with credentials from the existing secure credential-store boundary or returned a direct descriptor value;
4. completed redacted validation/failure mapping without exposing the locator to browse/UI state.

The Media3 controller should not become a credential store, provider client, Room repository, or locator-persistence layer.

## Phase 005 gate state

The reconciled Phase 005 continuation head on which this planning branch is based is:

`c57e4eae24d0857c5c5d2337e7c44fcd86eba249`

The source-only reconciliation commit is `169a02aa12c382d14675e24de522473db6da3bd9`, followed by the reconciliation evidence commit `c57e4eae24d0857c5c5d2337e7c44fcd86eba249`.

No full Android validation run has yet established that reconciled head as green. Therefore the Phase 006 implementation gate remains **closed**.

## Decision

Media3 **1.11.0** is the verified planning baseline as of 2026-08-22. No Media3 dependency, playback production code, database migration, merge, release, deployment, or publication is introduced by this document.
