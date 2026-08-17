# Phase 004 — Live Channel Browsing

## Status

Implementation complete and validated on `agent/phase-004-live-browsing`, stacked on the validated but unmerged Phase 003 branch.

Phase 004 remains a draft PR and is not authorized for merge until the user explicitly approves it.

## Authoritative scope

Phase 004 implements the Live browsing foundations required by the product source specification:

- channel list read path
- provider categories
- search
- filtering
- favorites visibility
- recent-channel history and recently-watched ordering
- initial Xtream/M3U Live catalog ingestion into local persistence
- compact phone-first Compose browsing UI

Playback, drag-and-drop editing, bulk personalization, EPG, movies/series, backup/restore, and release work remain outside Phase 004.

## Slice A — Read model and deterministic projection

Implemented:

- `LiveBrowseDao` joins provider channels, categories, local customization, favorites, hidden state, and later recent history
- reactive `LiveCatalogRepository`
- pure `LiveBrowseProjector`
- text search
- category filtering
- favorite filtering
- hidden/removed visibility controls
- Provider order, My Order, Favorite order, A-Z, Z-A, Category, and Recently watched modes
- unit tests proving browsing filters do not mutate persistent ordering

The browse read model deliberately does not expose stream locator values. Channel selection uses local `channelId`; playback resolution remains a Phase 006 responsibility.

## Slice B — Browse session state

Implemented `LiveBrowseSession` as a small Flow-based state layer for transient browsing state:

- search text
- selected category
- favorites-only mode
- visibility flags
- temporary ordering mode

This layer remains independent of Android ViewModel APIs so its state transitions can be tested directly on the JVM.

## Slice C — Phone-first Compose UI

Implemented a stateless `LiveBrowseScreen` with:

- Live header and result count
- search field
- category chips
- favorites toggle
- ordering menu
- compact channel rows
- filtered and unfiltered empty states

The UI does not simulate playback. Channel taps expose only the selected local `channelId` for later Phase 006 wiring.

## Slice D — Initial Live catalog ingestion

Implemented a narrow ingestion boundary from validated source data into local persistence:

- Xtream live categories/streams -> incoming Live catalog
- M3U entries/groups -> incoming Live catalog
- deterministic provider identities and stable local IDs
- encrypted/opaque storage references for stream locators and logo locator values
- reconciliation against existing provider channel identities
- transactional Room catalog writes
- rollback cleanup of newly allocated sensitive-value references on persistence failure
- redacted rendering for sensitive locator values

This is initial catalog ingestion, not the full provider refresh lifecycle. Removed/unavailable lifecycle semantics and full refresh reconciliation remain Phase 009.

## Slice E — Recent channel history and Room schema v2

Implemented bounded recent-channel persistence:

- `RecentChannelEntity`
- maximum retained history of 20 entries
- most-recent-first observation
- `Recently watched` browsing order
- foreign-key cascade to provider channels
- additive Room migration `1 -> 2`
- migration harness proving existing provider rows survive the migration

Room schema version 2 is committed under `app/schemas/` with the compiler-generated identity hash. Schema comparison in CI is JSON-semantic so formatting normalization cannot hide or manufacture structural drift.

## Validation evidence

Final implementation validation before this documentation update:

- GitHub Actions run: `32009171911` (`Android CI` run #42)
- head: `8be592d5fa037731d7c99d8de6f89a2227d0ed10`
- unit tests: success
- Android lint: success
- debug APK assembly: success
- androidTest compilation / migration harness compilation: success
- Room schema artifact upload: success
- committed Room schema semantic drift gate: success
- debug APK artifact upload: success

A documentation-only follow-up commit must also receive a green CI run before the PR is considered final-head validated.

## Deferred boundaries

- manual ordering mutations and drag-and-drop: Phase 005
- hide/unhide editing: Phase 005
- favorite mutation and favorite reordering: Phase 005
- local rename/logo override editing: Phase 005
- custom-group editing and bulk actions: Phase 005
- playback and recent-history recording from actual playback: Phase 006
- EPG: Phase 008
- full provider refresh/reconciliation lifecycle: Phase 009
