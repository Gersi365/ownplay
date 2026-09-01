# OwnPlay — Separate Mobile and TV APK Specification

Date: 2026-08-31
Status: Authoritative rebuild target

## Product boundary

OwnPlay is rebuilt as two independent Android APK targets over a shared media/core layer:

- `mobile` — phone/tablet, touch-first
- `tv` — Android TV / Google TV / TV Box, D-pad-first

The two APKs must not rely on runtime device-profile selection to decide which primary presentation model to expose.

Shared core may include playlist ingestion, Room persistence, reconciliation, Media3 playback, EPG data, personalization models, and other non-presentation logic that is already validated and does not force one target's UX onto the other.

## Branding contract

- The user-visible application/product name is exactly `OwnPlay` on both targets.
- `mobile` and `tv` are internal build-target identifiers only; they must not be appended to the launcher/application label.
- Launcher icon and TV banner must follow the current dark-purple visual system and must not embed `Mobile` or `TV` as part of the OwnPlay brand name.

## Retired scope

Cross-device Device Sync / device pairing is retired from the OwnPlay product scope. It is not a current objective, roadmap item, or implementation backlog.

- Do not expose a Device Sync destination, pairing flow, or cross-device synchronization action in the current product UI.
- `SourceSyncState` and source refresh status refer to playlist/source ingestion and refresh; they are unrelated to the retired cross-device Device Sync objective.
- Historical internal sync/schema-compatibility code may remain dormant where removing it would require unrelated database/architecture churn. Its presence must not be interpreted as pending product work.

## Global navigation contract

Visible primary navigation is exactly:

- Live
- Library
- Settings

Movies and Series are internal Library sections/routes. They are never primary destinations.

No visible aggregate `All` is allowed in Live, Movies, or Series. No visible `All categories` is allowed.

## Mobile APK

### Target

- Android phone and tablet
- touch-first
- portrait and landscape
- application id: `app.ownplay.mobile`

### Primary navigation

Bottom/app navigation exposes only:

- Live
- Library
- Settings

### Library

Visible Library sections:

- Offline
- Movies
- Series

Rules:

- Offline/download presentation is Mobile-only.
- Movies and Series use real provider categories only.
- If a category selection becomes invalid after hydration/refresh, fall back to the first real provider category.
- Never expose `All` or `All categories`.

### Live

- Category information must not be drawn over or inside the channel logo/icon.
- Categories and channels must be visually distinct.
- Channel rows/cards show channel identity first; EPG/current-program metadata may update without changing row geometry.
- Selecting a channel opens Preview with EPG/current-next information.
- Preview has no generic visible playback controls.
- Fullscreen is media-first and may reveal lightweight EPG when requested.
- Adaptive HLS/DASH behavior and bounded retry are defaults; no Network settings section is added.

### Movie details

- Play/Resume is the primary action.
- When details opens, the usable primary action must be immediately accessible.
- Back hierarchy: Playback -> Movie Details -> previous Movie category/catalog -> Library.
- Returning to catalog restores the real category and item context where practical.
- Download/Offline actions are allowed on Mobile.

### Series details

- Hierarchy: Series -> Season -> Episode.
- Play/Resume is the primary episode action.
- Back hierarchy: Playback -> Episode -> Season -> Series -> previous Series category/catalog -> Library.
- Download/Offline actions are allowed on Mobile.

### Visual direction

- dark, calm, professional, media-first
- primary accent `#9B7BFF`
- background `#080A0F`
- surface `#0D1016`
- surface variant `#151922`
- flat 8–12dp surfaces
- selected/active state expressed primarily by color, not geometry changes
- no flashy/neon IPTV styling

## TV APK

### Target

- Android TV / Google TV / TV Box
- D-pad/remote first
- landscape
- application id: `app.ownplay.tv`
- no PiP requirement

### Primary navigation

Visible primary navigation exposes only:

- Live
- Library
- Settings

### Library

Visible Library sections:

- Movies
- Series

Rules:

- No Offline/Download presentation on TV.
- No `All` or `All categories`.
- Real provider categories only.
- Initial focus and focus restoration must target real actionable content, not Back.

### Live hierarchy

Exact browsing hierarchy:

1. Categories
2. Channels
3. Preview + EPG
4. Fullscreen

Rules:

- No `All channels`.
- First/selected real provider category is the category fallback.
- Enter category -> Channels.
- Enter channel -> Preview.
- Preview has no generic controls.
- OK from Preview -> Fullscreen.
- Back/ESC from Preview -> Channels.
- Back/ESC from Channels -> Categories.

### Fullscreen EPG

- channel open/change shows EPG temporarily
- OK reveals EPG
- Down enters EPG timeline when data is available
- Left/Right navigate programmes
- Up exits timeline
- Full EPG remains lightweight
- leaving Full EPG returns to Preview
- no generic playback menu

### Movie details

- initial focus is Play/Resume, never Back
- Back hierarchy: Playback -> Movie Details -> previous Movie category/catalog -> Library
- no Download/Offline presentation

### Series details

- Series root initial focus -> first season when available
- Season initial focus -> Play/Resume of first actionable episode
- Episode Details initial focus -> Play/Resume
- Back hierarchy: Playback -> Episode -> Season -> Series -> previous Series category/catalog -> Library
- no Download/Offline presentation

### Focus contract

- focus is always visible
- focus/selected state primarily changes color
- do not change card size, shape, font weight, or layout geometry as the primary focus affordance
- restore focus after returning from details/playback where practical

### Reliability contract

TV physical QA is a release blocker.

Required P1 behavior:

- zero app/device restart
- zero freeze
- zero black/stuck Surface
- zero duplicate audio

Preview <-> Fullscreen remains reliability-first:

- one player/one decoder ownership model
- no parallel active video decoders
- do not depend on unsafe seamless Surface transfer for correctness
- controlled stop/detach/restart handoff is acceptable when needed for device reliability

## Settings

Settings contain real configuration/actions only.

Mobile may expose Downloads management.
TV must not expose Downloads/Offline management.

Do not add a Network settings section. Playback/network resilience that is safe should be the default.

## Build and QA gates

Each target must compile and validate independently:

- KSP
- unit tests
- lint
- AndroidTest Kotlin compilation

Before final APK acceptance:

- physical phone QA for Mobile
- physical TV/TV Box QA for TV
- exact-candidate audit

Old universal/update-compatible APK requirements are retired for this rebuild. Fresh installation is expected; version continuity with previous APKs is not a requirement.
