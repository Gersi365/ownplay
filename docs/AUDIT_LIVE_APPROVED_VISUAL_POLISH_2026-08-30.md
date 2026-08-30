# OwnPlay approved visual direction — Live browse / Preview / EPG pass

Date: 2026-08-30

## Scope

This checkpoint applies the conversation-approved dark-purple visual direction to the active Live browsing path while preserving the existing reliability model and interaction contracts.

Base: Draft PR #59 head `6cf4f62535b59bfee174abd7825ec71628661ae2`.

Source checkpoint before this evidence commit: `8672edb5d305d993bf3600179e6db603104a2f8c`.

## Product structure preserved

Primary navigation remains:

- Live
- Library
- Settings

The active Live flow remains:

- TV: Categories -> Channels -> Preview -> Fullscreen
- touch/phone: channel browse -> Preview -> Fullscreen
- Preview remains presentation-only
- Preview remains paired with EPG
- Full EPG behavior remains outside this visual pass and is not changed here

## No aggregate All

Historical Draft PR #48 validated removal of aggregate `All` category controls from consumer browsing. This checkpoint preserves that decision in the active Live path.

### TV categories

- `All channels` is removed.
- A valid selected provider category is preferred.
- If no valid selection exists, the first real provider category is the visual/focus fallback.

### Touch / non-TV category strip

- The `All` category chip is removed.
- When a category strip is active and categories exist, null/invalid selection is replaced with the first real provider category.

The `All groups` entry inside the custom-group popup remains a reset for the custom-group filter; it is not the removed aggregate provider-category destination.

## TV category visual behavior

`LiveBrowseHierarchy.kt` now keeps category-row geometry stable across normal, selected and focused states:

- fixed 10 dp shape
- fixed title font weight
- no focus/selected border
- no focus/selected tonal-elevation change
- state is communicated through fill, text and icon color only

The Categories header and spacing were aligned with the approved dark-purple visual direction.

## Channel browse visual behavior

`PortraitLiveViewModes.kt` remains the shared List / Compact / Cards implementation.

Visual updates:

- search field uses 10 dp corners and slightly more breathing room
- toolbar spacing is increased without changing action order or focus callbacks
- category strip uses real provider categories only
- TV Cards minimum width is 176 dp; touch remains 156 dp
- standard channel cards use 10 dp corners and 0 dp tonal elevation
- active/focused channel card uses purple container tint without changing geometry
- List and Compact active/focus treatment remains color-only
- logo surfaces use 8 dp corners
- grid/list gutters are slightly increased

### Stable delayed EPG presentation

Channel secondary metadata is constrained to one line in List, Compact and Cards. The text may update from category metadata to `Now · time · programme` when EPG arrives, but its maximum line count is stable so that asynchronous EPG arrival does not increase row/card height.

The removed `▶` playing marker remains absent.

## Preview

`LivePreviewPanel.kt` remains presentation-only and retains:

- no visible playback controls
- no close button
- no fullscreen button
- transparent touch overlay on non-TV for tap-to-fullscreen
- TV focus remaining in the channel browser
- existing PlayerView / video-output bind and unbind behavior

Visual-only changes:

- Preview surface: 10 dp corners, 0 dp tonal elevation
- LIVE badge: 6 dp corners, flat presentation
- failure status surface: 8 dp corners, flat presentation

## Preview EPG

`EpgPanel.kt` retains exactly the same EPG data and interaction responsibilities:

- publishes the snapshot through `LiveEpgPresentationBridge`
- consumes the one-shot Full Guide request
- whole panel opens the guide only when programs are available and loading is false
- Current / Next presentation remains intact

Visual-only changes:

- 10 dp corners
- reduced surfaceVariant fill intensity
- slightly increased internal spacing
- fixed medium-weight EPG header
- 0 dp tonal elevation

## Landscape workspace

`LandscapeLiveWorkspaceAdaptive.kt` keeps the existing 62/38 browse-to-preview split and the same D-pad focus policy.

Visual-only changes:

- outer workspace padding increased to 12 / 8 dp
- browser/Preview+EPG surfaces use 12 dp corners
- tonal elevation removed
- panel spacing increased to 10 dp
- divider uses `outlineVariant`

The following focus actions are unchanged:

- Browser Right handling on TV with Preview
- Preview Left -> Browser
- Preview Down -> EPG
- EPG Left -> Browser
- EPG Up -> Preview
- channel focus restoration when Preview closes

## Playback / navigation boundary

`LiveRoute.kt` was not modified by this checkpoint.

Therefore this pass does not change:

- first TV OK -> Preview
- second TV OK on the same channel -> Fullscreen
- Back/ESC closes Preview first
- Channels -> Categories Back behavior
- source refresh / EPG refresh logic
- channel selection routing
- playback start/restart behavior
- one-player / one-decoder handoff
- Media3 player ownership

## Architecture boundary

No changes to:

- Media3 / decoder ownership
- playback engine
- Fullscreen EPG policy
- EPG repository / timeline model
- download engine / storage
- Room schema / migrations
- auth / credentials / sync
- signing / versioning
- release / deployment / publication

## Static validation performed

- exact PR #59 base verification
- active routing audit through `OwnPlayApp` -> `LiveRoute`
- exact source inspection after each large rewrite
- final source compare: 5 source commits ahead / 0 behind before this evidence commit
- `LiveRoute.kt` re-read on the branch and confirmed unchanged
- landscape focus policy re-read after mutation and confirmed structurally unchanged
- Preview PlayerView binding and transparent touch overlay re-read after mutation
- EPG bridge publication and one-shot Full Guide request re-read after mutation

## Validation not executed

On this exact branch/head, the following have not been executed:

- Gradle compile
- unit tests
- lint
- AndroidTest Kotlin compilation
- physical smartphone QA
- physical Android TV / TV Box QA

No APK is requested or authorized.

## Physical QA targets

Before calling this visual pass complete, verify on real hardware:

### TV / TV Box

- Categories opens with first valid real category focused if no valid prior category exists.
- No `All channels` entry is visible.
- category focus/selection changes only color, not geometry/border/weight.
- channel List / Compact / Cards keep stable geometry as current EPG arrives.
- first OK opens Preview and keeps browser focus.
- second OK on the same channel opens Fullscreen.
- Back/ESC closes Preview before hierarchy navigation.
- Preview + EPG remain legible at TV viewing distance.

### Smartphone / tablet

- no aggregate `All` category chip is visible.
- first real provider category is selected when needed.
- search, Favorites, Browse and View controls retain behavior.
- Preview tap opens Fullscreen.
- Preview Back closes Preview.
- EPG panel opens Full Guide when available.
