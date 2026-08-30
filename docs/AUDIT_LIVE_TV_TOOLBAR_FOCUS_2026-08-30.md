# OwnPlay Live TV Toolbar Focus Audit — 2026-08-30

## Scope

Third focused source-level audit of OwnPlay Live TV navigation, stacked on the D-pad hardening checkpoint from Draft PR #52.

Base checkpoint:
- branch: `agent/live-tv-dpad-audit-no-apk`
- commit: `56f86d0812cb251f8e1ca2a2cd455dddcf30a717`

Audit branch:
- `agent/live-tv-toolbar-focus-audit-no-apk`

This pass is limited to Live TV toolbar/search/menu focus behavior. It does not change playback architecture, Media3/ExoPlayer ownership, Room/database state, sync/auth architecture, signing, downloads, release/deployment behavior, or provider data.

## Findings

### 1. Search opened without deterministic TV focus

The Search field became visible, but focus ownership was left to Compose spatial focus search. On a D-pad device this could leave focus on the Search icon instead of the new field.

Correction:
- opening Search on TV requests focus on the Search field after composition
- D-pad Up from the Search field returns focus to the Search trigger
- D-pad Down from Search returns to the current channel focus target when channels exist
- Down does not call an unattached channel `FocusRequester` when search results are empty

### 2. Browse and View Mode popup dismissal had no explicit focus return

`DropdownMenu` dismissal relied on default focus restoration. This is not sufficiently deterministic for TV remote navigation, especially after selecting an item that changes the channel projection or view mode.

Correction:
- added reusable `TvPopupFocusPolicy`
- popup open -> focus the currently selected item
- popup close -> restore focus to the popup trigger
- state is recorded before the frame wait so a fast dismiss cannot lose the restore transition
- behavior is opt-in; non-TV callers retain existing behavior

### 3. ESC could bypass popup dismissal

OwnPlay maps `KEYCODE_ESCAPE` to `onBackPressedDispatcher.onBackPressed()`. Relying only on platform popup key dismissal could therefore allow Live hierarchy Back handling to run while a toolbar popup was open.

Correction:
- Browse and View Mode register local TV `BackHandler`s while expanded
- ESC/Back dismisses the active popup before Live hierarchy navigation

### 4. Search Back handling needed to preserve Preview precedence

Correction:
- with no Preview active, ESC closes an actively opened Search and restores the Search trigger
- once a Preview becomes active, Search expansion is collapsed and Search no longer intercepts Back
- therefore the product rule remains intact: Preview + ESC -> close Preview; the next ESC can continue through the Live hierarchy

### 5. Toolbar Down could return to a stale channel target

The shared channel `FocusRequester` was attached to the externally requested channel, not necessarily the channel most recently reached by D-pad browsing. Returning from the toolbar could therefore jump back to the initial channel when no Preview was active.

Correction:
- `LiveChannelView` remembers the most recently focused channel locally
- the shared `FocusRequester` follows that channel across List, Compact, and Gallery/Cards
- after popup dismissal, D-pad Down from the toolbar returns to the last focused channel
- external category/filter fallback still overrides the target when the previous channel is no longer visible

## Back / ESC precedence after this pass

For Live TV toolbar states:

1. An open Browse/View Mode popup dismisses first.
2. Otherwise, an active Preview owns Back/ESC and closes first.
3. Otherwise, an actively opened Search closes and returns focus to its trigger.
4. Otherwise, Channels returns to Categories.
5. Category root propagates to normal app Back behavior.

This keeps transient UI local while preserving the explicit Preview-first rule in normal channel browsing.

## Regression coverage

Added deterministic unit-policy coverage for `TvPopupFocusPolicy`:
- popup open -> selected item focus action
- popup close after having been open -> trigger restore action
- idle/non-TV states -> no forced focus action

The row/card last-focused-channel behavior remains a Compose focus integration concern and requires TV runtime QA in addition to source review.

## Validation boundary

Source-level audit and post-mutation review were performed on this branch.

Executable Gradle validation has not run on this head. The connected GitHub toolset exposes workflow reads/re-runs but not the manual `workflow_dispatch` needed to start `Android Validation No APK`. The branch intentionally retains the `-no-apk` suffix, so standard PR Android CI must not assemble an APK.

Do not treat source review as physical-device PASS.

Still required before promotion:
- no-APK compile/unit/lint/AndroidTest compile validation on the exact head
- real TV/TV Box D-pad QA for Browse, View Mode, Search, List, Compact, Gallery, Preview, Fullscreen, and ESC transitions
- later update-compatible QA APK only when explicitly authorized

## Operational boundary

Draft/source work only. Do not merge, mark ready, deploy, release, publish, force-push, rewrite history, alter signing, or create an APK without explicit authorization.
