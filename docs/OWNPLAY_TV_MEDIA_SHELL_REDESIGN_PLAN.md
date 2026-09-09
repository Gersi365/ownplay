# OwnPlay TV — Media Shell Redesign Plan

## Role

Durable implementation roadmap for the approved OwnPlay TV redesign. Read this file at the start of any future redesign chat.

This file defines **product intent and implementation order**, not current repository state. Always verify the authoritative branch, exact HEAD, active TV path, tests, and CI live from GitHub before changing code.

The redesign is TV-only. Preserve existing repositories, Room/DataStore semantics, source handling, playback controller, Live playback ownership, EPG behavior, favorites, progress, and supported personalization unless a later verified defect requires a scoped change.

---

# 1. Approved Product Contract

Primary navigation is exactly:

1. **Home**
2. **Live TV**
3. **Movies**
4. **Series**
5. **Settings**

Not part of primary TV navigation:

- Search
- Discover
- Library

Movies and Series are separate first-class destinations.

Movies/Series do not use a generic `All` category chip. Use real provider categories. `All Channels` may remain in Live because it is a real aggregate channel view.

---

# 2. Navigation Rail Contract

## Initial state

- App launch: rail **expanded**.
- Home is active and focused.
- Expanded rail shows icon + label.

## Enter content

From a focused rail item, D-pad Right:

1. transfers focus to the destination content entry target;
2. rail loses focus;
3. rail collapses;
4. active destination remains visibly selected in icon-only form.

OK activates a rail destination but does not by itself force collapse. Collapse follows content focus ownership.

## Return to rail

D-pad Left from the **left boundary** of content:

1. transfers focus to rail;
2. rail expands;
3. focus lands on the active destination.

Left inside a row/grid must navigate content normally until the left boundary. Do not globally intercept every Left press.

## Geometry

Preferred shell strategy:

- reserve collapsed rail footprint;
- expanded rail overlays/extends above content;
- do not resize/reflow poster rows when rail expands;
- focused cards never scale;
- active destination and focused control are separate states.

---

# 3. Real Capability Boundary

Only ship presentation backed by real OwnPlay data.

## Movies data available

- provider categories
- name / poster
- provider rating when supplied
- added timestamp when supplied
- favorite state
- playback position/duration/completion
- Continue Watching
- details description/backdrops/release date/duration/genre/country/director/cast/rating/trailer metadata when supplied

## Series data available

- provider categories
- name / poster / description / rating when supplied
- last-modified timestamp when supplied
- favorite state
- episode Continue Watching
- details metadata
- seasons / episodes
- episode progress

## Live data already available

- categories/groups
- channels
- current/next EPG where available
- Preview
- Full View
- local personalization/ordering/hide/custom groups

## Do not make redesign dependencies

- recommendation engine
- popularity/trending service
- external ratings/reviews
- cast-card browsing
- mandatory trailers
- external metadata service
- new account/auth backend
- provider-independent "Recently Added" when timestamps are absent

Optional metadata may be shown only when it exists; screen layout must remain complete without it.

---

# 4. Target Screen Set

Baseline screens/presentations:

1. Media shell / navigation rail
2. Home
3. Live browse + Preview
4. Live Full View + transient EPG
5. Movies browse
6. Movie Details
7. Existing Movie playback presentation
8. Series browse
9. Series Details / seasons / episodes
10. Existing Series episode playback presentation
11. Settings root
12. Playlists
13. Live Management
14. Backup & Restore
15. About
16. Existing Settings dialogs/file picker/text input flows

No standalone Search, Discover, or Library screen.

---

# 5. Home — Baseline

Keep Home useful and cheap to implement.

Sections:

- **Continue Watching** from real progress data
- **Movies** from active movie catalog
- **Series** from active series catalog
- optional Live highlight only if it can use current real Live data without new backend work

Hero is optional but allowed when data-backed.

Hero fallback order:

1. relevant Continue Watching item
2. deterministic Movie/Series item
3. branded empty-state background

Never invent metadata.

Content-entry focus from Home rail:

1. first Continue Watching item
2. else first Movie
3. else first Series
4. else meaningful empty-state action

Left from first content column returns to Home rail item.

---

# 6. Live TV — Preserve the Proven Contract

Do not change:

- different channel OK -> Preview
- same previewed channel OK again -> Full View
- Preview has no visible playback controls
- Preview does not unnecessarily steal browse focus
- selected-channel EPG belongs with Preview
- Full View is video-first
- OK reveals EPG in Full View
- Down enters EPG timeline
- Left/Right browse programs
- Up leaves timeline
- CH+ / CH- zap channels
- ordinary D-pad Up/Down do not zap
- Back: Full -> Preview -> browse
- same-channel Preview <-> Full preserves playback session

Target browse composition:

- category column
- channel column
- Preview + Now/Next EPG region

Use real channel/category/EPG data only. No ornamental "popular channels" shelf required.

Rail is visible only at browse root. Full View hides rail. Deeper Live layers own Back before shell navigation.

---

# 7. Movies — Baseline

Movies is a dedicated primary destination built on current VOD runtime/repository.

Browse UI:

- provider category strip/list
- Continue Watching when non-empty
- movie poster rows/grid
- optional Favorites when non-empty and useful

No generic `All`, `Trending`, or recommendation shelves.

Category policy:

- restore current valid category on return
- otherwise first real provider category in stable order
- if no categories exist, show catalog directly without inventing `All`

Focus:

- stable poster size
- color/outline emphasis only
- scrolling follows focus
- OK opens details
- Back from details restores originating poster

Movie Details baseline:

- backdrop/poster when available
- title
- release date/year when available
- genre when available
- duration when available
- description when available
- provider rating only if already useful/available
- Continue/Resume when progress exists
- Play from Beginning
- Add/Remove Favorite

Not baseline requirements:

- cast cards
- reviews
- external scores
- recommendation row
- trailer UI

---

# 8. Series — Baseline

Browse UI:

- provider category strip/list
- Continue Watching episode/series context when non-empty
- series poster rows/grid
- optional Favorites when non-empty and useful

No generic `All`, `Trending`, or recommendation shelves.

Use the same deterministic category policy and stable focus geometry as Movies.

Series Details baseline:

- backdrop/poster when available
- title
- release date/year when available
- genre when available
- description when available
- Add/Remove Favorite
- Continue Watching episode when progress exists
- Start from Episode 1
- season selector
- episode list
- episode number/title/duration when available
- progress for relevant episode

Back restores originating Series poster.

---

# 9. Settings — Reuse Stabilized Work

Do not redesign Settings from scratch again.

Keep root destinations exactly:

1. Playlists
2. Live Management
3. Backup & Restore
4. About

Integrate the existing TV-first Settings flow into the new shell and visual language.

Preserve existing focus restoration and mutation hardening.

At Settings root, Left from the shell boundary returns to Settings rail item and expands rail. Deeper Settings pages/dialogs own Back first.

---

# 10. Visual System

Direction:

- dark navy/charcoal
- OwnPlay blue-purple accent
- provider posters/backdrops where available
- restrained gradients for text legibility
- clear TV typography hierarchy
- stable geometry
- large remote-safe focus targets

Focus uses:

- background/tint change
- subtle fixed-width outline/glow if useful
- brighter icon/title color

Never use focus-only:

- scale
- padding change
- card dimension change
- row height change
- typography metric change

Missing artwork must use deterministic OwnPlay placeholders without collapsing layout.

---

# 11. Architecture Boundary

This is a **TV presentation architecture replacement**, not a data/playback rewrite.

Preserve unless specifically justified:

- `OwnPlayAppRuntime`
- playlist/source repositories
- Xtream/M3U parsing and credentials
- Room schema/entities
- DataStore
- WorkManager infrastructure
- VOD/Series repositories
- playback controller
- Live presentation session
- on-demand presentation session
- EPG data/request layer
- favorites
- playback progress
- personalization/backup format

Shared code changes require a genuine TV dependency, not aesthetic symmetry.

---

# 12. Preferred TV Code Organization

Keep `TVOwnPlayApp.kt` as shell orchestration, not a new monolith.

Preferred structure when extraction is justified:

```text
app/src/tv/java/app/ownplay/player/ui/
  TVOwnPlayApp.kt
  shell/
    TvDestination.kt
    TvNavigationRail.kt
    TvMediaShell.kt
    TvShellFocusBridge.kt
  home/
    TvHomeScreen.kt
  movies/
    TvMoviesScreen.kt
    TvMovieDetailsScreen.kt   # only if needed
  series/
    TvSeriesScreen.kt
    TvSeriesDetailsScreen.kt  # only if needed
```

Reuse existing routes/components where they already meet the contract. Do not refactor repositories merely for folder symmetry.

---

# 13. Implementation Sequence

Each production phase is forward-only and should end with focused tests + exact-HEAD no-APK validation before moving on.

## Phase 1 — Shell foundation

Goal: new rail and destination model with minimal content changes.

Work:

- add `TvDestination`: Home, Live TV, Movies, Series, Settings
- add expandable/collapsible rail
- add shell content host
- remove active `TVPrimaryNavigationBar`
- add explicit rail/content focus bridge
- initial expanded Home focus
- Right -> content/collapse
- Left-boundary -> rail/expand

Tests:

- exact destination order
- Home initial state
- rail expanded policy
- Right handoff
- Left-boundary restoration
- active destination remains visible when collapsed
- no Search/Discover/Library destination

## Phase 2 — Direct Movies / Series routing

Goal: remove Library from TV routing without rebuilding catalogs yet.

Work:

- Movies -> existing VOD route
- Series -> existing Series route
- preserve on-demand presentation session
- remove Library route from active TV shell
- preserve details/playback Back behavior

Tests:

- Movies/Series reachable independently
- details return context
- playback Back safe

## Phase 3 — Home baseline

Work:

- observe existing Movie/Series catalogs
- combined real Continue Watching presentation
- simple Movies row
- simple Series row
- optional real-data hero
- focus entry/return
- empty source state

No recommendation/trending infrastructure.

## Phase 4 — Live shell integration

Work:

- align Live browse with new shell styling
- keep category/channel/Preview structure
- rail focus only at browse root
- Full View hides rail
- no playback continuity changes unless defect evidence exists

Regression focus:

- different channel Preview
- same channel Full
- Full -> Preview -> browse
- EPG keys
- CH+/CH-
- Back ownership
- no generic controls

## Phase 5 — Movies browse presentation

Work:

- provider categories
- no generic All
- Continue Watching
- poster rows/grid
- optional Favorites
- deterministic focus restoration

Avoid details/network calls on every focus movement unless cached and cheap.

## Phase 6 — Movie Details presentation

Implement only Section 7 baseline metadata/actions. Preserve playback/progress semantics.

## Phase 7 — Series browse presentation

Implement provider categories, Continue Watching, catalog and optional Favorites with independent Series focus state.

## Phase 8 — Series Details presentation

Implement clean header + Continue + seasons + episodes. Preserve episode progress/resume/favorites/playback.

## Phase 9 — Settings shell integration

Keep stabilized Settings behavior; only adapt shell boundary and visual language. Do not reopen solved focus logic without evidence.

## Phase 10 — Empty/loading/error states

Cover:

- no source
- disabled source
- empty categories
- empty movies
- empty series
- no Continue Watching
- missing artwork
- refresh failure/warning
- missing Live EPG

Each state needs a safe Back path and, where possible, one obvious useful action.

## Phase 11 — Visual consistency pass

Only after focus/navigation is stable:

- typography
- spacing
- rail sizes
- poster sizes
- backgrounds/gradients
- placeholders
- focus colors
- loading indicators

No geometry-changing focus animation.

## Phase 12 — Regression saturation

Protect:

- destination order
- rail expand/collapse ownership
- rail/content boundary
- Home focus restore
- Movies category/item restore
- Series category/item restore
- details returns
- Live hierarchy
- Settings origin restore
- no TV Offline/Downloads
- no Search/Discover/Library primary destination

Run exact-HEAD no-APK validation.

## Phase 13 — Physical QA candidate

Only after explicit APK authorization:

- monotonic TV versionCode if needed
- exact source candidate
- package `app.ownplay.tv`
- established signer
- TV-only build
- verify package/version/signature/signer/SHA-256
- install as update where possible

CI/build success is not physical PASS.

---

# 14. Per-Phase Checklist

Before production mutation:

1. read this plan
2. read durable TV source/UI/workflow contracts
3. verify current authoritative PR/ref live
4. verify exact HEAD
5. inspect active execution path for the phase
6. check for newer user decision
7. keep TV-only scope
8. keep no-APK unless explicitly authorized

After production mutation:

1. inspect net diff
2. remove unrelated drift
3. add focused regression tests
4. run no-APK validation
5. verify validation belongs to exact final HEAD
6. verify APK count = 0 under no-APK work
7. report remaining physical-QA boundary separately

---

# 15. Physical QA Matrix

## Shell

- launch: expanded rail, Home focused
- rail order: Home -> Live TV -> Movies -> Series -> Settings
- Right: content focus + collapse
- Left at content boundary: rail focus + expand
- Left inside content does not prematurely open rail
- no focus trap
- no disruptive content reflow

## Home

- real progress values
- real Movie/Series rows
- details return focus
- empty state

## Live

- category/channel navigation
- Preview
- same-channel Full View
- EPG timeline
- CH+/CH-
- Back hierarchy
- one-player/one-surface continuity
- no duplicate audio/stale surface

## Movies

- provider categories
- no generic All
- long lists/scroll-follow-focus
- Continue Watching
- Favorites if shown
- details return
- resume/from-beginning

## Series

- provider categories
- no generic All
- Continue Watching
- seasons
- long episode list
- episode resume
- details return

## Settings

- shell -> Settings root
- four destinations
- subpage focus restore
- Add/Edit keyboard
- file picker
- Live Management mutations/order/groups
- Backup & Restore
- About
- Back hierarchy

Device QA should include 1080p, 4K where possible, large playlists, slow remote input, background/foreground, artwork/network delay, and 10-foot readability.

---

# 16. Explicit Non-Goals

Not included unless separately approved:

- Mobile redesign
- global Search
- Discover
- Library destination
- new auth/account system
- cloud sync architecture replacement
- recommendation service
- external metadata/TMDB/IMDb integration
- downloads/offline TV UI
- TV PiP UI
- new backup format
- database architecture replacement
- playback-engine replacement
- merge/release/publication

---

# 17. Resume in Another Chat

At the start of a new OwnPlay TV chat:

1. open `Gersi365/ownplay`
2. read `docs/OWNPLAY_TV_MEDIA_SHELL_REDESIGN_PLAN.md`
3. read the durable OwnPlay TV source/UI/workflow contracts
4. verify authoritative source ref and exact HEAD live
5. inspect Git history/diff to determine which redesign phase is actually complete
6. do not infer completion from this roadmap alone
7. continue the next smallest incomplete phase
8. remain no-APK unless explicitly authorized

**This plan defines what and in what order. GitHub live state defines where work currently is.**

---

# 18. Success Definition

OwnPlay TV should feel like one coherent media product while keeping existing reliability.

Final baseline must have:

- expanded-on-entry / collapsed-in-content left rail
- Home first
- Live TV immediately after Home
- Movies and Series separate
- Settings last
- no primary Search/Discover/Library
- no generic All in Movies/Series
- real catalog/provider data only
- stable visible focus
- predictable D-pad behavior
- safe Back hierarchy
- unchanged Live playback continuity
- unchanged source/data semantics
- realistic Details screens
- robust empty/loading states
