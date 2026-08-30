# OwnPlay approved visual direction — Library pass

Date: 2026-08-30

## Scope

This checkpoint begins implementation of the conversation-approved dark streaming UI reference while preserving OwnPlay's real information architecture and previously validated no-aggregate browse behavior.

Base: Draft PR #57 head `882341bcfdd06b78e7988d1629461afcd8224b7e`.

## Real information architecture preserved

Primary navigation remains:

- Live
- Library
- Settings

Movies and Series remain internal Library destinations/subsections, not primary navigation destinations.

The existing `OwnPlayApp` already uses the same `VideoLibrary` icon in both top and bottom primary navigation, so mobile and TV use the same Library icon.

## Library no-aggregate behavior

Historical Draft PR #48 validated removal of aggregate `All` controls from phone/tablet consumer browse. This checkpoint preserves that product decision instead of treating `All` as a Library destination.

Current visible Library controls are:

### Mobile / touch

- Offline
- Movies
- Series

### TV / D-pad

- Movies
- Series

TV does not expose Offline presentation, in accordance with the later TV no-offline decision.

The internal `UnifiedLibraryFilter.ALL` state is retained only as the existing implementation backing for the mobile `Offline` destination; it is never labeled or presented as `All`.

Movie and Series category strips also no longer expose `All categories`. When provider categories exist, the first real provider category is selected as the fallback, matching the previously validated no-aggregate behavior.

Other real Library behavior remains:

- search scoped to the selected destination
- Cards / Compact / List view modes
- movie details routing
- series details routing
- mobile offline playback/download presentation
- TV focus restoration

## Visual design system change

`Theme.kt` uses the approved dark-purple direction:

- primary accent: `#9B7BFF`
- dark purple primary container: `#2A1F45`
- neutral dark background: `#080A0F`
- neutral dark surface: `#0D1016`
- dark surface variant: `#151922`
- muted secondary text: `#B8B4C2`
- smaller global shape radii: 6 / 8 / 10 / 12 / 16 dp

The existing navigation geometry rule is preserved: the Material3 selected indicator blends into the navigation surface, so selected navigation is expressed through color rather than a selected pill changing geometry.

TV shared focus indication automatically adopts the new purple primary color through `OwnPlayDarkColors.primary`; focus geometry/measurement behavior is unchanged.

## Architecture boundary

No changes to:

- playback engine / Media3 / decoder ownership
- Live Preview / Fullscreen handoff
- EPG repository or timeline logic
- download engine, files or storage paths
- Room/schema/migrations
- auth/credentials/sync
- signing/versioning
- release/deployment/publication

## Validation boundary

Completed:

- exact PR #57 base/head verification before mutation
- source-level information architecture review
- comparison against the historically validated PR #48 no-aggregate behavior
- exact `Theme.kt` post-mutation inspection
- exact `UnifiedLibraryRoute.kt` post-mutation inspection
- post-mutation diff review confirming the Library correction is limited to the intended filter/category semantics

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

1. Library cards/header/control spacing alignment using the approved design direction while retaining the corrected no-aggregate behavior.
2. Live Categories / Channels + Preview + EPG visual alignment.
3. Live Fullscreen + EPG / Full EPG visual alignment.
4. Settings visual alignment.
5. Final cross-screen consistency audit and physical-device QA.
