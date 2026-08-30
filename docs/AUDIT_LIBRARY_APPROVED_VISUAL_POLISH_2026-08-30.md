# OwnPlay Library approved visual polish

Date: 2026-08-30

## Scope

Presentation-only Library polish stacked on Draft PR #58.

Base exact head: `23a2a04790c54dd1eb44b3406f779b84b3168dac`.

This pass applies the conversation-approved dark-purple visual reference to the real OwnPlay Library while preserving its established information architecture and behavior.

## Information architecture preserved

Primary navigation remains:
- Live
- Library
- Settings

Library presentation remains:
- mobile/touch: Offline / Movies / Series
- TV/D-pad: Movies / Series

There is no visible aggregate `All` destination and no `All categories` chip.

## Visual changes

### Header and controls
- increased Library page breathing room
- TV uses wider horizontal page padding than touch
- Library title moves to `headlineSmall`
- header title/subtitle spacing tightened
- refresh spinner reduced slightly
- top media-filter spacing increased
- search icon reduced to 20 dp
- search field corner radius tightened to 10 dp
- category strip spacing and trailing padding increased slightly

### Catalog density
- Cards mode keeps the existing touch density while TV uses a wider 172 dp minimum card size
- Compact mode keeps the existing touch density while TV uses a wider 120 dp minimum card size
- grid gutters and bottom breathing room increased
- List mode bottom breathing room increased

### Media cards
- standard media cards move from 14 dp / 1 dp tonal elevation to a flatter 10 dp / 0 dp presentation
- compact media cards move from 12 dp / 1 dp tonal elevation to 8 dp / 0 dp
- card inner padding is slightly reduced to keep posters visually dominant
- list row vertical padding is tightened by 1 dp

Poster aspect ratio remains 2:3. Existing title, status, Offline mobile, download actions and focus restoration semantics are unchanged.

## Architecture boundary

No changes to:
- playback / Media3 / decoder ownership
- Live Preview / Fullscreen handoff
- EPG repository or timeline behavior
- Library filter semantics or routing
- download engine / storage
- TV no-offline presentation boundary
- Room / schema / migrations
- auth / credentials / sync
- signing / versioning
- release / deployment / publication

## Static validation

Completed:
- exact PR #58 head verification before branching
- one-file source mutation
- exact base-to-head diff review
- Compose/Material3 API and import review

The source diff for the implementation commit is limited to `UnifiedLibraryRoute.kt` with 67 additions / 49 deletions (116 changed lines), all presentation-oriented.

Not executed on this exact head:
- Gradle compile
- unit tests
- lint
- AndroidTest compilation
- physical smartphone QA
- physical Android TV / TV Box QA

No APK is requested or authorized.

## Recommended next visual pass

Apply the same approved visual language to Live Categories and Channels + Preview + EPG while preserving the existing hierarchy and playback reliability model.
