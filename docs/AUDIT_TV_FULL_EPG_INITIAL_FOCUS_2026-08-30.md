# OwnPlay TV Full EPG initial focus audit — 2026-08-30

## Scope

Presentation/focus-only change stacked on Draft PR #56.

Base:
- branch: `agent/tv-no-offline-presentation-no-apk`
- exact base head: `b67472feaad41a42dfc501d09030cf9cfcabd9e8`

Audit branch:
- `agent/tv-full-epg-initial-focus-no-apk`

Product goal:
- when Full EPG opens on TV, start D-pad focus on the current programme when available
- if there is no current programme, focus the first available programme
- use `Done` only as the initial fallback when the guide cannot expose programme rows
- do not force focus on touch/mobile

## Finding

`EpgGuideSheet` previously created only a `doneFocusRequester` and requested it whenever the sheet opened on TV. The programme list scrolled near the current programme, but the D-pad focus remained on `Done`.

This made the user perform an extra navigation step before browsing EPG even though the guide was opened specifically for programme navigation.

## Implementation

### Pure focus policy

Added `EpgGuideFocusPolicy` with deterministic initial-focus resolution:

1. non-TV -> `NONE`
2. TV + loading/failure/no programmes -> `DONE`
3. TV + valid current programme -> `PROGRAM(currentIndex)`
4. TV + programmes but no valid current programme -> `PROGRAM(0)`

No EPG data/repository logic is changed.

### EpgGuideSheet

- Adds a dedicated programme `FocusRequester`.
- Resolves the target through `EpgGuideFocusPolicy`.
- For a programme target, scrolls the LazyColumn near that programme before requesting focus.
- Waits one Compose frame after scrolling so the target row is attached before `requestFocus()`.
- Attaches the programme requester only to the resolved target row.
- Keeps `Done` available and uses it as the safe fallback for loading/error/empty guide states.
- Mobile/touch receives no forced initial focus.

## Regression tests added

`EpgGuideFocusPolicyTest` covers:

- current programme wins on TV
- first programme fallback when current is unavailable
- Done fallback for loading/failure/empty guide
- non-TV does not force focus

## Architecture boundary

No changes to:
- EPG repository/provider/data model
- EPG timeline projection semantics
- Live preview/fullscreen handoff
- Media3/ExoPlayer
- decoder/surface ownership
- Room/schema/database
- downloads/storage
- auth/sync
- signing/versioning/release/deployment

## Static validation

Completed:
- exact base/head drift check before implementation
- source inspection of `EpgGuideSheet`
- deterministic focus policy extraction
- policy test addition
- post-mutation source/diff inspection
- FocusRequester attachment ordering review

Not executed on the exact audit head:
- Gradle compile
- unit tests
- lint
- AndroidTest compilation
- physical Android TV / TV Box QA
- smartphone/touch QA

The branch ends in `-no-apk`. Standard Android CI may skip it; a skipped workflow is not executable validation and does not constitute a build/test PASS.

No APK is requested or authorized.

## Physical TV QA target

Verify:

1. Open Full EPG when a current programme exists -> focus lands on current programme.
2. Open guide with programmes but no current programme -> focus lands on first programme.
3. Open while loading -> focus remains safely on Done.
4. Open with failed/empty guide -> focus remains safely on Done.
5. D-pad Up/Down continues natural row navigation after initial focus.
6. OK on a programme still opens programme details.
7. Back/ESC dismisses programme details/sheet through existing behavior.
