# Phase 001 — Android Baseline

## Status

Build-phase implementation baseline.

## Scope

This phase establishes only the Android phone project foundation. It does not implement Xtream, M3U, playback, persistence, channel management, or provider integration.

## Authoritative product source

The authoritative product/source specification is `OwnPlay_SOURCE.md` in the dedicated OwnPlay Google Drive project folder.

GitHub is authoritative for code and implementation history.

## Toolchain baseline

- Android Gradle Plugin: 9.3.0
- Gradle: 9.5.0
- JDK: 17
- Compile SDK: 36
- Target SDK: 36
- Minimum SDK: 26
- Jetpack Compose BOM: 2026.06.00
- AndroidX Core KTX: 1.18.0
- Activity Compose: 1.13.0

API 37 / Android 17 is intentionally not targeted in this baseline because it is still a preview SDK at the time this baseline is established.

## Application identity

Development namespace/application ID:

`app.ownplay.player`

This identifier is intentionally product-neutral and contains no personal username or account identifier. It remains provisional for build-phase implementation and must be reviewed before any public store publication.

## UI baseline

The initial screen is intentionally minimal:

- dark professional presentation
- restrained single accent color
- no provider-like branding
- explicit player-only positioning
- no non-functional navigation or playlist controls

## Validation contract

The authoritative CI validation command is:

```text
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace
```

The workflow pins Gradle 9.5.0 and JDK 17 and verifies the GitHub-hosted runner's preinstalled Android SDK 36 / build-tools 36.0.0 before building.

A successful Phase 001 validation requires:

- unit tests pass
- Android lint passes
- debug APK assembles successfully

## Deferred

The following remain outside this phase:

- Xtream client
- M3U/M3U8 parser
- XMLTV/EPG
- Media3 player
- Room/DataStore
- channel reordering
- hide/unhide
- favorites
- custom groups
- backup/restore
- production signing
- store configuration

No release or deployment is authorized by this baseline.
