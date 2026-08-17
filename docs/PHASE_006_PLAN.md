# Phase 006 — Playback

## Status

Planning only.

This document is prepared on `agent/phase-006-planning-no-ci` while the included GitHub Actions allowance is exhausted. No Phase 006 production code should be started or presented as validated until the existing Android validation gate is available again and the final Phase 005 continuation head has passed it.

No pull request is opened for this planning branch, so documentation commits do not intentionally trigger the current Android CI workflow.

## Authoritative scope

Phase 006 adds playback for user-supplied, authorized media sources. OwnPlay remains a media player and playlist organizer; it does not provide channels, subscriptions, playlists, or IPTV services.

The playback scope is:

- Media3 / ExoPlayer playback baseline
- live-channel playback
- clear recoverable playback errors
- audio-track selection
- subtitle-track selection
- fullscreen and orientation handling
- Picture-in-Picture where supported
- previous / next channel navigation
- fast return to the channel list
- preservation of playback while navigating where practical

Movies, series, and Continue Watching remain Phase 007. EPG remains Phase 008. Full source refresh/reconciliation remains Phase 009.

## Entry conditions

Before implementation starts:

1. The final Phase 005 head, including continuation hardening, must pass the existing JVM test, Android lint, debug assembly, androidTest compilation, Room schema generation/drift, and APK artifact gates.
2. The current stable Media3 version and its Android requirements must be verified from official AndroidX documentation before dependency pinning.
3. The existing source-security boundary must remain intact: raw credentials and sensitive stream locators must not be logged or exposed in browse/UI models.
4. The Live screen must have a clear runtime composition path from the app shell. The present stateless screen/repository boundary is acceptable architecture, but playback integration must not remain unreachable from `MainActivity`.

## Architectural boundaries

### Playback domain

Define small, explicit models for:

- playback request
- media kind
- playback state
- recoverable/non-recoverable failure
- selected audio/subtitle tracks
- previous/next channel intent

The domain layer must not depend on Compose.

### Stream resolution

Browse models continue to expose opaque identifiers/references rather than raw secrets.

A playback request should resolve the actual stream locator as late as possible, through a narrow boundary that can:

- verify the selected channel belongs to the active source
- retrieve/decrypt sensitive locator values where required
- construct provider-specific playback URLs where required
- avoid persisting derived credential-bearing URLs unless there is a concrete need
- redact sensitive values from errors and logs

### Media3 adapter

Media3 ownership should be isolated behind a focused controller/session boundary instead of spreading `ExoPlayer` calls through Composables.

Responsibilities include:

- player creation/release
- media-item preparation
- state observation
- retry
- track enumeration/selection
- lifecycle handling
- PiP-compatible playback ownership
- mapping Media3 failures into OwnPlay playback failures

Avoid a generalized media framework until VOD/series requirements prove it necessary.

### UI

Compose should render state and send intents/callbacks. It should not contain provider URL construction, secure-value lookup, or persistence logic.

## Error contract

OwnPlay must never leave the user with an endless spinner. Playback failures should resolve to one of the following user-facing categories where evidence allows:

- network unavailable
- timeout
- authentication failure
- stream unavailable
- unsupported media
- unknown playback failure

The mapping must be conservative. Do not report authentication failure merely because an arbitrary HTTP request failed.

Each failure should state whether retry is meaningful. Sensitive URLs, usernames, passwords, query tokens, headers, or opaque secure-store contents must never be rendered in error text or logs.

## Planned slices

### Slice A — Playback contracts and error mapping

Pure Kotlin where practical.

Deliverables:

- playback request/state models
- explicit failure taxonomy
- retryability contract
- deterministic Media3/HTTP/network error mapping helpers where they can be tested without an Android runtime
- redaction-focused unit tests

No player UI in this slice.

### Slice B — Media3 player controller

Deliverables:

- single focused Media3 controller/session boundary
- lifecycle-safe player creation and release
- prepare/play/pause/retry
- observable player state
- no endless loading state
- unit-testable mapping around player events where feasible

Pin Media3 only after official-version verification.

### Slice C — Secure live-stream resolution

Deliverables:

- resolve a selected provider channel to a playable request without leaking locator secrets
- Xtream-compatible URL construction only from securely retrieved source credentials and provider identifiers
- M3U stream-locator retrieval through the existing sensitive-value boundary
- source/channel ownership checks before playback
- explicit resolution failures

Do not place raw stream URLs in `LiveChannelItem`.

### Slice D — Reachable Live playback flow

Deliverables:

- wire the app shell/navigation so the Live screen is reachable
- channel selection opens/activates the player
- fast return to channel list
- current channel identity remains stable across the transition
- playback state survives normal Compose recomposition

Keep phone-first navigation minimal.

### Slice E — Player surface and controls

Deliverables:

- video surface
- play/pause where applicable
- loading indicator with bounded/error transition behavior
- retry action for retryable failures
- fullscreen control
- aspect-ratio control
- controls that hide when not needed

The visual treatment must remain dark, minimal, professional, and non-promotional.

### Slice F — Audio and subtitle tracks

Deliverables:

- enumerate available audio tracks
- enumerate available subtitle tracks
- select/default/off behavior where supported
- clear empty-state behavior when tracks are unavailable
- no assumptions that every stream has multiple tracks

### Slice G — Previous/next channel

Deliverables:

- deterministic previous/next resolution from the active browse/order context
- switching channels without rebuilding unrelated app state
- preserve user ordering semantics
- no mutation of persistent `My Order` from playback navigation

Filtered/search results must not silently redefine persistent order. The exact navigation context should be explicit in the playback request/session state.

### Slice H — Picture-in-Picture and orientation

Deliverables:

- Android PiP where supported
- sensible behavior when PiP is unavailable
- orientation/fullscreen transitions without leaking/recreating player ownership unnecessarily
- lifecycle validation around background/foreground transitions

### Slice I — Playback hardening

Deliverables:

- retry/backoff boundaries for recoverable failures
- network loss/recovery behavior
- unsupported stream behavior
- rapid channel-switch stress checks
- lifecycle and resource-release checks
- verification that logs contain no credentials or sensitive locator values

## Testing strategy

### JVM tests

Prefer deterministic JVM tests for:

- error mapping
- playback request construction that does not require Android secrets
- channel navigation ordering
- redaction
- state reducers

### Android compile/instrumentation boundary

At minimum, CI must continue to compile `androidTest` and the Media3 integration path. Runtime player behavior cannot be claimed from compilation alone.

### Device/emulator validation

Before Phase 006 is final, perform runtime validation on an Android device/emulator with authorized test media covering, where available:

- successful HLS playback
- live stream startup
- stream unavailable
- network unavailable
- timeout/retry
- unsupported media
- audio/subtitle selection
- fullscreen/orientation
- PiP
- previous/next channel
- return to list

Authentication-failure behavior should be tested only with controlled credentials/test endpoints; do not infer it from unrelated failures.

## CI gate after Actions capacity returns

Each implementation slice should pass the established gate before the next high-coupling slice proceeds:

- JVM unit tests
- Android lint
- debug APK assembly
- androidTest compilation
- Room schema generation/drift check
- debug APK artifact generation

Playback-specific tests should be added to the same gate rather than creating expensive parallel workflows without evidence that parallelization is needed.

## Security and privacy invariants

- Never log playlist passwords, Xtream credentials, bearer-like query parameters, or resolved sensitive stream URLs.
- Keep raw sensitive locators behind the secure-value boundary for as long as practical.
- Do not send credentials or playback metadata to an OwnPlay-operated server.
- Do not add analytics, ads, tracking SDKs, or cloud accounts as part of playback.
- Do not add provider/service branding that implies OwnPlay supplies media.

## Performance boundaries

- Reuse player/session ownership where appropriate instead of recreating it on every recomposition.
- Do not block the main thread with network or secure-store work.
- Avoid loading full catalogs into the playback layer merely to switch one channel.
- Keep previous/next resolution deterministic and cheap for large channel lists.
- Profile before introducing caching or pooling abstractions.

## Phase 006 completion criteria

Phase 006 is complete only when:

- a user can reach Live channels from the app shell and start an authorized stream
- Media3 playback works for supported test streams
- playback state and lifecycle ownership are explicit
- recoverable errors expose retry instead of endless loading
- required failure categories are represented conservatively
- audio/subtitle selection works where tracks exist
- fullscreen/orientation and supported PiP work
- previous/next channel navigation respects the active ordering context
- returning to the list is fast and predictable
- credentials and sensitive stream locators are absent from logs/UI errors
- the full Android CI gate passes
- runtime device/emulator evidence is recorded

No merge, release, deployment, or publication is authorized by this plan.
