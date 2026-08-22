# Phase 006 Slice B secure-resolution evidence — 2026-08-22

## Status

Implementation is present on `agent/phase-006-secure-resolution-no-ci` and is intentionally stacked on the exact Slice A head that passed Android CI run `32570603003` (#63):

`57a0687d82a7f7b09ee8269975c43c8ddd78b19f`

Slice B implementation commit:

`63b5e1eaa5021ea2242e7af597c4b048a30ffc67` — `Implement Phase 006 secure live-stream resolution`

No pull request has been opened for Slice B and no Android CI run has been intentionally triggered by this branch yet.

## Scope

The implementation adds the secure resolution boundary planned for Phase 006:

- source/channel ownership verification
- primary-key channel lookup without loading the full Live catalog
- opaque stream descriptor retrieval through `SensitiveValueStore`
- reuse of the existing `ownplay-locator-v1` parser from Slice A
- direct HTTP/HTTPS locator validation and cleartext gating
- Xtream server locator retrieval through `SensitiveValueStore`
- Xtream credential retrieval through the existing `CredentialStore`
- late Xtream Live URL construction
- explicit redacted resolution failures
- cancellation propagation
- JVM-focused resolution/redaction tests

It does not add Media3, player UI, Compose integration, a Room schema migration, a new locator descriptor format, a new credential store, analytics, release, deployment, or publication work.

## Persistence boundary

The existing schema already contains all data required for resolution:

- `playlist_sources.locatorRef`
- `playlist_sources.credentialRef`
- `provider_channels.streamLocatorRef`
- `provider_channels.sourceId`
- `provider_channels.channelId`
- `provider_channels.availability`

The only DAO change is a read query on the existing `provider_channels.channelId` primary key:

`ProviderCatalogDao.channelById(channelId)`

The reconstructed pre-change `PersistenceDaos.kt` blob was verified locally to equal the repository baseline blob exactly:

`8f88055510725033854f7dfe569982173450e993`

The modified DAO blob is:

`58c694441f94b0c4576506b26265cbfc118076a5`

Remote compare reports exactly 3 added lines in `PersistenceDaos.kt` and no deletions. No table, column, index, entity, database version, or migration file is changed.

## Resolution model

### Lightweight lookup records

`PlaybackSourceRecord` and `PlaybackChannelRecord` expose only the metadata needed to resolve one requested channel. Their `toString()` implementations redact source/channel IDs and opaque references defensively.

`RoomPlaybackResolutionLookup` adapts the existing Room DAOs to those lightweight records.

Xtream source kind is mapped from the existing `SourceKinds.XTREAM`; other source kinds remain `OTHER` because direct descriptors do not need provider-specific source reconstruction.

A removed channel is rejected before secure-store lookup. Other channel availability states are not converted into a permanent block at this boundary.

### Direct descriptors

A direct descriptor is retrieved only after source/channel ownership succeeds. The payload is validated through the existing `SourceValidator.validateRemotePlaylistUrl` path.

Supported transport here remains HTTP/HTTPS. Cleartext HTTP is rejected unless `allowCleartext=true` is explicitly supplied to the resolver, matching the established source-network policy.

The resolved locator object carries the raw locator only at the final player-facing boundary and overrides rendering so the value is always `<redacted>`.

### Xtream Live descriptors

For `ownplay-locator-v1|xtream-live|<streamId>` the resolver:

1. requires the persisted source kind to be Xtream;
2. retrieves the source server URL from `SensitiveValueStore` using the existing opaque `locatorRef`;
3. validates/normalizes the server URL with `SourceValidator.validateXtreamServer`;
4. enforces the same cleartext opt-in rule;
5. creates the existing `CredentialRef` from the persisted opaque credential reference;
6. retrieves `XtreamCredentials` from the existing `CredentialStore`;
7. rejects missing or blank credentials without classifying that local configuration problem as a provider authentication failure;
8. constructs the live transport locator only at the resolution boundary.

The initial Xtream Live transport baseline uses the widely implemented Xtream-compatible pattern:

`/live/{username}/{password}/{streamId}.ts`

The path is built with OkHttp path segments rather than string concatenation so credential path components receive URL encoding. No credential-bearing URL is persisted or rendered by the resolution models.

The `.ts` output is an initial Live baseline. OwnPlay does not yet persist the provider account's `allowed_output_formats`; changing output-format selection later must be evidence-driven rather than silently changing the descriptor format.

## Failure contract

Resolution failures are typed and contain no free-form provider response, exception message, descriptor text, URL, username, password, or secure-store payload.

Current reasons cover:

- source missing/disabled
- channel missing
- source/channel mismatch
- channel removed
- invalid/missing descriptor reference or descriptor
- secure-store failure
- unsupported source kind
- invalid/missing source locator reference/value
- missing/invalid credential reference
- missing/invalid credentials
- credential-store failure
- cleartext transport blocked
- persistence failure

Provider `AUTHENTICATION_FAILURE` is intentionally not synthesized here from missing local credentials. The Phase 006 plan requires authentication failure to be reported only when HTTP/provider evidence supports it.

## Cancellation

Persistence, secure-store, and credential-store operations explicitly rethrow `CancellationException` before generic exception mapping. A JVM test fixture verifies secure-store cancellation propagation.

## Tests added

`PlaybackResolutionTest` covers:

- direct descriptor late resolution
- direct-locator rendering redaction
- ownership mismatch before secure lookup
- removed-channel short circuit
- Xtream source/server/credential resolution
- Xtream final-locator rendering redaction
- provider-kind mismatch
- direct and Xtream cleartext rejection by default
- invalid and missing descriptors
- missing credentials without false authentication classification
- secure-store cancellation propagation
- redaction of lightweight internal records

## Local validation

Available local compiler:

- `kotlinc-jvm 1.9.0`

Checks performed before the remote commit:

1. `PlaybackResolution.kt` compiled against minimal signature-compatible stubs: PASS.
2. `RoomPlaybackResolutionLookup.kt` syntax-compiled against minimal DAO/entity stubs: PASS.
3. `PlaybackResolutionTest.kt` syntax-compiled against minimal JUnit/coroutine stubs: PASS.
4. Dependency-free resolution smoke harness: `PHASE_006_SLICE_B_SMOKE=PASS`.
5. The smoke harness exercised Xtream late resolution, redacted rendering, and source/channel mismatch rejection.
6. The reconstructed DAO baseline Git blob matched the repository baseline SHA exactly before the 3-line query addition.
7. Every remote source/test blob created for Slice B matched its local `git hash-object` SHA exactly.

These checks are not substitutes for the repository Gradle/Android CI gate.

## Remote audit

Compare from validated Slice A head `57a0687d...` to Slice B implementation commit reports:

- status: ahead
- ahead by: 1
- behind by: 0
- changed paths: 4

Paths:

- `app/src/main/java/app/ownplay/player/persistence/PersistenceDaos.kt` — +3
- `app/src/main/java/app/ownplay/player/playback/PlaybackResolution.kt` — +235
- `app/src/main/java/app/ownplay/player/playback/RoomPlaybackResolutionLookup.kt` — +35
- `app/src/test/java/app/ownplay/player/playback/PlaybackResolutionTest.kt` — +308

Implementation tree:

`2b65af2c744714aeb8775e3fa60619e62ec2d8b6`

No pull-request-triggered workflow run is associated with implementation commit `63b5e1ea...`.

## Required next gate

Before Media3 dependency/controller work, Slice B should receive one draft-PR run of the established Android CI gate:

- JVM unit tests
- Android lint
- debug APK assembly
- `compileDebugAndroidTestKotlin`
- Room schema generation/drift verification
- debug APK artifact generation

The critical expected schema result is that Room schema v1/v2 remain byte/semantic-equivalent because Slice B adds only a DAO read query and no persistence model change.

No merge of PR #7, no merge of Slice B, no Media3 dependency pin, no player UI, no release, deployment, or publication is authorized by this evidence record.