# Phase 002 — Core Source Model

## Status

Implementation complete on `agent/phase-002-core-sources`.

This branch is intentionally stacked on the validated Phase 001 branch. Neither Phase 001 nor Phase 002 is merged. Merge remains an explicit user-approval boundary.

## Authoritative scope

`OwnPlay_SOURCE.md` defines Phase 002 as:

- playlist source model
- secure credentials handling
- Xtream client
- M3U parser
- source validation
- error model

The implementation remains inside that scope. Room persistence, playback, EPG ingestion, channel personalization, release signing, deployment, and store publication are not part of this phase.

## Implemented

### Playlist source contracts

- `PlaylistSource.Xtream`
- `PlaylistSource.RemoteM3u`
- `PlaylistSource.LocalM3u`
- opaque `CredentialRef` for Xtream credentials
- deterministic `SourceResult` / `SourceError` contracts

Plaintext Xtream username/password values are not members of `PlaylistSource`.

### Source validation

- HTTP/HTTPS URL validation
- Xtream server URL normalization
- rejection of embedded URL user-info credentials
- remote M3U query support without copying URLs into error objects
- `content://` validation for local playlist documents
- explicit cleartext detection

Sensitive URL-bearing validation results use redacted string rendering.

### M3U parsing and loading

The parser supports and tests common playlist fields including:

- `#EXTM3U`
- `#EXTINF`
- `tvg-id`
- `tvg-name`
- `tvg-logo`
- `group-title`
- display name
- stream URL
- `url-tvg` / `x-tvg-url`

Additional hardening includes:

- quoted commas
- single-quoted attributes
- missing `#EXTINF` commas with safe fallback behavior
- UTF-8 BOM tolerance
- prevention of stale metadata leaking to a later entry
- rejection of HTML/garbage lines as stream entries
- remote M3U HTTP/error mapping
- local `content://` loading through `ContentResolver`

### Secure credential handling

- Android Keystore AES key
- AES-256/GCM encrypted credential payloads
- versioned encrypted local payload format
- authenticated additional data for the credential payload format
- opaque credential references
- transient credential byte arrays cleared as soon as practical
- credential objects render redacted values instead of plaintext through `toString()`

The Phase 001 manifest keeps Android app backup disabled.

### Xtream transport

- one shared OkHttp client
- bounded connect/read/call timeouts
- off-main-thread I/O through coroutines
- account validation
- live category discovery
- live stream discovery
- deterministic HTTP/network/TLS/timeout/parse error mapping
- deterministic MockWebServer fixtures
- provider-echoed username/password fields are not retained in `XtreamAccountInfo`

VOD, series, and EPG endpoint expansion is intentionally deferred to the phases that consume those domains.

## Sensitive-value policy

The source layer does not include an HTTP logging interceptor.

String rendering is hardened for values that may carry credentials or tokens, including:

- Xtream credentials
- playlist/source URLs
- normalized remote URLs
- M3U stream/logo/EPG URLs and raw attributes
- Xtream direct-source/icon URLs

Test fixtures verify that representative secret values are absent from these rendered model strings.

Remote M3U URLs may contain query tokens. Their persistence policy must be finalized before Phase 003 stores source configuration; they must not be casually persisted or logged as ordinary public metadata.

## Cleartext transport decision

HTTPS remains the default.

Cleartext HTTP is detected and rejected by default by the source clients/loaders. The Android manifest has not been changed to globally enable unrestricted cleartext traffic.

Legacy HTTP support may be added later only with an explicit source-level policy/opt-in. The application must not silently weaken transport policy merely because some providers use legacy HTTP endpoints.

## Dependencies added

Only dependencies with a concrete Phase 002 use were added:

- OkHttp `5.3.2`
- kotlinx.serialization JSON `1.11.0`
- kotlinx.coroutines Android `1.11.0`
- MockWebServer3 `5.3.2` for deterministic JVM transport tests

No Retrofit or dependency-injection framework was introduced.

## Validation evidence

The existing CI gate runs:

```text
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace
```

Validated Phase 002 slices:

- source contracts + M3U parser — run `31999610910` — success
- Android Keystore credential vault — run `31999892260` — success
- Xtream transport — run `32000276824` — success
- M3U source loading + source verification — run `32000658383` — success
- sensitive-value and malformed-playlist hardening — run `32001048792` — success

Each successful run also completed debug APK artifact upload.

The final Phase 002 head must pass the same gate after this evidence/security-finalization commit. The PR body is the authoritative record for that final run so the document does not require a self-referential follow-up commit.

## Evidence limits

The AES/GCM primitive and credential codec are exercised by JVM tests, including tamper rejection. The Android Keystore adapter itself is compiled and linted but is not claimed to have been exercised against a real Android Keystore in this JVM-only CI environment.

Likewise, local `ContentResolver` integration is compiled/linted and URI validation is tested, but real document-provider interaction remains a device/emulator integration check.

These runtime Android integration checks must be exercised when the corresponding UI/integration path exists and again during the Phase 012 device/emulator validation gate.

## Deferred

The following remain outside Phase 002:

- Room database/schema
- source persistence policy and migrations
- channel reconciliation
- Live browsing UI
- channel personalization
- Media3 playback
- VOD/series domain implementation
- XMLTV/Xtream EPG domain implementation
- backup/restore
- production signing
- store configuration/publication

No release, deployment, or publication is authorized by Phase 002.
