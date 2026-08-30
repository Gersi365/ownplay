# OwnPlay Live TV D-pad Audit — 2026-08-30

## Scope

Focused second-pass audit of Live TV navigation and focus behavior across List, Compact, and Gallery/Cards modes.

Audit base:

- Branch: `agent/live-tv-hierarchy-preview-no-apk`
- Commit: `22d551d609cd13abdb0ced0b9db3e14c84ab966c`

Audit branch:

- `agent/live-tv-dpad-audit-no-apk`

No APK creation, Room/schema migration, database change, authentication change, playback-engine replacement, signing change, deployment, release, publication, merge, or history rewrite is part of this audit.

## Authoritative behavior checked

TV Live navigation should remain deterministic:

1. Categories -> Channels.
2. First OK on a channel -> Preview.
3. Second OK on the same previewed channel -> Fullscreen.
4. OK on another channel -> replace Preview, not Fullscreen.
5. TV Preview has no interactive playback control strip.
6. Back/ESC with Preview -> close Preview only.
7. Next Back/ESC from Channels -> Categories.
8. Back/ESC at Categories -> normal app-level handling.
9. List, Compact, and Gallery/Cards share the same activation behavior.

## Findings

### F1 — Fullscreen -> Preview could restore the wrong hierarchy level

`OwnPlayApp` removes `LiveRoute` from composition while Live Fullscreen is active. On return to Preview, `LiveRoute` is created again.

The prior hierarchy initialization used only the device type, so TV recreated at `CATEGORIES` even when an active Live Preview selection already existed. This could produce a selected Preview panel alongside the category root instead of restoring Channels.

Correction:

- `LiveBrowseHierarchyPolicy.initialLevel(...)` now accepts `hasPreview`.
- TV with an active Preview initializes at `CHANNELS`.
- `LiveRoute` passes the actual Preview presence when it is created.
- A deterministic unit-policy assertion covers this lifecycle case.

### F2 — Category navigation was duplicated inside the Channels level

The new TV hierarchy already provides Categories -> Channels, but the reused channel browser still rendered the legacy horizontal category chip strip.

That produced two category-navigation models and an unnecessary focusable row above the channel list.

Correction:

- `PortraitLiveBrowseWithViewModes` now has a backwards-compatible `showCategoryStrip` parameter defaulting to `true`.
- `HierarchicalLiveBrowse` passes `showCategoryStrip = false` for the hierarchy Channels level.
- Existing non-hierarchy callers keep the previous behavior through the default value.

### F3 — D-pad Right could escape the TV channel browser

The previous TV code stopped explicitly routing Right into Preview, but returned `false` for the event. Default Compose focus search could therefore move focus into the right-side Preview/EPG area.

This weakens the intended two-step interaction because the second OK is expected to remain on the selected channel.

Correction:

- TV browser consumes Right while Preview exists.
- Non-TV landscape behavior continues to use the existing Browser -> Preview focus transition.
- The behavior is exposed through `LandscapeLiveFocusPolicy.consumeBrowserRight(...)` and has deterministic unit-policy coverage.

## View-mode audit

List, Compact, and Gallery/Cards all route activation through the same `onChannelSelected(channelId)` callback.

Therefore the TV first-OK / second-OK policy is shared rather than duplicated per view mode.

Focus restoration uses the same channel ID and request generation for all three modes; the list modes scroll through `LazyListState`, while Gallery/Cards uses `LazyGridState`.

No separate playback or activation implementation was found for any Live view mode.

## Remote-key audit

The activity-level TV guard uses a per-key standard cooldown and suppresses repeated key-down events. This prevents accidental rapid duplicate activation while still allowing a deliberate second OK after the cooldown.

`KEYCODE_ESCAPE` is translated to `onBackPressedDispatcher.onBackPressed()`, so ESC uses the same Compose BackHandler chain as Back.

## Validation status

Source-level checks performed:

- exact branch/base audit before mutation;
- focused diff review;
- call-path review for Categories, Channels, Preview, Fullscreen, Back/ESC, and TV remote input;
- regression-policy tests added for Fullscreen -> Preview hierarchy restoration and TV Right-key focus trapping;
- existing shared activation path confirmed for List, Compact, and Gallery/Cards.

Executable validation status:

- `:app:testDebugUnitTest`: NOT EXECUTED on this audit head.
- `:app:lintDebug`: NOT EXECUTED on this audit head.
- `:app:compileDebugAndroidTestKotlin`: NOT EXECUTED on this audit head.
- APK: NOT CREATED.

Reason: the connected GitHub action set does not expose manual `workflow_dispatch` for the repository's no-APK validation workflow. A local command runtime was also unavailable before command execution, so no local Gradle result exists. This is an environment/tooling limitation, not a source PASS or source FAIL.

## Physical QA boundary

No physical TV/TV Box PASS is claimed.

A future explicitly authorized update-compatible QA APK should verify at minimum:

- Categories -> Channels focus entry;
- List / Compact / Gallery D-pad movement;
- first OK -> Preview;
- second OK -> Fullscreen;
- Fullscreen -> Preview returns to Channels and the selected channel;
- Right does not escape the channel browser while TV Preview is open;
- OK on a different channel replaces Preview;
- ESC closes Preview first;
- next ESC returns Channels -> Categories;
- root ESC/Back reaches exit confirmation only at the actual app-exit boundary.
