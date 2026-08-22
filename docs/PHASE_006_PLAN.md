# Phase 006 — Playback

## Status

Planning only.

This reconciled planning document is prepared on `agent/phase-006-planning-reconciled-no-ci`, based directly on the reconciled Phase 005 continuation head `c57e4eae24d0857c5c5d2337e7c44fcd86eba249`.

No Phase 006 production code should be started or presented as validated until the final Phase 005 continuation head has passed the established Android validation gate. No pull request is opened for this planning branch, so documentation-only pushes do not intentionally trigger the current Android CI workflow, whose push trigger is limited to `main`.

## Reconciled entry baseline — 2026-08-22

The repository was re-audited after the Drive checkpoint reconciliation.

Confirmed current state:

- Phase 005 source/test/script reconciliation is present on GitHub and evidence is recorded in `docs/PHASE_005_RECONCILIATION_2026-08-22.md`.
- `MainActivity` still renders the initial OwnPlay shell and does not yet compose the Live browse screen. Reachable Live/app-shell integration therefore remains a real Phase 006 requirement.
- Live browse models expose channel identity and personalization state without exposing raw stream locator values.
- initial Live ingestion already converts M3U stream URLs and Xtream live IDs into a versioned `PlaybackLocatorDescriptor` representation.
- locator descriptor values are persisted behind `SensitiveValueStore`; Room stores opaque `streamLocatorRef` values rather than raw stream locators.
- Xtream credential storage already exists behind the Android Keystore-backed credential-store boundary.
- no Media3 dependency is currently pinned.
- the Phase 005 full Android gate remains outstanding because the included Actions capacity is unavailable.

The planning work must reuse these existing boundaries instead of duplicating them.

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

Movies, series, and Continue Watching remain Phase 007. EPG remains Phase 008. Full provider refresh/removal lifecycle remains Phase 009.

## Entry conditions

Before implementation starts:

1. The reconciled Phase 005 head must pass the existing JVM test, Android lint, debug assembly, androidTest compilation, Room schema generation/drift, and APK artifact gates.
2. The stable Media3 version and Android requirements must be re-checked immediately before the first dependency-pinning commit. The verified planning baseline as of 2026-08-22 is recorded in `docs/PHASE_006_MEDIA3_BASELINE.md`.
3. The existing security boundary must remain intact: raw credentials and sensitive stream locators must not be added to browse/UI models, logs, audit files, or Room fields that currently hold only opaque references.
4. The Live screen must become reachable from the app shell through an explicit runtime composition path.
5. No architecture replacement, database migration, broad refactor, dependency churn, merge, release, deployment, or publication is implied by this plan.

## Existing boundaries to reuse

### Live channel identity

The reconciled Live read path already carries stable local `channelId`, `sourceId`, provider identifiers, ordering context, favorites, hidden state, local naming, and logo-reference state.

Playback must start from channel identity. It must not require adding raw stream URLs to `LiveChannelItem`.

### Locator descriptor and secure storage

`InitialLiveCatalogFactory` already produces versioned locator descriptors:

- `ownplay-locator-v1|direct|...` for direct M3U/direct-source URLs
- `ownplay-locator-v1|xtream-live|<streamId>` for Xtream live streams without a direct source

`InitialLiveCatalogIngestor` stores these descriptors through `SensitiveValueStore` and persists only the resulting opaque `streamLocatorRef` in Room.

Phase 006 should add a narrow resolver that:

1. verifies that the requested channel belongs to the active source;
2. loads the channel's opaque `streamLocatorRef` from persistence;
3. retrieves the descriptor from `SensitiveValueStore`;
4. parses only the supported version/kinds conservatively;
5. for `direct`, returns the contained locator to the player boundary without logging it;
6. for `xtream-live`, retrieves the source credentials through the existing credential-store boundary and constructs the provider URL as late as possible;
7. returns an explicit redacted failure when the descriptor, source, channel, secure value, or credentials cannot be resolved.

Do not introduce a second locator format unless implementation evidence requires a versioned migration path.

### Media3 ownership

Media3 ownership should be isolated behind one focused controller/session boundary rather than spreading `ExoPlayer` calls through Composables.

Responsibilities include:

- player creation/release
- media-item preparation
- state observation
- play/pause/retry
- track enumeration/selection
- lifecycle handling
- PiP-compatible player ownership
- mapping Media3 failures into OwnPlay playback failures

Avoid a generalized media framework until VOD/series requirements prove it necessary.

### UI

Compose should render playback state and send intents/callbacks. It must not contain provider URL construction, secure-value lookup, credential retrieval, or persistence logic.

## Error contract

OwnPlay must never leave the user with an endless spinner. Playback failures should resolve conservatively to one of:

- network unavailable
- timeout
- authentication failure
- stream unavailable
- unsupported media
- unknown playback failure

Each failure must state whether retry is meaningful. Authentication failure must only be reported where HTTP/provider evidence supports it.

Sensitive URLs, usernames, passwords, query tokens, headers, locator descriptors, secure-store contents, and credential-bearing derived URLs must never be rendered in user-visible errors or logs.

## Planned slices

### Slice A — Pure playback contracts and reducers

Prefer pure Kotlin where practical.

Deliverables:

- `PlaybackRequest` carrying opaque source/channel identity and navigation context, not a raw URL
- media kind for the current Live scope
- playback lifecycle/state model
- explicit failure taxonomy and retryability contract
- deterministic state reducers
- redaction-focused unit tests
- previous/next navigation context contract

No Media3 dependency and no player UI in this slice unless the Phase 005 Android gate has opened.

### Slice B — Secure live-stream resolution

Deliverables:

- parser for the existing `PlaybackLocatorDescriptor` version/kinds
- source/channel ownership verification
- `SensitiveValueStore` descriptor retrieval
- Xtream live URL construction from securely retrieved credentials and provider stream identity
- direct-locator resolution for M3U/direct-source streams
- explicit redacted resolution failures
- cancellation propagation consistent with existing source/persistence mutation code
- unit tests that prove secrets do not appear in model `toString`, failures, or logs produced by the resolver

Do not place raw stream URLs in `LiveChannelItem`, Room browse projections, or persistent playback state.

### Slice C — Media3 dependency and controller

Only after Phase 005 validation is green.

Deliverables:

- minimal same-version Media3 dependency set
- one focused Media3 controller/session boundary
- lifecycle-safe player creation and release
- prepare/play/pause/retry
- observable player state
- no endless loading state
- deterministic mapping around player events where feasible

Do not add DASH, Cast, download, Transformer, ads, analytics, or unrelated media modules without a concrete requirement.

### Slice D — Reachable Live playback flow

Deliverables:

- replace the current shell-only runtime path with a minimal phone-first navigation/composition path that can reach Live browsing
- channel selection creates an opaque playback request and activates the player flow
- fast return to the channel list
- current channel identity remains stable across the transition
- playback/session ownership survives normal Compose recomposition
- existing personalization/edit interactions remain independent from playback selection

This is runtime composition work, not authorization for a broad navigation-framework redesign.

### Slice E — Player surface and controls

Deliverables:

- video surface
- play/pause where applicable
- bounded loading state
- retry action for retryable failures
- fullscreen control
- aspect-ratio control
- controls that hide when not needed

Visual treatment must remain dark, minimal, professional, and non-promotional.

### Slice F — Audio and subtitle tracks

Deliverables:

- enumerate available audio tracks
- enumerate available subtitle tracks
- select/default/off behavior where supported
- clear empty states
- no assumptions that every stream has multiple tracks

### Slice G — Previous/next channel

Deliverables:

- deterministic previous/next resolution from the explicit active browse/order context
- switching channels without rebuilding unrelated app state
- preservation of user ordering semantics
- no mutation of persistent `My Order` or favorite order from playback navigation

Search/filter results must not silently redefine persistent order.

### Slice H — Picture-in-Picture and orientation

Deliverables:

- Android PiP where supported
- sensible behavior when unavailable
- orientation/fullscreen transitions without unnecessary player recreation
- lifecycle validation across background/foreground transitions

### Slice I — Playback hardening

Deliverables:

- retry/backoff boundaries for recoverable failures
- network loss/recovery behavior
- unsupported stream behavior
- rapid channel-switch stress checks
- lifecycle/resource-release checks
- verification that logs contain no credentials, locator descriptors, or sensitive stream URLs

## Testing strategy

### JVM tests

Prefer deterministic JVM tests for:

- locator descriptor parsing
- playback request validation
- source/channel ownership checks that do not require Android runtime state
- error mapping helpers
- navigation ordering
- redaction
- reducers

### Android compile/instrumentation boundary

CI must continue to compile `androidTest` and the Media3 integration path. Compilation alone does not prove runtime playback behavior.

### Device/emulator validation

Before Phase 006 is final, record runtime evidence on an Android device/emulator with authorized test media covering, where available:

- successful HLS playback
- Live stream startup
- stream unavailable
- network unavailable
- timeout/retry
- unsupported media
- audio/subtitle selection
- fullscreen/orientation
- PiP
- previous/next channel
- return to list

Authentication-failure behavior should be tested only with controlled credentials/test endpoints.

## CI gate

Each implementation slice that changes Android production code should pass the established gate before the next high-coupling slice proceeds:

- JVM unit tests
- Android lint
- debug APK assembly
- androidTest compilation
- Room schema generation/drift check
- debug APK artifact generation

Playback-specific tests should join the existing workflow rather than creating additional expensive workflows without evidence that parallelization is needed.

## Security and privacy invariants

- Never log playlist passwords, Xtream credentials, bearer-like query parameters, locator descriptors containing direct URLs, or resolved sensitive stream URLs.
- Keep raw sensitive locators behind the secure-value boundary until the last practical point before Media3 preparation.
- Do not send credentials or playback metadata to an OwnPlay-operated server.
- Do not add analytics, ads, tracking SDKs, or cloud accounts as part of playback.
- Do not add provider/service branding that implies OwnPlay supplies media.

## Performance boundaries

- Reuse player/session ownership where appropriate instead of recreating it on every recomposition.
- Do not block the main thread with network, secure-store, or credential work.
- Avoid loading full catalogs into the playback layer merely to switch one channel.
- Keep previous/next resolution deterministic and cheap for large channel lists.
- Profile before introducing caching, pooling, or prefetch abstractions.

## Phase 006 completion criteria

Phase 006 is complete only when:

- Phase 005 entry validation is green
- a user can reach Live channels from the app shell and start an authorized stream
- Media3 playback works for supported test streams
- playback state and lifecycle ownership are explicit
- recoverable errors expose retry instead of endless loading
- required failure categories are represented conservatively
- audio/subtitle selection works where tracks exist
- fullscreen/orientation and supported PiP work
- previous/next navigation respects the active ordering context
- returning to the list is fast and predictable
- credentials, sensitive locator descriptors, and resolved stream URLs are absent from logs/UI errors
- the full Android CI gate passes
- runtime device/emulator evidence is recorded

No merge, release, deployment, or publication is authorized by this plan.
