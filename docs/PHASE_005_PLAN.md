# Phase 005 — Channel Personalization

## Status

Implementation started on `agent/phase-005-channel-personalization`, stacked on the final validated Phase 004 branch.

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

The first slice is pure Kotlin and intentionally does not touch Room schema or UI.

It provides deterministic plans for:

- moving one channel to a final target index
- moving a selected set to the top
- moving a selected set to the bottom
- preserving relative order of selected and unselected channels
- assigning contiguous `Long` manual-order positions
- failing explicitly on blank IDs, duplicate current IDs, missing channels, invalid target indices, and invalid bulk selections

The planner consumes the full resolved channel order. Search/filter state must never silently become the persistent ordering input; later UI wiring must invoke ordering only from an explicit edit/My Order context.

## Persistence safety for Slice B

The existing `ChannelCustomizationEntity` also stores local display name and logo override. Persistence work must therefore update `manualOrder` without nulling or overwriting those independent customization fields.

Slice B will introduce the narrow persistence adapter and tests for restart-safe manual ordering without adding a schema migration unless implementation evidence proves one is necessary.

## Planned later slices

- Slice B: manual-order persistence adapter
- Slice C: hide/unhide operations and hidden-channel management contract
- Slice D: favorite mutation and favorite manual order
- Slice E: local rename and logo-override mutation
- Slice F: custom groups and membership operations
- Slice G: bulk edit orchestration
- Slice H: Compose edit mode and drag/drop wiring

Each slice must pass the existing unit-test, lint, build, androidTest compile, and Room schema-drift gates before the next persistence/UI layer is added.
