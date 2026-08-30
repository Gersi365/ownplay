# Audit — Remove aggregate All from Movie/Series catalog detail routes

Date: 2026-08-30

## Scope

This checkpoint is stacked on exact Draft PR #64 head:

`f5420fc1bb5719d7efea7908f36d2f86cffca942`

Branch:

`agent/library-details-remove-aggregate-all-no-apk`

Goal: remove the remaining reachable aggregate `All` / `All Movies` entries from the legacy Movie/Series catalog panes that are still used when Unified Library opens Movie or Series details, while preserving playback, downloads, progress and TV focus behavior.

## Source finding before mutation

`OwnPlayApp` routes Unified Library detail requests through `VodRoute` and `SeriesRoute`.

Reachable aggregate entries still existed there:

- Movies portrait category strip: `All`;
- Movies landscape/TV category rail: `All Movies`;
- Series catalog filter row: `All`.

These were product-regression remnants and contradicted the existing no-aggregate-`All` Library decision.

## Implemented — Movies

File:

`app/src/main/java/app/ownplay/player/ui/vod/VodRoute.kt`

Changes:

- removed portrait `All` chip;
- removed landscape/TV `All Movies` row;
- provider categories are now the only visible Movie category choices;
- when there is no valid selected category, state normalizes to:
  1. the selected/detail Movie's real category when present and valid;
  2. otherwise the first real provider category;
  3. `null` only when the provider exposes no categories;
- Library-requested Movie details select the target Movie's real provider category when known, otherwise the first real provider category;
- late category hydration preserves the target Movie category instead of incorrectly falling back to the first category.

Preserved:

- Movie search;
- favorites filter;
- sort order;
- catalog selection callbacks;
- Movie details callback wiring;
- playback request construction;
- playback progress persistence;
- download behavior;
- TV/mobile Offline presentation boundary.

## Implemented — Series

File:

`app/src/main/java/app/ownplay/player/ui/series/SeriesRoute.kt`

Changes:

- removed visible `All` filter chip;
- provider categories are now the only visible Series category choices;
- category state normalizes to:
  1. selected/detail Series real category when present and valid;
  2. otherwise first real provider category;
  3. `null` only when no provider categories exist;
- Library-requested Series details resolve to the target Series category when known, otherwise first real category;
- existing TV catalog return `FocusRequester` moved from the deleted `All` chip to the selected real category, otherwise first real category;
- if a provider exposes zero categories, `Favorites` is the deterministic focus fallback so focus restoration never targets a missing node.

Preserved:

- Series search;
- Favorites semantics;
- Series → Season → Episode hierarchy;
- Continue Watching playback path;
- playback request construction;
- progress save/clear behavior;
- download enqueue/pause/resume/retry behavior;
- TV/mobile Offline presentation boundary.

## Architecture boundary

No changes to:

- Media3/player engine;
- decoder/surface ownership;
- Room/schema;
- download engine/storage;
- source sync/auth;
- signing;
- release/deployment.

No APK is requested or authorized.

## Static validation completed

- exact PR #64 head verified before branch creation;
- final source changes limited to `VodRoute.kt` and `SeriesRoute.kt` before this audit document;
- compare before evidence: 3 commits ahead / 0 behind;
- Movie requested-detail category and late-hydration logic re-read from exact head;
- Movie portrait and landscape category UI re-read and confirmed provider-category-only;
- Series requested-detail category logic re-read from exact head;
- Series TV focus restoration target re-read and confirmed selected/first real category with zero-category Favorites fallback;
- no extracted detail component, playback component or download runtime file changed.

## Validation not executed on this exact head

- Gradle compile;
- unit tests;
- lint;
- AndroidTest compilation;
- physical smartphone QA;
- physical Android TV / TV Box QA.

A skipped workflow is not a build/test PASS.

## Physical QA required before UX completion claims

Smartphone:

1. Library → Movies: verify only real provider categories are shown and no `All` exists.
2. Open a Movie from a non-first category and verify details return/category context remains that category.
3. Library → Series: verify only real provider categories are shown and no `All` exists.
4. Open Series from a non-first category and verify hierarchy/playback still works.

Android TV / TV Box:

1. Open Movie details from Library and confirm no `All Movies` appears in the side rail.
2. Open Series details and confirm no `All` filter appears.
3. Start a Series episode, return from playback and confirm focus restores to the selected real category (or first real category if needed).
4. Verify no Download/Offline presentation is visible on TV.

## Status

Static/source checkpoint only. Do not treat skipped CI as a build/test pass and do not claim physical UX validation until smartphone and TV/TV Box QA are completed.
