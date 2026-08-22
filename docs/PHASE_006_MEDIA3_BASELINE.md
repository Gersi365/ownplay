# Phase 006 — Media3 dependency baseline

**Date:** 2026-08-22  
**Status:** Planning evidence only; no playback dependency or production-code change is authorized by this document.  
**Branch:** `agent/phase-006-planning-no-ci`

## Purpose

Record the verified AndroidX Media3 baseline required by the Phase 006 entry conditions before any playback dependency is pinned.

This document supports `docs/PHASE_006_PLAN.md`. It does not replace the authoritative Phase 006 scope or relax the requirement that the final Phase 005 continuation head pass the established Android validation gate before Phase 006 production implementation starts.

## Verified upstream baseline

Official Android documentation checked on 2026-08-22:

- AndroidX Media3 stable release: **1.11.0**, released **2026-08-05**.
- The official ExoPlayer getting-started documentation uses Media3 `1.11.0` and requires all Media3 modules used by an application to stay on the same version.
- Media3 raised its library `minSdk` to **23** in version 1.9.0.
- Official supported-device guidance lists API 23 as the minimum for core video playback and HLS without DRM.
- Picture-in-Picture for phone activities is available from Android 8.0 / API 26.

Official references:

- https://developer.android.com/jetpack/androidx/releases/media3
- https://developer.android.com/media/media3/exoplayer/hello-world
- https://developer.android.com/media/media3/exoplayer/supported-devices
- https://developer.android.com/develop/ui/views/picture-in-picture

## OwnPlay compatibility check

Current OwnPlay Android configuration on this planning branch:

- `minSdk = 26`
- `compileSdk = 36`
- `targetSdk = 36`
- Java source/target compatibility: 17

Result:

- Media3 1.11.0's API-level baseline is compatible with the current OwnPlay minimum SDK.
- HLS playback's documented API-level baseline is compatible with the current OwnPlay minimum SDK.
- OwnPlay's Java 17 configuration exceeds Media3's documented requirement to enable at least Java 8 support.
- OwnPlay's `minSdk = 26` aligns with the platform introduction of phone Picture-in-Picture. Runtime/device capability handling is still required; this is not evidence that PiP behavior is implemented or validated.

## Candidate Phase 006 dependency set

When the Phase 006 implementation gate opens, the minimal initial Media3 dependency set should be evaluated as:

```kotlin
val media3Version = "1.11.0"
implementation("androidx.media3:media3-exoplayer:$media3Version")
implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
implementation("androidx.media3:media3-ui-compose-material3:$media3Version")
```

Constraints:

- Keep every explicitly declared Media3 module on the same version.
- Do not add these dependencies before the Phase 005 validation gate is green.
- Do not add DASH, Cast, download, Transformer, or other Media3 modules without a concrete Phase 006 requirement.
- Do not introduce a generalized playback framework around Media3 before VOD/series evidence requires it.
- Re-check the official stable Media3 release immediately before the first dependency-pinning commit if implementation begins on a later date.

## Phase 005 gate state observed during this audit

The Phase 005 continuation branch head observed from the repository comparison is:

`134d79b4c20e9e8b2597aedd1662ad2eca6ba718`

No pull-request-triggered workflow run is currently associated with that continuation head. The earlier historical Phase 005 gesture head `b7d237025b4218fde93abbf2da172624f40755f7` has Android CI run `32018080374` / run number 57 recorded as failed; existing project evidence states that runner execution was blocked by the exhausted Actions allowance rather than by a demonstrated source failure.

Therefore the Phase 006 implementation gate remains **closed**.

## Decision

Media3 **1.11.0** is the verified dependency baseline as of 2026-08-22, but no dependency pin or playback production code is introduced while the Phase 005 Android validation gate remains unavailable.
