# OwnPlay approved visual direction — Library pass

Date: 2026-08-30

## Scope

This checkpoint begins implementation of the conversation-approved dark streaming UI reference while preserving OwnPlay's real information architecture and behavior.

Base: Draft PR #57 head `882341bcfdd06b78e7988d1629461afcd8224b7e`.

## Real information architecture preserved

Primary navigation remains:

- Live
- Library
- Settings

Movies and Series remain internal Library destinations/subsections, not primary navigation destinations.

The existing `OwnPlayApp` already uses the same `VideoLibrary` icon in both top and bottom primary navigation, so no additional icon mutation was required in this pass.

## Library structure preserved

`UnifiedLibraryRoute` already provides the required real-product structure:

- Library title
- All / Movies / Series filters
- Movies category filtering
- Series category filtering
- Search
- Cards / Compact / List view modes
- movie details routing
- series details routing

Mobile Offline presentation remains available. TV Offline presentation remains hidden by the preceding TV no-offline stack.

## Visual design system change

`Theme.kt` now uses the approved dark-purple direction:

- primary accent: `#9B7BFF`
- dark purple primary container: `#2A1F45`
- neutral dark background: `#080A0F`
- neutral dark surface: `#0D1016`
- dark surface variant: `#151922`
- muted secondary text: `#B8B4C2`
- smaller global shape radii: 6 / 8 / 10 / 12 / 16 dp

The existing navigation geometry rule is preserved: the Material3 selected indicator blends into the navigation surface, so selected navigation is expressed through color rather than a selected pill changing geometry.

TV shared focus indication automatically adopts the new purple primary color through `OwnPlayDarkColors.primary`; focus geometry/measurement behavior is unchanged.

## What this pass intentionally does not change

- Library data/query/filter semantics
- Movies/Series routing
- Offline/download engine or storage
- TV no-offline presentation rules
- playback engine / Media3 / decoder ownership
- Live Preview / Fullscreen handoff
- EPG repository or timeline logic
- Room/schema/migrations
- auth/credentials/sync
- signing/versioning
- release/deployment/publication

## Validation boundary

Completed:

- exact PR #57 base/head verification before mutation
- source-level information architecture review
- Library structure review
- exact `Theme.kt` post-mutation inspection

Not executed on this exact head:

- Gradle compile
- unit tests
- lint
- AndroidTest compilation
- physical smartphone QA
- physical Android TV / TV Box QA

No APK is requested or authorized.

## Next visual passes

Recommended sequence:

1. Library cards/header/control spacing alignment using the approved design direction while retaining real Library behavior.
2. Live Categories / Channels + Preview + EPG visual alignment.
3. Live Fullscreen + EPG / Full EPG visual alignment.
4. Settings visual alignment.
5. Final cross-screen consistency audit and physical-device QA.
