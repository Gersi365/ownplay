# Phase 005 — Channel Personalization

## Status

Implementation in progress on `agent/phase-005-channel-personalization`, stacked on the final validated Phase 004 branch.

This phase remains draft work. It must not be merged ahead of Phase 003 and Phase 004, and no merge is authorized without explicit user approval.

## Authoritative scope

Phase 005 is limited to channel personalization:

- manual drag-and-drop ordering
- persistent custom order
- hide/unhide
- bulk editing
- local rename
- custom groups
- favorite ordering

Playback remains Phase 006. Provider refresh lifecycle remains Phase 009.

## Slice A — Deterministic manual-order planning

Implemented and CI-validated as pure Kotlin without Room/schema changes.

The planner supports:

- moving one channel to a final target index
- moving a selected set to the top
- moving a selected set to the bottom
- preserving relative order of selected and unselected channels
- assigning contiguous `Long` manual-order positions
- failing explicitly on blank IDs, duplicate current IDs, missing channels, invalid target indices, and invalid bulk selections

The planner consumes the full resolved channel order. Search/filter state must never silently become the persistent ordering input; later UI wiring must invoke ordering only from an explicit edit/My Order context.

Slice A validation: Android CI run `32010251122` (#44) completed successfully with unit tests, lint, debug build, androidTest compilation, schema artifact/gate, and APK upload.

## Slice B — Transactional manual-order persistence

Slice B adds a narrow Room-backed mutation boundary without changing schema v2.

Design constraints:

- load resolved source order inside the same Room transaction as the write
- plan the mutation against that transaction snapshot
- preserve `localDisplayName` and `logoOverrideRef` while updating `manualOrder`
- create customization rows only when a channel has no existing local customization row
- return explicit planner rejection separately from persistence failure
- rethrow coroutine cancellation rather than converting it into a persistence error
- deterministic fallback ordering uses provider order and channel ID when manual positions are absent/tied

Unit tests cover the customization merge so manual-order persistence cannot silently null independent rename/logo values.

## Planned later slices

- Slice C: hide/unhide operations and hidden-channel management contract
- Slice D: favorite mutation and favorite manual order
- Slice E: local rename and logo-override mutation
- Slice F: custom groups and membership operations
- Slice G: bulk edit orchestration
- Slice H: Compose edit mode and drag/drop wiring

Each slice must pass the existing unit-test, lint, build, androidTest compile, and Room schema-drift gates before the next persistence/UI layer is added.
