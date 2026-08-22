# Phase 006 Slice C evidence — 2026-08-22

## Status

Slice C source is implemented on `agent/phase-006-media3-controller-no-ci` on top of the exact validated Slice B head. Android CI has **not** yet been triggered for this branch.

## Validated base

- Slice B validated head: `f2a302ab32c70ee9955dfc7aad1f0a2d689b734b`
- Slice B Android CI: run #65 / `32571925358` — success

## Upstream Media3 verification

Official Android documentation was re-checked immediately before the dependency-pinning commit.

- AndroidX Media3 stable: **1.11.0**, released 2026-08-05.
- official ExoPlayer getting-started documentation uses 1.11.0 and requires Media3 modules used together to share the same version.
- official HLS documentation uses `media3-exoplayer-hls:1.11.0` for HLS playback.
- OwnPlay remains on `minSdk = 26` and Java 17, which exceeds the documented ExoPlayer Java 8 requirement.

Official references:

- https://developer.android.com/jetpack/androidx/releases/media3
- https://developer.android.com/media/media3/exoplayer/hello-world
- https://developer.android.com/media/media3/exoplayer/hls
- https://developer.android.com/reference/androidx/media3/common/PlaybackException
- https://developer.android.com/reference/androidx/media3/datasource/HttpDataSource.InvalidResponseCodeException

## Implementation commit

`791406b2ebc6165ae6417b0de3df89f87328a6ae` — `Implement Phase 006 Media3 controller boundary`

Remote compare against the validated Slice B head reports:

- 1 commit ahead / 0 behind
- `app/build.gradle.kts`: +4 / -0
- `Media3PlaybackEngine.kt`: +165 / -0
- `PlaybackController.kt`: +254 / -0
- `Media3PlaybackFailureMapperTest.kt`: +73 / -0
- `PlaybackControllerTest.kt`: +220 / -0

## Dependency decision

Pinned same-version Media3 modules:

```kotlin
val media3Version = "1.11.0"
implementation("androidx.media3:media3-exoplayer:$media3Version")
implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
```

Not added:

- no Media3 UI module yet
- no DASH
- no RTSP
- no Cast
- no download
- no Transformer
- no ads
- no analytics

The Compose/player surface belongs to later playback slices and is intentionally not part of this controller-only dependency pin.

## Controller boundary

`PlaybackController` owns the narrow orchestration boundary between secure locator resolution and the playback engine:

- starts from `PlaybackRequest`, never a raw URL;
- resolves the locator on the injected IO dispatcher;
- passes the resolved locator only to the engine boundary;
- keeps raw locator values out of `PlaybackState`;
- supports start, play, pause, retry, stop, and release;
- cancels stale resolution jobs when a new request starts;
- bounds the loading state with a 30-second default timeout;
- converts timeout to the existing `PlaybackFailureCategory.TIMEOUT` contract;
- clears the engine on stop/restart/release;
- exposes state through `StateFlow<PlaybackState>`.

The controller does not own Room, credentials, provider URL construction, Compose UI, navigation, tracks, PiP, analytics, or persistence.

## Media3 engine boundary

`Media3PlaybackEngine` owns one `ExoPlayer` instance and centralizes:

- MediaItem preparation;
- play/pause;
- stop and media-item clearing;
- release;
- READY/ENDED observation;
- player-error mapping.

Player operations are marshalled to `player.applicationLooper` when needed. Resolved locators are not logged or placed in OwnPlay state models.

## Failure mapping

Media3 error-code mapping is conservative:

- network connection failure → `NETWORK_UNAVAILABLE`
- network/generic timeout → `TIMEOUT`
- explicit HTTP 401/403 evidence → `AUTHENTICATION_FAILURE`
- other bad HTTP status / malformed media / IO stream errors → `STREAM_UNAVAILABLE`
- unsupported manifest/container/decoder capability → `UNSUPPORTED_MEDIA`
- unclassified errors → `UNKNOWN`

Authentication is **not** inferred from missing local credentials or a generic bad HTTP status. The mapper returns only the existing OwnPlay failure category and never propagates Media3 exception messages or response bodies into playback state.

## Local validation

Available runtime validation before the remote commit:

1. `PlaybackController.kt` compiled with `kotlinc` using the installed real `kotlinx-coroutines-core` runtime plus narrow domain stubs: PASS.
2. `Media3PlaybackEngine.kt` syntax-compiled with the controller against narrow Android/Media3 API-shape stubs based on the verified official APIs: PASS.
3. Both new JVM test files syntax-compiled with the production files against temporary minimal JUnit/Android/Media3 stubs: PASS.
4. Dependency scope check: exactly two `androidx.media3` declarations; no UI/DASH/Cast/Transformer modules: PASS.
5. Dependency-free controller behavior smoke covered resolve → prepare → ready, redacted state, network failure → retry, release, and bounded loading timeout: `PHASE_006_SLICE_C_SMOKE=PASS`.
6. Remote compare confirms only the five intended implementation/test paths changed in the source commit.
7. No pull-request-triggered workflow run is associated with implementation commit `791406b2...`.

The stub-assisted compilation checks verify Kotlin/API shape only. They are **not** a substitute for Gradle dependency resolution, Android lint, real JVM tests, APK assembly, androidTest compilation, or Room schema verification.

## Required next gate

Before Slice D reachable Live playback integration, open one draft Slice C PR stacked on `agent/phase-006-secure-resolution-no-ci` and run the established Android CI gate on the exact Slice C head:

- JVM unit tests
- Android lint
- debug APK assembly
- `compileDebugAndroidTestKotlin`
- Room schema generation/drift verification
- debug APK artifact generation

No merge, release, deployment, publication, broad navigation change, or player UI work is authorized by this evidence document.
