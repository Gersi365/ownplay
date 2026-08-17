# Phase 004 — Live Channel Browsing

## Status

Implementation started on `agent/phase-004-live-browsing`, stacked on the validated but unmerged Phase 003 branch.

## Authoritative scope

Phase 004 is limited to Live browsing foundations:

- channel list read path
- provider categories
- search
- filters
- favorites visibility
- recent-channel integration when its concrete persistence contract is introduced

Playback, drag-and-drop editing, bulk personalization, EPG, movies/series, backup/restore, and release work are outside this phase slice.

## Slice A — Read model and deterministic projection

This slice adds:

- `LiveBrowseDao` with Room joins across provider channels, categories, local customization, favorites, and hidden state
- a reactive `LiveCatalogRepository`
- a pure `LiveBrowseProjector` for search, category filtering, visibility filtering, favorite filtering, and temporary sorting
- explicit provider order, My Order, favorite order, A-Z, Z-A, and category order modes
- tests proving search/filter do not mutate stored ordering and unordered new channels remain after established manual ordering

## Persistence boundary

Slice A must not change the Room schema. It only adds DAO read queries and pure projection logic.

The Phase 003 schema-drift guard therefore acts as a regression gate: if Slice A accidentally changes schema v1, CI must fail.

## Sensitive data boundary

The browse read model deliberately does not expose the encrypted stream locator reference. Channel selection uses local `channelId`; playback resolution belongs to the later player boundary.

## Deferrals

- Recent-channel persistence and ordering require a concrete history model and may justify a later schema version in this consuming phase.
- UI composition and ViewModel state are separate slices after the data read path is validated.
- Channel editing remains Phase 005.
- Playback remains Phase 006.
