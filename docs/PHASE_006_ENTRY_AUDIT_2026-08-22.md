# Phase 006 entry audit — 2026-08-22

## Status

Planning-ready; implementation gate closed.

This audit verifies the repository state from which Phase 006 should start after the Phase 005 Drive/GitHub reconciliation. It records current code boundaries and does not authorize production playback implementation.

## Repository baseline

- repository: `Gersi365/ownplay`
- planning branch: `agent/phase-006-planning-reconciled-no-ci`
- direct base: `c57e4eae24d0857c5c5d2337e7c44fcd86eba249`
- Phase 005 reconciliation report: `docs/PHASE_005_RECONCILIATION_2026-08-22.md`
- Phase 006 plan: `docs/PHASE_006_PLAN.md`
- Media3 planning baseline: `docs/PHASE_006_MEDIA3_BASELINE.md`

The legacy `agent/phase-006-planning-no-ci` branch is intentionally left untouched as historical planning evidence. It should not be force-rebased or treated as the implementation base.

## Verified current runtime boundary

`MainActivity` currently composes only the initial `OwnPlayHome` shell.

Therefore:

- Live browsing is implemented as a screen/repository boundary but is not reachable from the production app shell;
- playback integration cannot be considered reachable until a minimal runtime composition/navigation path is added;
- Phase 006 should not respond to this by introducing a broad navigation architecture unless a smaller deterministic path proves insufficient.

## Verified current stream-security boundary

The existing Live ingestion path already provides the foundation needed for secure playback resolution.

### Descriptor production

`InitialLiveCatalogFactory` creates `PlaybackLocatorDescriptor` values:

- direct M3U/direct-source locator -> `ownplay-locator-v1|direct|<locator>`
- Xtream live stream without direct source -> `ownplay-locator-v1|xtream-live|<streamId>`

The descriptor's `toString` exposure is avoided at the incoming-channel model boundary by explicit redaction of `locatorValue` and `logoValue`.

### Secure storage

`InitialLiveCatalogIngestor` stores descriptor/logo values through `SensitiveValueStore` and writes only opaque reference values into `ProviderChannelEntity`.

Existing failure behavior already distinguishes secure-storage and persistence failure and rethrows coroutine cancellation instead of silently converting it into an ordinary result.

Phase 006 should preserve this behavior in its locator-resolution path.

### Credential storage

The source layer already contains the Android Keystore-backed credential-store implementation. Xtream playback resolution should retrieve credentials through this existing contract rather than inventing another credential persistence mechanism.

## Current Android baseline

Current `app/build.gradle.kts`:

- `minSdk = 26`
- `compileSdk = 36`
- `targetSdk = 36`
- Java source/target 17
- Compose enabled
- no Media3 dependency currently declared

Official Android documentation re-checked on 2026-08-22 identifies Media3 1.11.0 as stable and documents API 23 as sufficient for core video/HLS without DRM. This is a compatibility planning result, not a build result.

## Ready planning decisions

The following are sufficiently supported by current evidence and do not need to be re-derived when implementation begins:

1. use Media3/ExoPlayer unless implementation evidence demonstrates a concrete blocker;
2. keep the initial Media3 dependency set minimal and same-version;
3. start playback from opaque source/channel identity, never a raw locator in the browse model;
4. parse/reuse the existing locator descriptor format rather than replacing it;
5. resolve direct and Xtream locators as late as practical;
6. keep credential and secure-value retrieval outside Compose;
7. isolate ExoPlayer ownership behind a focused controller/session boundary;
8. preserve explicit cancellation propagation and redacted failure behavior;
9. keep previous/next navigation tied to an explicit browse/order context without mutating persistent user order;
10. treat device/emulator playback evidence as required separately from Android compilation.

## Still blocked

The following must not be represented as implemented or validated yet:

- Media3 dependency pinning
- playback domain production classes
- locator resolver production classes
- ExoPlayer controller/session
- player UI
- Live app-shell wiring
- PiP/orientation behavior
- audio/subtitle selection
- previous/next runtime playback switching

These remain blocked until Phase 005 passes the established Android gate.

## Phase 005 gate required before implementation

The reconciled Phase 005 head must pass:

- JVM unit tests
- Android lint
- debug APK assembly
- androidTest compilation
- Room schema generation/drift verification
- debug APK artifact generation

If the gate fails, classify the result as source defect versus environment/tooling failure before making corrective changes.

## First implementation action after gate opens

The first authorized Phase 006 implementation slice should remain low-coupling and test-heavy:

1. re-check the stable Media3 release;
2. introduce pure playback request/state/failure contracts without raw locators;
3. add parser/tests for the existing locator descriptor format;
4. add source/channel ownership and redaction tests around the resolver contract;
5. run the full Android gate before adding the Media3 controller or UI.

This sequence minimizes dependency and runtime coupling while preserving existing source-security architecture.

## Safety

No source code, dependency, Room schema, workflow, main branch, PR base, release, deployment, publication, or provider credential was changed by this audit.
