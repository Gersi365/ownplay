# OwnPlay Live TV Hierarchy / Preview Audit — 2026-08-30

## Scope

Focused build-phase implementation and regression-hardening pass for Live navigation on Android TV / TV Box, while preserving the existing Live playback engine and no-APK boundary.

Authorized behavior implemented in this slice:

- Live opens at a category hierarchy level before channel browsing.
- Selecting a category opens its channels using the existing List / Compact / Cards browse modes.
- TV: first OK on a channel opens Preview.
- TV: second OK on the same channel opens Fullscreen.
- TV: OK on a different channel replaces Preview instead of opening Fullscreen.
- TV Preview does not expose playback/navigation/fullscreen/close buttons.
- Back/ESC precedence is Preview -> Categories -> normal app navigation.
- TV focus indication is strengthened globally for D-pad navigation.

## Authoritative repository baseline

- Repository: `Gersi365/ownplay`
- Parent branch: `agent/live-playback-restructure-no-apk`
- Parent HEAD: `ede2338801e0a804a41c5e51b7ca233a00c0b297`
- Parent exact-head no-APK validation evidence: Android Validation No APK run `33257736799` / run #32, SUCCESS.
- New focused branch: `agent/live-tv-hierarchy-preview-no-apk`

The new branch was created directly from the exact validated parent HEAD so this slice is isolated from PR #45 rather than broadening that PR's existing diff boundary.

## Audit findings before mutation

1. `LivePreviewPanel` explicitly forced `controlsVisible = true` on television devices and auto-focused the Fullscreen/Close control path. This conflicted with the requested TV preview behavior.
2. `LiveRoute.selectChannel` always routed channel activation to Preview; it had no TV-specific second-OK Fullscreen transition.
3. Live browsing rendered the category strip and channel list together rather than enforcing a category -> channels hierarchy.
4. Landscape Back handling could move focus from Preview/EPG back to the browser without closing Preview, allowing a later Back/ESC to escape the Live surface while Preview remained active.
5. TV focus already had a custom indication, but the persistent focus state used only a 3 dp outline with no focused fill.

## Implementation boundary

Changed/added only Live UI/navigation policy, TV focus indication, and unit-test coverage. No changes were made to:

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

`LiveBrowseHierarchyPolicy` makes the requested transitions deterministic and unit-testable:

- Preview open + Back/ESC -> close Preview.
- Channels level + no Preview + Back/ESC -> show Categories.
- Categories level + no Preview + Back/ESC -> propagate to normal app navigation.
- TV + no active Preview + channel OK -> open Preview.
- TV + same active Preview channel + channel OK -> open Fullscreen.
- TV + different channel OK -> replace Preview.
- Non-TV repeated channel activation keeps the existing Preview behavior.

## Validation status at authoring time

- Repository/source audit: PASS.
- Exact parent baseline identification: PASS.
- Deterministic policy regression tests: ADDED.
- Kotlin/Android compile, unit-test execution, lint, AndroidTest compile: NOT YET EXECUTED on this new branch at the time this report was authored.
- APK build: NOT AUTHORIZED / MUST NOT RUN for this branch.
- Physical TV/TV Box QA: REQUIRED after a later explicitly authorized update-compatible QA APK exists.

The connected GitHub action set available to this implementation session does not expose `workflow_dispatch`, so the repository's manual `Android Validation No APK` workflow cannot be started directly from this session. This is an environment/tooling boundary, not a source PASS.

## Required physical QA later

1. Live -> category list -> category -> channel list.
2. D-pad focus remains obvious while moving between channels.
3. First OK on channel -> Preview appears and channel retains practical browse focus.
4. Preview shows video/status only; no preview control bar/buttons on TV.
5. Move to another channel -> OK -> Preview switches channel.
6. OK again on the same previewed channel -> Fullscreen.
7. Fullscreen -> Preview transition remains reliable under the existing controlled-restart policy.
8. ESC from Preview -> Preview closes only.
9. ESC again from channel level -> returns to category level.
10. ESC again from category root -> normal app-level Back/exit-safety handling.

## Promotion boundary

Do not call this slice physically stable until a future update-compatible QA APK is installed and the TV transition matrix above passes on real hardware.
