# Audit — Settings approved visual polish — 2026-08-30

## Scope

Presentation-only Settings visual alignment with the approved OwnPlay design direction.

Base exact head: `4cce87fdc45ec7f2f000bd795ff55222d7b6012b` (Draft PR #61)
Source checkpoint before evidence: `0addd4c7e8a5322be200ecda53bf9bf6f7895279`
Branch: `agent/settings-approved-visual-polish-no-apk`

## Files changed

- `app/src/main/java/app/ownplay/player/ui/SettingsComponents.kt`
- `app/src/main/java/app/ownplay/player/ui/SettingsInterface.kt`
- `app/src/main/java/app/ownplay/player/ui/SettingsLandscape.kt`
- `app/src/main/java/app/ownplay/player/ui/SettingsLandscapeRail.kt`

`SettingsScreen.kt` remains unchanged.

## Approved visual language applied

### Shared Settings components

- Device/profile/orientation selectors now use one fixed 10 dp geometry.
- Selected, focused and idle states no longer switch between filled and outlined button types.
- State is communicated by fill/text color only.
- Selector typography weight remains fixed.
- Compact Settings sections use 10 dp corners and 0 dp tonal elevation.
- Shared rows use slightly more consistent vertical spacing.

### Portrait / mobile Settings

- Header uses stronger `headlineSmall` hierarchy.
- Outer spacing is aligned with the Library/Live visual passes.
- Section spacing is increased slightly while remaining compact.
- Mobile Downloads remains present and functionally unchanged.

### Landscape / TV Settings

- Rail and content panel use 12 dp outer geometry and 0 dp tonal elevation.
- Content page headings use `headlineSmall`.
- Rail rows use fixed 10 dp geometry.
- Rail selected/focused states use color only; no border, elevation or font-weight change.
- TV focus is explicitly visible through the same purple primary-container language used elsewhere.
- Downloads remains omitted on TV.

## Functional invariants preserved

`SettingsScreen.kt` was re-read after mutation and remains unchanged.

Therefore this pass does not change:

- default Settings destination behavior;
- nested Back handling;
- TV guard that redirects `DOWNLOADS` to `CONTENT`;
- mobile Downloads navigation;
- device profile persistence callbacks;
- smartphone orientation persistence callbacks;
- Live management navigation;
- playlist management navigation;
- backup/restore behavior;
- source sync behavior;
- source/storage/database behavior.

`SettingsContent.kt` is unchanged, so Live management, Playlists and Backup/Restore callbacks remain as before.

## Architecture boundary

No changes to:

- Media3/player/decoder ownership;
- Live playback or EPG;
- download engine or download storage;
- Room/schema/database architecture;
- source sync implementation;
- backup/restore implementation;
- auth;
- signing;
- release/deployment.

No APK was requested or authorized.

## Static validation completed

- PR #61 exact head verified before branching.
- Active Settings route audited.
- `SettingsScreen.kt` re-read after mutation and confirmed unchanged.
- TV Downloads guard confirmed present.
- `SettingsContent.kt` re-read and confirmed unchanged.
- Final source re-read completed for the four presentation files.
- Source compare before evidence: 4 commits ahead / 0 behind, exactly four modified presentation files.

## Validation not executed on this exact head

- Gradle compile;
- unit tests;
- lint;
- AndroidTest compilation;
- physical smartphone QA;
- physical Android TV / TV Box QA.

A skipped GitHub Actions workflow, if observed, must not be interpreted as compile/test PASS.

## Physical QA checklist

### Smartphone

- Open Settings in portrait.
- Verify header/section spacing and no clipping.
- Verify Smartphone/Tablet/Android TV/TV Box choices keep identical geometry while selected state changes only by color.
- Verify Portrait/Landscape choices keep identical geometry.
- Verify Downloads remains visible and opens the existing Downloads screen.
- Verify Live management, Playlists and Backup/Restore actions behave as before.

### Android TV / TV Box

- Open Settings in landscape.
- Verify rail does not show Downloads.
- Verify rail focus is visible with color only and no border/geometry jump.
- Move focus across Interface, Content and About.
- Verify selected destination retains the same fixed row geometry.
- Verify Content nested screens return to Content.
- Verify Interface profile/orientation controls remain navigable by D-pad.

## Operational boundary

Draft-only checkpoint. Do not merge, mark ready, deploy, release, publish, force-push, rewrite history, alter signing or create an APK without explicit authorization.
