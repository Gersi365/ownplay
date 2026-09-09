# OwnPlay TV — From-Zero Rebuild Contract

## Status

This document records the explicit product decision to rebuild the OwnPlay TV presentation architecture from zero.

The rebuild applies to the Android TV application `app.ownplay.tv` only. It does not authorize a Mobile redesign.

## Source strategy

The previous TV presentation tree is not an implementation baseline for the rebuilt application.

The rebuild must:

- preserve Git history and use forward-only commits;
- replace the active TV presentation/execution path rather than reskinning old screens;
- avoid importing or routing into the previous TV shell, Home, Live, Movies, Series, Settings, or focus graph;
- keep shared provider, persistence, security, networking, Media3 playback, and other non-presentation infrastructure where reuse is technically appropriate;
- avoid unrelated shared/runtime refactors unless the rebuilt TV behavior genuinely depends on them.

The rebuild is not permission for force-push, reset, rebase, history rewrite, release, merge, signing changes, or APK generation.

## Visual direction

The rebuilt OwnPlay TV experience is media-first and inspired by the interaction qualities of modern Stremio-style television browsing, without copying Stremio branding, assets, source code, wording, or pixel-identical layouts.

OwnPlay remains visually and commercially distinct.

The new TV visual system uses:

- near-black / graphite backgrounds;
- restrained violet and purple emphasis;
- poster-first catalog browsing;
- strong artwork and metadata hierarchy;
- compact persistent navigation;
- stable focus geometry;
- color-driven focus states;
- large remote-friendly action surfaces;
- minimal, transient playback chrome;
- no touch-derived dense forms in primary TV presentation.

Focused elements must not scale, resize, change row height, or shift surrounding layout.

## Primary navigation

The rebuilt primary navigation is:

1. Home
2. Live TV
3. Movies
4. Series
5. Settings

This replaces older TV navigation structures such as `Live / Library / Settings`.

D-pad Right from the navigation rail must enter a deterministic content target. D-pad Left from the first logical content boundary must return to the active navigation destination.

## Home

Home is a media-first landing surface.

It may contain data-backed areas such as Continue Watching, Live TV gateways, Movies, Series, recent content, and other supported catalog projections. It must not fabricate provider media or metadata.

## Live TV

The durable Live behavior remains:

- activating a different channel opens Preview;
- activating the same previewed channel again opens Full View;
- Preview has no generic visible playback controls;
- selected-channel EPG belongs with Preview;
- Full View is video-first;
- EPG is the main transient Full View interaction layer;
- Back from Full View returns to Preview;
- Back from Preview returns to channel browsing.

Full View remote behavior remains:

- Channel Up → next channel;
- Channel Down → previous channel;
- OK → reveal EPG;
- Down → enter EPG timeline;
- Left/Right → browse programs;
- Up → leave timeline;
- ordinary D-pad Up/Down must not directly zap channels.

Same-channel Preview ↔ Full View must preserve the active playback session and maintain one player/controller and one active video destination.

## Movies and Series

Movies and Series use poster-first catalog surfaces and dedicated metadata/detail pages.

Fullscreen playback is video-first, uses predictable Back behavior, preserves resume progress, supports Play from beginning and Continue Watching where supported, and avoids permanent player chrome.

## Settings

The rebuilt Settings root remains:

1. Playlists
2. Live Management
3. Backup & Restore
4. About

Settings is TV-first, with stable rows, clear focus, OK-to-open navigation, logical Back behavior, and focus restoration to the originating item.

## Rebuild order

Implementation proceeds in this order:

1. shell, navigation, theme, and deterministic focus foundation;
2. source onboarding and Settings;
3. Live browse, Preview, Full View, and EPG;
4. Movies catalog, details, and playback integration;
5. Series catalog, details, episodes, and playback integration;
6. Home and Continue Watching integration;
7. physical-device QA and visual/ergonomic polish.

A stage may compile and validate before the complete product is feature-complete. Stage PASS must not be represented as physical-device PASS.

## Validation boundary

Routine rebuild work is source-only and no-APK.

After source mutations:

- run focused regression tests;
- run Android TV no-APK validation;
- preserve Room schema checks where relevant;
- explicitly verify APK count is zero;
- ensure validation belongs to exact final HEAD.

Physical-device QA remains required for D-pad focus, focus restoration, playback surfaces, Live Preview/Full View, EPG, background/foreground behavior, real video rendering, provider/network behavior, perceived performance, and television ergonomics.
