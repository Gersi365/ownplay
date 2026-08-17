# Phase 005 — Channel Personalization

## Status

Implementation is substantially complete at the domain, persistence, browse-model, and stateless Compose-screen boundaries.

Validated Phase 005 work lives on `agent/phase-005-channel-personalization`, stacked on the final Phase 004 branch. Additional audit-only hardening is isolated on `agent/phase-005-continuation-no-ci` while GitHub Actions minutes are exhausted.

This phase remains draft work. It must not be merged ahead of Phase 003 and Phase 004, and no merge is authorized without explicit user approval.

## Authoritative scope

Phase 005 is limited to channel personalization:

- manual drag-and-drop ordering
- persistent custom order
- hide/unhide
- bulk editing
- local rename
- local logo override
- custom groups
- favorite ordering

Playback remains Phase 006. Provider refresh lifecycle remains Phase 009.

## Slice A — Deterministic manual-order planning

Implemented and CI-validated as pure Kotlin without Room/schema changes.

The planner supports:

- moving one channel to a final target index
- moving a channel before or after an anchor channel
- moving a selected set to the top
- moving a selected set to the bottom
- preserving relative order of selected and unselected channels
- assigning contiguous `Long` manual-order positions
- failing explicitly on blank IDs, duplicate current IDs, missing channels, invalid target indices, and invalid bulk selections

The planner consumes the full resolved channel order. Search/filter state must never silently become the persistent ordering input; UI ordering is exposed only from explicit edit/My Order context.

Slice A validation: Android CI run `32010251122` (#44) completed successfully with unit tests, lint, debug build, androidTest compilation, schema artifact/gate, and APK upload.

## Slice B — Transactional manual-order persistence

Implemented without a schema change.

The mutation boundary:

- loads resolved source order inside the same Room transaction as the write
- plans against that transaction snapshot
- preserves `localDisplayName` and `logoOverrideRef` while updating `manualOrder`
- creates customization rows only where needed
- returns planner rejection separately from persistence failure
- rethrows coroutine cancellation instead of converting it into a persistence result
- relies on deterministic provider-order/channel-ID fallback when manual positions are absent or tied

Unit tests cover customization merging so manual-order persistence cannot silently clear independent rename/logo values.

## Slice C — Hide/unhide

Implemented as source-scoped transactional mutations.

Requested channel IDs are validated against the source-resolved order before hidden rows are changed. Empty selections, blank IDs, missing channels, invalid timestamps, cancellation, and persistence failures are distinguished.

## Slice D — Favorites and favorite order

Implemented with:

- source-scoped add/remove favorite mutations
- deterministic favorite ordering
- single favorite moves
- selected favorites to top/bottom
- stable favorite-entry planning

Favorite state remains a local personalization overlay.

## Slice E — Local rename and logo override

Implemented through `ChannelCustomizationMutator` and `ChannelCustomizationPatcher`.

Local display names are normalized and stored independently from provider names. Logo override values are stored behind opaque secure-value references instead of directly in Room.

Audit hardening on `agent/phase-005-continuation-no-ci` additionally preserves `CancellationException` if secure logo storage throws during `put`, matching the cancellation contract used by the other mutation paths.

## Slice F — Custom groups

Implemented with:

- create, rename, and delete group operations
- deterministic group ordering
- add/remove channel membership
- membership planning that preserves stable ordering
- source validation before membership mutation

## Slice G — Bulk edit orchestration

Implemented through the edit-state reducer, selection validator, and bulk-action executor.

Supported bulk actions:

- hide
- unhide
- favorite
- unfavorite
- move to top
- move to bottom
- add to custom group
- remove from custom group

## Slice H — Compose edit mode and drag/drop

Implemented at the stateless `LiveBrowseScreen` boundary.

The screen supports:

- explicit edit mode
- multi-select and select-visible
- bulk action controls
- My Order activation when entering edit mode
- long-press drag targeting
- before/after drop intent
- callback-based relative-order mutation
- hidden/favorite status display and custom-group filtering

The deterministic drag-target resolver commit `b3a35191328fdd898ab9c5c972ce56b9728be7d0` passed Android CI run `32017524004` (#56). The latest drag/drop UI commit is `b7d237025b4218fde93abbf2da172624f40755f7` and remains outside a completed CI run because the Actions quota was exhausted immediately afterward.

Application-level runtime composition is still intentionally separate: `MainActivity` remains the simple project shell, while Live browse and personalization are stateless/repository boundaries ready for later app-navigation and orchestration wiring.

## Validation boundary while Actions quota is exhausted

GitHub Actions usage reached the included monthly limit on 2026-08-17. No further Actions run should be intentionally triggered until the quota resets or billing policy is explicitly changed.

The latest Phase 005 workflow run, `32018080374` (#57), is recorded as failed but contains zero executed steps and no job log. It therefore does not establish a source-code build failure. The current final drag/drop head must be treated as not yet CI-validated.

To continue without generating Actions usage, audit-only changes are being committed to `agent/phase-005-continuation-no-ci`, which has no pull request and is not covered by the workflow's `push` trigger (`main` only).

Before Phase 005 can be considered final:

1. Re-run the full existing validation gate after Actions capacity is available: JVM unit tests, Android lint, debug assembly, androidTest compilation, Room schema generation/drift check, and APK artifact generation.
2. Validate the continuation hardening commit together with the Phase 005 head.
3. Inspect any new failures before updating the Phase 005 draft PR.
4. Do not merge without explicit user approval.
