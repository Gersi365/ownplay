# OwnPlay Live TV Hierarchy / Preview Audit — 2026-08-30

## Scope

Focused build-phase implementation and regression-hardening pass for Live navigation on Android TV / TV Box, while preserving the existing Live playback engine, phone/tablet browse behavior, and no-APK boundary.

Authorized behavior implemented in this slice:

- TV Live opens at a category hierarchy level before channel browsing.
- Selecting a TV category opens its channels using the existing List / Compact / Cards browse modes.
- TV: first OK on a channel opens Preview.
- TV: second OK on the same channel opens Fullscreen.
- TV: OK on a different channel replaces Preview instead of opening Fullscreen.
- TV Preview does not expose playback/navigation/fullscreen/close buttons.
- TV Back/ESC precedence is Preview -> Categories -> normal app navigation.
- TV focus indication is strengthened for D-pad navigation.
- Phone/tablet retain the existing direct Live browse model and existing Back behavior.

## Authoritative repository baseline

- Repository: `Gersi365/ownplay`
- Parent branch: `agent/live-playback-restructure-no-apk`
- Parent HEAD: `ede2338801e0a804a41c5e51b7ca233a00c0b297`
- Parent exact-head no-APK validation evidence: Android Validation No APK run `33257736799` / run #32, SUCCESS.
- Focused branch: `agent/live-tv-hierarchy-preview-no-apk`
- Current implementation checkpoint before this report update: `c714ba5d516540472ae0916b11c6a3e17696a2b6`
- Relationship to parent at that checkpoint: ahead by 8 commits, behind by 0.

The focused branch was created directly from the exact validated parent HEAD so this slice remains isolated from PR #45 rather than broadening that PR's existing diff boundary.

## Audit findings before mutation

1. `LivePreviewPanel` explicitly forced `controlsVisible = true` on television devices and auto-focused the Fullscreen/Close control path. This conflicted with the requested TV preview behavior.
2. `LiveRoute.selectChannel` always routed channel activation to Preview; it had no TV-specific second-OK Fullscreen transition.
3. Live browsing rendered the category strip and channel list together rather than enforcing a TV category -> channels hierarchy.
4. Landscape Back handling could move focus from Preview/EPG back to the browser without closing Preview, allowing a later Back/ESC to escape the Live surface while Preview remained active.
5. TV focus already had a custom indication, but the persistent focus state used only a 3 dp outline.

## Post-mutation corrective findings

Two scope/focus risks were found during post-mutation audit and corrected before this checkpoint was accepted:

1. The first implementation applied the new hierarchy and Back handler to phone/tablet as well as TV. `LiveRoute` was corrected so hierarchy state, hierarchy Back ownership, and category-first presentation are TV-only. Phone/tablet again use the established direct `PortraitLiveBrowseWithViewModes` path.
2. The hierarchy wrapper could force a fallback channel focus when used by a non-TV landscape workspace. The focus fallback is now gated by television `uiMode` so non-TV focus behavior is not broadened by this slice.

The TV-only platform gate was then promoted into `LiveBrowseHierarchyPolicy` so the initial hierarchy level and Back ownership are deterministic and explicitly covered by unit tests rather than remaining only inline UI logic.

## Implementation boundary

Changed/added only Live UI/navigation policy, TV focus indication, and focused unit-test coverage. No changes were made to:

- Media3 / ExoPlayer engine choice
- player/decoder concurrency architecture
- Room entities or schema
- database migrations
- source credentials/authentication
- cross-device sync architecture
- downloads/offline architecture
- signing identity
- package/version identity
- production deployment/store publication

No APK is to be produced by this branch.

## Regression policies added

`LiveBrowseHierarchyPolicy` now makes the device and transition rules deterministic and unit-testable:

- TV initial Live hierarchy -> Categories.
- Non-TV initial hierarchy state -> Channels/direct browse semantics.
- Only TV owns Preview/category hierarchy Back handling.
- Preview open + Back/ESC -> close Preview.
- Channels level + no Preview + Back/ESC -> show Categories.
- Categories level + no Preview + Back/ESC -> propagate to normal app navigation.
- TV + no active Preview + channel OK -> open Preview.
- TV + same active Preview channel + channel OK -> open Fullscreen.
- TV + different channel OK -> replace Preview.
- Non-TV repeated channel activation keeps the existing Preview behavior.

## Current diff boundary

Compared with parent `ede2338801e0a804a41c5e51b7ca233a00c0b297`, the implementation checkpoint changes only:

- `app/src/main/java/app/ownplay/player/ui/LivePreviewPanel.kt`
- `app/src/main/java/app/ownplay/player/ui/LiveRoute.kt`
- `app/src/main/java/app/ownplay/player/ui/live/LandscapeLiveWorkspaceAdaptive.kt`
- `app/src/main/java/app/ownplay/player/ui/live/LiveBrowseHierarchy.kt`
- `app/src/main/java/app/ownplay/player/ui/live/LiveBrowseHierarchyPolicy.kt`
- `app/src/main/java/app/ownplay/player/ui/tv/TvRemoteIndication.kt`
- `app/src/test/java/app/ownplay/player/ui/live/LiveBrowseHierarchyPolicyTest.kt`
- this audit report

No Room schema file changed in this slice.

## Validation status

- Repository/source audit: PASS.
- Exact parent baseline identification: PASS.
- Diff boundary audit: PASS; 8 files, focused on Live/TV UI, policy, tests, and this report.
- Deterministic policy regression tests: ADDED, including TV-only hierarchy entry and Back ownership, but not executed on this new branch in the connected session.
- Static review of TV Preview control suppression: PASS at source level.
- Static review of TV second-OK routing and ESC precedence: PASS at source level.
- Static review that phone/tablet direct browse behavior is preserved: PASS at source level after corrective commit `93c5d310ff46d1ed39b9040069ee3b62e0dbca13`.
- Static review that hierarchy focus fallback is TV-only: PASS at source level after corrective commit `c716508d6f203a52a44f7f33c09523099b7fdca9`.
- TV-only hierarchy/Back policy wiring checkpoint: `c714ba5d516540472ae0916b11c6a3e17696a2b6`.
- Current commit combined CI statuses at the implementation checkpoint: none attached.
- Kotlin/Android compile, unit-test execution, lint, AndroidTest compile: NOT EXECUTED on this branch.
- APK build: NOT AUTHORIZED / MUST NOT RUN for this branch.
- Physical TV/TV Box QA: REQUIRED after a later explicitly authorized update-compatible QA APK exists.

The connected GitHub action set available to this implementation session does not expose a new `workflow_dispatch` action, so the repository's manual `Android Validation No APK` workflow cannot be started directly from this session. An isolated local checkout was also attempted, but the execution environment could not resolve `github.com`; this is an environment/tooling limitation, not source validation evidence and not a source defect.

## Required physical QA later

1. TV Live -> category list -> category -> channel list.
2. D-pad focus remains obvious while moving between categories and channels.
3. First OK on channel -> Preview appears and the channel retains practical browse focus.
4. Preview shows video/status only; no preview control bar/buttons on TV.
5. Move to another channel -> OK -> Preview switches channel.
6. OK again on the same previewed channel -> Fullscreen.
7. Fullscreen -> Preview transition remains reliable under the existing controlled-restart policy.
8. ESC from Preview -> Preview closes only.
9. ESC again from channel level -> returns to category level.
10. ESC again from category root -> normal app-level Back/exit-safety handling.
11. Phone/tablet Live browsing remains direct and does not acquire TV category-first or ESC semantics.

## Promotion boundary

Do not call this slice compile-validated or physically stable until the no-APK validation lane passes the exact head and a future update-compatible QA APK passes the TV transition matrix on real hardware.
