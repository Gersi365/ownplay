# Phase 003 — Local Persistence

## Status

Implementation started on `agent/phase-003-local-persistence` after validated Phase 001 and Phase 002 were merged into `main`.

## Authoritative scope

`OwnPlay_SOURCE.md` defines Phase 003 as:

- database baseline
- provider entities
- personalization entities
- reconciliation model
- migration/versioning policy

This phase must preserve the source-of-truth rule that provider/source data and local personalization are separate. It must not expand into Live UI, drag-and-drop UI, Media3 playback, EPG ingestion, backup/restore, release signing, deployment, or store publication.

## Dependency decision

OwnPlay uses Room 2.8.4 for the initial Android database baseline.

Room 3.0 is stable, but it is a new major API line. The current implementation has no concrete Room 3-only requirement, while the Android migration guidance recommends modernizing on Room 2.8 before moving to Room 3.0. Using Room 2.8.4 minimizes API churn while keeping a supported modern baseline.

KSP2 is used for Room code generation because the project uses AGP 9 and Kotlin 2.3. KSP1 is not compatible with those toolchain generations.

## Schema v1 boundaries

### Provider/source-owned data

- `playlist_sources`
- `provider_categories`
- `provider_channels`
- `playlist_refresh_state`

Provider rows hold source-derived identity and metadata. Provider refresh logic must update these rows without using destructive table replacement as the default strategy.

### User-owned personalization

- `channel_customizations`
- `hidden_entries`
- `favorite_entries`
- `custom_groups`
- `custom_group_memberships`

Personalization is stored independently so provider refresh cannot silently reset local order, hidden state, favorites, rename, logo override, or custom-group membership.

## Sensitive-value persistence policy

Raw source URLs and stream locators may contain credentials or query tokens. They must not be stored as ordinary plaintext Room metadata.

Schema v1 therefore stores opaque references such as `locatorRef`, `streamLocatorRef`, `logoRef`, and optional credential references. A secure locator/value vault will be implemented before any import path persists raw remote URLs or stream locators.

The Room schema must not become a backdoor around the Phase 002 redaction and credential-handling policy.

## Stable identity policy

`channelId` is OwnPlay-local identity and survives provider refresh where stable matching succeeds.

`providerKey` is a source-specific stable key used for reconciliation. It must not contain raw credentials or token-bearing URLs. Examples may include:

- Xtream stream ID scoped by source
- a deterministic non-sensitive fingerprint for M3U entries

The exact M3U fingerprint algorithm is deferred to the reconciliation slice and must be covered by deterministic tests before it is relied upon for migration or refresh behavior.

## Ordering policy

Provider order and local custom order are separate fields/tables:

- `providerOrder` belongs to provider data
- `manualOrder` belongs to user customization
- `favoriteOrder` belongs to favorites
- `groupOrder` belongs to custom groups/memberships

A temporary sort must never overwrite these persistent user-owned order values.

## Availability policy

Provider channels are not deleted merely because they disappear from one refresh.

The schema supports lifecycle values equivalent to:

- available
- temporarily unavailable
- removed

The reconciliation algorithm and transition rules will be implemented and tested in a later Phase 003 slice before Phase 009 expands refresh orchestration.

## Migration/versioning policy

- database starts at schema version 1
- Room schema export is enabled
- schema history must be preserved in source control once generated
- migrations must be explicit and tested before version bumps
- no `fallbackToDestructiveMigration()` in production database creation
- destructive migration requires explicit user authorization because it may delete personalization

## Slice plan

### Slice A — Database baseline

- Room/KSP configuration
- schema v1 entities
- DAO boundaries
- database factory without destructive fallback
- tests for persistence contracts and redaction assumptions
- CI build/lint/test validation

### Slice B — Secure locator persistence

- encrypted storage for remote source locators and stream locator values
- opaque references from Room rows
- deletion/update lifecycle
- tamper/error handling
- no sensitive values in logs or model rendering

### Slice C — Stable matching and reconciliation primitives

- deterministic source-specific provider keys
- generation-based refresh staging
- preserve personalization when provider metadata changes
- new/temporarily unavailable/removed semantics
- unit tests for refresh scenarios

### Slice D — Migration and database integration evidence

- capture Room schema JSON
- migration test harness
- Android/emulator integration checks where practical
- final Phase 003 evidence report

## Current deferrals

The following are intentionally not part of Slice A:

- VOD/series persistence tables
- EPG tables
- recent-channel history
- playback progress
- backup format
- UI repositories/view models
- data import from real user sources

Those tables will be added when their consuming phases establish concrete requirements, rather than locking speculative schema now.
