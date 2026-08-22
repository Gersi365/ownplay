# Phase 006 Slice G evidence — 2026-08-22

## Status

Slice G previous/next Live-channel navigation is implemented on `agent/phase-006-previous-next-no-ci`, stacked directly on the exact Android-CI-validated Slice F head.

This document records source shape and local pre-CI validation only. No Slice G pull request has been opened and Android CI has not been triggered for Slice G.

## Validated base

- Slice F validated head: `7226b11591cc0bbc981ffb5f8cae3fe346a146ec`
- Slice F Android CI: run #71 / `32580993282` — success

## Implementation commit

`2f9f04f9261df6c4038622b858b1eecfa770de34` — `Implement Phase 006 previous and next navigation`

Remote compare against validated Slice F reports:

- 1 commit ahead / 0 behind;
- exactly three changed files;
- `LivePlaybackFlow.kt`: +81 / -2;
- `OwnPlayApp.kt`: +44 / -2;
- `LivePlaybackFlowTest.kt`: +112 / -1;
- no Gradle dependency file, Media3 dependency/version, Room entity, DAO, schema, migration, or persistent personalization mutation.

All three candidate Git blobs were verified byte-for-byte against their local Git object hashes before the implementation tree was created.

## Active browse/order context

Slice G reuses the existing `PlaybackNavigationContext` contract instead of adding a second navigation model to the controller.

At normal channel selection time, OwnPlay captures an explicit immutable snapshot of the currently visible, already-projected Live list:

- only channels from the active source are retained;
- duplicate channel IDs are collapsed deterministically by first occurrence;
- list order is preserved exactly as supplied by `LiveBrowseState.channels`;
- only opaque channel identity plus normalized display name is retained;
- no stream locator, credential, provider URL, Room row, or personalization mutation handle enters the snapshot.

Because `LiveBrowseState.channels` is already the result of the active search/filter/order projection, previous/next resolution follows that explicit user-visible context. A filtered result can narrow playback navigation for the current session, but it does not rewrite provider order, `My Order`, favorite order, or any other persistent ordering field.

## Navigation semantics

`LivePlaybackBrowseContext.selectionFor(channelId)` creates a `PlaybackRequest` whose existing `PlaybackNavigationContext` contains only the immediate previous and next channel IDs from the captured snapshot.

`LivePlaybackSelection.navigate(direction)`:

- reads the target through `PlaybackRequest.navigationTarget(...)`;
- resolves that target against the same captured browse snapshot;
- creates the next `LivePlaybackSelection` with updated immediate neighbors;
- returns null at either edge rather than wrapping around;
- does not query or mutate persistence;
- does not rebuild the Live catalog or create another player/controller.

The browse snapshot is shared across generated selections, so channel switches preserve the exact captured order while avoiding repeated full-catalog copies.

## Runtime composition and UI

The existing player screen now exposes focused `Previous` and `Next` actions.

- buttons are enabled only when the current request has a neighbor in that direction;
- a switch updates `activeSelection`;
- the same existing `PlaybackController` receives `start(targetSelection.request)`;
- the route remains `OwnPlayRoute.Playback`, so unrelated app/player composition is not intentionally rebuilt;
- the current player/controller ownership from Slices C–F is unchanged;
- track-panel state is closed before a channel switch;
- fullscreen/resize UI state remains owned by the existing playback screen composition;
- returning to channels remains a separate explicit action.

The first controls row now contains Channels / Previous / Play-or-Pause / Next. Existing Tracks / resize / fullscreen controls remain on a second row to avoid unnecessarily crowding the phone control surface.

## Security and privacy

- `PlaybackRequest` still carries opaque source/channel identity, never a raw stream URL;
- captured browse-context `toString()` redacts source identity and renders only entry count;
- browse-entry `toString()` redacts channel identity;
- `LivePlaybackSelection.toString()` continues to rely on the redacted playback request;
- no URL, credential, locator descriptor, secure-store value, header, or query token is added to UI/navigation state.

## Tests and local pre-CI validation

Available local checks passed:

- `PHASE_006_SLICE_G_FLOW_COMPILE=PASS`
- `PHASE_006_SLICE_G_FLOW_SMOKE=PASS`
- `PHASE_006_SLICE_G_TEST_SHAPE_COMPILE=PASS`
- `PHASE_006_SLICE_G_UI_WIRING_SCAN=PASS`

The deterministic navigation tests cover:

- previous/next neighbors for a middle channel;
- edge behavior with null previous/next and no wrap-around;
- repeated navigation using the same captured snapshot;
- filtered/search-result snapshots not navigating into channels outside the visible result;
- rejection of channels from other sources;
- deterministic duplicate-ID collapse;
- redaction of opaque source/channel identity;
- edit-mode taps remaining edit-selection-only and never starting playback;
- legacy no-context selection behavior remaining valid.

These checks use local Kotlin plus narrow shape stubs where needed. They are not substitutes for real Gradle dependency resolution, Android lint, JVM tests, debug APK assembly, androidTest compilation, Room schema verification, or runtime device playback.

## Explicitly not included

- no Picture-in-Picture;
- no orientation/lifecycle work;
- no persistent browse-query snapshot;
- no `My Order` or favorite-order mutation;
- no wrap-around channel navigation;
- no VOD/series work;
- no new dependency;
- no Room/schema/migration change;
- no merge, release, deployment, or publication.

## Operational note

During the reconnect recovery, an accidental empty Git branch ref named `__noop__` was created pointing at the unchanged validated Slice F commit `7226b11591cc0bbc981ffb5f8cae3fe346a146ec`. It contains no unique commit, no source change, no PR, and no CI run, and it was not used by Slice G. The connected GitHub tool surface available in this session does not expose branch-ref deletion, so the ref is left inert rather than performing an unsafe workaround.

## Required next gate

Before Slice H, open one draft Slice G pull request stacked on `agent/phase-006-track-selection-no-ci` and run exactly one established full Android CI gate on the exact current Slice G head. A separate explicit authorization is required before that PR/Actions step.
