# Phase 002 — Core Source Model

## Status

Implementation in progress on `agent/phase-002-core-sources`.

This branch is intentionally stacked on the validated Phase 001 branch. Phase 001 remains unmerged pending explicit user authorization.

## Authoritative scope

`OwnPlay_SOURCE.md` defines Phase 002 as:

- playlist source model
- secure credentials handling
- Xtream client
- M3U parser
- source validation
- error model

Phase 002 must not expand into Room persistence, playback, EPG ingestion, channel personalization, or production publication.

## Implementation slices

### Slice A — Pure source contracts

- playlist source types
- opaque credential references
- source validation
- non-sensitive source error model
- resilient M3U parser
- JVM unit tests

No network or Android credential I/O is required for this slice.

### Slice B — Secure credential vault

- Android Keystore AES key
- AES/GCM encryption
- encrypted local credential payloads
- opaque credential references only in source models
- no credential values in logs, exceptions, or audit evidence
- tests for round-trip and deletion behavior where practical

AndroidX `EncryptedSharedPreferences` is intentionally not selected because AndroidX Security Crypto APIs are deprecated. The implementation will use Android Keystore primitives directly.

### Slice C — Xtream transport

- one shared OkHttp client
- bounded connect/read/call timeouts
- typed Xtream request/response models
- account/source validation
- selected Phase 002 discovery endpoints needed to validate and inspect a source
- deterministic mapping from network/HTTP/parse failures into the source error model
- MockWebServer tests

Phase 002 will not log request URLs containing credentials.

### Slice D — Source validation integration

- validate Xtream account inputs without persisting plaintext credentials in the source model
- validate remote M3U URL sources
- validate local M3U document URIs
- clear distinction between malformed source, authentication failure, network failure, timeout, and unsupported transport

## Transport security decision

HTTPS is treated as the secure default.

Many legacy user-provided IPTV endpoints use cleartext HTTP. Phase 002 will identify cleartext sources explicitly rather than silently enabling unrestricted cleartext network traffic. A final product policy for legacy HTTP support must be recorded before global cleartext behavior is enabled in the Android manifest/network security configuration.

## Dependency policy

Dependencies are added only when a concrete Phase 002 need exists.

Planned candidates:

- OkHttp for HTTP transport
- kotlinx.serialization JSON for typed response parsing
- kotlinx.coroutines Android for off-main-thread I/O
- MockWebServer for deterministic transport tests

No Retrofit or dependency-injection framework is required by the current scope.

## Phase 002 validation gate

Phase 002 is complete only when:

- M3U parser tests cover common metadata and malformed real-world variants
- source validation tests pass
- credentials are encrypted at rest and never logged
- Xtream validation works against deterministic test fixtures
- error mapping is deterministic and does not include sensitive input values
- Android unit tests pass
- Android lint passes
- debug APK assembles successfully

No release, production signing, deployment, or store publication is authorized by this phase.
