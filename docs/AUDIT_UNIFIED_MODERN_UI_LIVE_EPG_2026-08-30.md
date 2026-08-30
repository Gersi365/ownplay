# OwnPlay — Unified Modern UI + Live EPG Audit

Date: 2026-08-30
Status: source implementation / static audit checkpoint

## Baseline

- Repository: `Gersi365/ownplay`
- Base branch: `agent/live-tv-toolbar-focus-audit-no-apk`
- Base commit: `ea7059fccc350a02409f4c7a55583e3f3c1c93c1`
- Work branch: `agent/unified-modern-ui-live-epg-no-apk`
- Scope applies to both smartphone/touch and TV/D-pad presentations.

## Product requirements implemented in this pass

1. Refresh the application visual language from the main navigation outward.
2. Keep active/focused geometry stable; express state primarily through color.
3. Remove delayed channel-state indicators that change row/card layout.
4. Open Live Preview from channel selection without playback/control buttons.
5. Back/ESC closes Preview before higher-level navigation on both device families.
6. Keep Preview EPG visible alongside Preview.
7. Remove the Live fullscreen playback menu and buttons entirely.
8. Show Live fullscreen EPG at the bottom automatically for a short period.
9. TV fullscreen: OK reveals EPG, Down enters the EPG timeline, Left/Right navigates available programmes, Up returns focus to video.
10. Provide a lightweight Full EPG affordance that first returns fullscreen to Preview and then opens the existing full guide.
11. Preserve the existing reliability-first single-player Live Preview/Fullscreen handoff.

## Visual system

`ui/theme/Theme.kt`

- Dark palette shifted to a calmer blue / teal accent system with darker neutral surfaces.
- Shape radii were tightened so cards and surfaces read as less inflated.
- Material3 navigation selection indicator uses the same color as the navigation surface, so the selected destination is conveyed by tint rather than by a visible pill whose geometry appears/disappears.
- The theme remains shared by mobile and TV so the visual direction propagates beyond the initial navigation surface.

`ui/tv/TvRemoteIndication.kt`

- Removed the previous 4 dp focus outline.
- TV focus now draws color fill only.
- Focus/press indication does not change measured component geometry.

## Live channel state

`ui/live/PortraitLiveViewModes.kt`

- Removed the delayed `▶` playing marker.
- List/Compact channel title weight no longer changes when the selected/active state changes.
- List, Compact, and Gallery/Cards communicate active/focused state with color while keeping padding and shape stable.
- Favorite `★` remains a persistent metadata indicator because it is independent of playback selection.

## Preview

`ui/LivePreviewPanel.kt`

- No Play/Pause, Previous/Next, Fullscreen, Close, Tracks, resize, or other control buttons are rendered in Preview on either mobile or TV.
- TV keeps channel-browser focus so the established second-OK behavior can open fullscreen.
- Mobile may tap the video surface itself to open fullscreen without introducing a visible button layer.
- Loading/failure status remains non-interactive so playback state is still understandable.

`ui/live/LiveBrowseHierarchyPolicy.kt`

- Preview now owns Back on every device family.
- TV still owns Channels -> Categories Back after Preview is closed.
- Category-root Back continues to propagate to normal app navigation.

## Preview EPG

`ui/EpgPanel.kt`

- Current/next EPG remains alongside Preview.
- Styling is lighter and uses the shared visual system.
- A low-weight `Full guide →` affordance replaces a heavier button-style treatment.
- The panel publishes the already loaded snapshot to the Live EPG presentation bridge.

## Live fullscreen

`ui/PlaybackScreen.kt`

The previous generic Live control overlay was replaced with a Live-specific EPG-first presentation while preserving the existing function boundary used by `OwnPlayApp`.

Removed from Live fullscreen:

- Play/Pause button
- Previous/Next channel buttons
- Fullscreen toggle button
- Tracks menu
- resize mode menu
- diagnostics menu
- generic playback control bar

Fullscreen interaction:

- EPG is visible on fullscreen/channel entry.
- It auto-hides after approximately 4.5 seconds once EPG loading is complete and playback is running.
- Mobile: tapping video reveals EPG again.
- TV: OK reveals/restarts the EPG presentation.
- TV: Down enters the EPG timeline when programmes are available.
- TV: Left/Right moves through the visible EPG range and the final Full EPG affordance.
- TV: Up returns interaction to the video layer.
- OK on the Full EPG affordance requests the full guide and returns through the established fullscreen -> Preview transition.

The EPG overlay is separate from playback controls because Live fullscreen no longer renders playback controls.

## Full EPG handoff

`ui/LiveEpgPresentationBridge.kt`

- UI-only in-memory bridge.
- Does not create a second player, decoder, database, or EPG repository.
- Keeps only a `WeakReference` to the existing `OwnPlayAppRuntime` so fullscreen can resolve the exact selected-channel EPG even if the second OK occurs before Preview EPG lookup completes.
- Lookup failure is non-fatal and resolves to an unavailable EPG state while video playback continues.
- Full-guide request is one-shot.

`ui/OwnPlayRoot.kt`

- Binds the existing app runtime to the weak presentation bridge.

When Full EPG is requested, fullscreen returns through the existing Live transition to Preview. The newly composed Preview EPG consumes the one-shot request and opens the existing full EPG guide over Preview.

## Regression policy coverage

`LiveBrowseHierarchyPolicyTest`

- Preview Back ownership on TV and non-TV.
- TV hierarchy Back behavior remains TV-only.
- Existing first OK -> Preview / second OK -> Fullscreen behavior remains covered.

`LiveFullscreenEpgPolicyTest`

- Timeline cannot be entered without EPG programmes.
- Right navigation reaches the Full EPG slot after the last programme.
- Left/Right movement clamps to the valid range.
- Empty EPG never exposes a Full EPG selection.

## Reliability boundary

This pass does not modify:

- Media3/ExoPlayer engine implementation
- the one-player/one-decoder policy
- Live surface handoff architecture
- Room entities, schemas, DAOs, or migrations
- playlist/source credentials or auth
- sync architecture
- download architecture
- signing configuration
- application ID or versioning

## Validation boundary

Source-level audit and deterministic policy coverage are present on this branch.

Executable Gradle validation has **not** run on the exact head in this environment. The available local command runtime fails before command execution, and the connected GitHub toolset does not expose the manual `workflow_dispatch` required to start `Android Validation No APK`.

Therefore this checkpoint must not be described as compile/test/lint PASS.

Still required before promotion:

- exact-head Kotlin/Compose compilation
- unit tests
- lint
- AndroidTest Kotlin compilation
- physical smartphone Preview / fullscreen / Back validation
- physical TV/TV Box OK / D-pad / ESC validation
- physical Preview <-> Fullscreen surface/freeze validation
- visual QA of navigation, List/Compact/Gallery channel state, Preview EPG, fullscreen EPG and full-guide handoff

No APK is requested or intentionally produced by this branch.
