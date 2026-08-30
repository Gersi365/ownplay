# OwnPlay Live Fullscreen + Full EPG visual audit — 2026-08-30

## Scope

This checkpoint is a presentation-only pass stacked on the exact head of Draft PR #60.

Base exact head:

`138395385d749435869e5bb4209c86ff6fe3e73b`

Head branch:

`agent/live-fullscreen-epg-visual-polish-no-apk`

The implementation intentionally leaves the existing Live playback/EPG interaction architecture in place.

## Files changed

Source:

- `app/src/main/java/app/ownplay/player/ui/PlaybackScreen.kt`
- `app/src/main/java/app/ownplay/player/ui/EpgGuideSheet.kt`

Evidence:

- `docs/AUDIT_LIVE_FULLSCREEN_EPG_VISUAL_POLISH_2026-08-30.md`

No policy, playback-engine, repository, persistence, auth, download, signing or release files are changed.

## Fullscreen behavior preserved

The existing interaction contract remains unchanged:

- Fullscreen has no generic playback menu or playback buttons.
- EPG is visible on Fullscreen/channel entry.
- EPG auto-hides after approximately 4.5 seconds while playback is Playing and EPG is not focused.
- TV OK/Center/Enter reveals EPG when hidden.
- TV Down enters the EPG timeline only when EPG data is available.
- TV Left/Right moves through visible EPG items using `LiveFullscreenEpgPolicy`.
- TV Up leaves EPG interaction and returns interaction ownership to the video surface.
- Selecting the final Full EPG item requests the existing one-shot Full Guide bridge and returns to Preview.
- Back returns Fullscreen to Preview through the existing handoff.
- Mobile keeps tap-to-reveal EPG behavior.
- PlayerView binding, one-player/one-decoder ownership and Media3 handoff are unchanged.

`LiveFullscreenEpgPolicy.kt` is unchanged.

## Fullscreen visual changes

### Overlay

- outer overlay radius reduced from 18 dp to 12 dp to align with the approved Live visual language
- outer padding adjusted to 14 dp horizontal / 10 dp vertical
- overlay remains flat at 0 dp tonal elevation
- black overlay opacity adjusted to 0.82 for a slightly more stable text background
- helper copy now exposes `↓ browse` before timeline focus while retaining the existing `← → browse · ↑ video` focused guidance

### Program cards

- cards reduced to a fixed 10 dp radius
- no border or elevation state changes
- selected/current state is communicated by fill and text color only
- selected/current title uses `onPrimaryContainer`; neutral title uses `onSurface`
- fixed Medium title weight retained across selected/current/neutral states
- width normalized to 224 dp and minimum height to 76 dp

The existing `NOW · time` metadata remains one line and does not change card geometry.

### Full EPG card

- fixed 10 dp radius
- 0 dp tonal elevation
- selected state uses color only
- width normalized to 160 dp and minimum height to 76 dp
- `Returns to Preview` remains explicit so the Full EPG transition is discoverable

### Failure surface

- error/status surface reduced to 10 dp and flattened to 0 dp tonal elevation

## Full EPG guide behavior preserved

The existing Full EPG data/focus model remains unchanged:

- `EpgTimelineProjector` still owns current/past/future projection.
- TV initial focus still comes from `EpgGuideFocusPolicy`.
- TV focuses the current program when available.
- TV falls back to the Done control for loading/failed/empty states.
- mobile receives no explicit focus injection.
- tapping/activating a program still opens the existing program details dialog.
- dismissing Full EPG still returns to the Preview surface that opened it.

`EpgGuideFocusPolicy.kt` is unchanged.

## Full EPG visual changes

- title changed from `Program guide` to the product term `Full EPG`
- header spacing increased slightly for clearer hierarchy
- program rows use fixed 10 dp geometry and 0 dp tonal elevation
- current program no longer inserts a separate `NOW` row inside the content column
- current program does not change time/title font weight
- current state is communicated by row fill and text color only
- row spacing increased from 2 dp to 4 dp
- horizontal row inset increased slightly
- day headers remain unchanged semantically
- program descriptions and detail dialogs remain available

Removing the conditional `NOW` content row eliminates a vertical layout shift when the timeline's current program changes.

## Static validation performed

- verified Draft PR #60 is open, draft, mergeable, and exact head `138395385d749435869e5bb4209c86ff6fe3e73b` before branching
- created the new branch from that exact commit
- re-read final `PlaybackScreen.kt`
- confirmed `PlaybackScreen` public/internal function signature is unchanged
- confirmed `openFullGuide()` still requests Full Guide then returns to Preview
- confirmed Fullscreen `BackHandler` still calls `onReturnToChannels`
- confirmed EPG auto-hide constant remains 4,500 ms
- confirmed TV OK/Down/Up/Left/Right routing remains present and still delegates Left/Right bounds to `LiveFullscreenEpgPolicy`
- confirmed PlayerView remains controller-free and uses the existing `PlaybackVideoOutput` bind/unbind path
- re-read final `EpgGuideSheet.kt`
- confirmed Full EPG still uses `EpgGuideFocusPolicy` and `EpgTimelineProjector`
- re-read unchanged `LiveFullscreenEpgPolicy.kt`
- re-read unchanged `EpgGuideFocusPolicy.kt`
- pre-evidence compare showed exactly two source files changed, 2 commits ahead / 0 behind

## Validation boundary

Not executed on this exact head:

- Gradle/Kotlin/Compose compilation
- unit tests
- lint
- AndroidTest compilation
- physical smartphone QA
- physical Android TV / TV Box QA

The source audit is not a substitute for those checks.

## Physical QA required before UX completion

### Smartphone

- open a Live channel and enter Fullscreen
- verify EPG appears immediately
- wait for auto-hide during Playing state
- tap video and verify EPG reappears
- horizontally scroll/tap program cards
- open Full EPG and verify the app returns through Preview before presenting the guide
- inspect current/past/future row readability and program details dialog

### Android TV / TV Box

- enter Fullscreen from Preview using the existing second-OK flow
- verify EPG appears immediately
- allow EPG to auto-hide and press OK to reveal it
- press Down to enter the timeline
- use Left/Right through program cards and the Full EPG card
- press Up to leave timeline interaction
- activate Full EPG and verify return-to-Preview + guide opening
- verify initial Full EPG focus lands on current program when data exists
- verify loading/failed/empty guide states focus Done
- verify Back/ESC returns correctly and no focus is stranded

## Operational boundary

No APK is requested or authorized.

Do not merge, mark ready, deploy, release, publish, force-push, rewrite history, alter signing or create an APK without explicit authorization.
