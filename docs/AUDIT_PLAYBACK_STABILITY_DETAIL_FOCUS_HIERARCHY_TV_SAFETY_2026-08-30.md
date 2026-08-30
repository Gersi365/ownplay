# Playback Stability, Detail Focus, Hierarchy, and TV Safety Audit — 2026-08-30

## Scope

This checkpoint is stacked on Draft PR #66 exact head:

`d163cb1fe07eaf29a474b067370de9d38643ceff`

Branch:

`agent/playback-stability-detail-focus-hierarchy-no-apk`

The scope is deliberately narrow:

- make Movie/Series detail entry useful with OK immediately on TV
- preserve category context when navigating back
- add a small default network-recovery improvement without exposing a new Settings surface
- explicitly preserve the previously hardened TV playback/surface architecture

No APK is requested or authorized.

## Implemented

### Movie Details focus

- TV entry focus moves to the primary `Play` / `Resume` action instead of `Back`.
- The same primary action is restored after returning from Movie playback.
- The Back action remains available but is no longer the injected initial focus target.

### Series focus hierarchy

TV focus is aligned with the actionable hierarchy:

- Series root -> first Season
- Season -> `Play` / `Resume` on the first Episode
- Episode Details -> `Play` / `Resume`
- Series with no Seasons -> Favorite fallback

Focus requests are delayed by one Compose frame and only issued when the target is known to exist.

### Movie Back hierarchy

Library-opened Movie flow now follows:

`Playback -> Movie Details -> selected Movie category/catalog -> Library`

Closing Movie Details no longer skips directly to Library. The selected real provider category remains selected, and TV focus is restored to that category before the next Back returns to Library.

### Series Back hierarchy

Library-opened Series flow now follows:

`Playback -> Episode -> Season -> Series -> selected Series category/catalog -> Library`

The Series root no longer skips directly to Library. The existing catalog focus restoration is used for the selected real provider category.

### App-level Back shortcut removal

The old `OwnPlayApp` Movie `BackHandler` shortcut that could bypass route-level hierarchy has been removed. Movie/Series route back ownership now remains with `PlaybackInteractionBridge` while those route levels are active.

### Default network recovery

No Network section or user-facing setting was added.

The default `PlaybackRetryPolicy` now allows one additional bounded automatic attempt:

- previous automatic attempts: 2
- current automatic attempts: 3
- delays remain bounded at 750 ms, 1.5 s, then 3 s

This is recovery behavior only. It does not reserve bandwidth against other devices on the LAN and does not increase video buffer memory.

A focused unit test was added at:

`app/src/test/java/app/ownplay/player/playback/PlaybackRetryDefaultsTest.kt`

It locks the intended default budget and delay sequence. The test is present in source but has not yet been executed on this exact head.

## TV restart / decoder / memory safety boundary

A temporary experimental 90-second streaming buffer profile was evaluated on this branch and then removed before this checkpoint because prioritizing time over byte-size thresholds could increase memory pressure on low-memory TV / TV Box hardware.

The final cumulative diff against PR #66 contains no `Media3PlaybackEngine.kt` change.

The following previously hardened areas are unchanged by the final checkpoint:

- `MainActivity` lifecycle handling
- `Media3PlaybackEngine` construction
- `PlayerView` bind/unbind behavior
- Live Preview / Fullscreen transition gate
- decoder/surface ownership model
- one-player / one-decoder rule
- Live controlled detach/stop/restart handoff
- VOD/Series playback engine ownership

The retry increase reuses the same `PlaybackEngine`; retry paths suspend the current engine before restarting the request. It does not create parallel players or decoders.

## Final cumulative source diff before this audit document

Against exact PR #66 head:

- `PlaybackHardening.kt`: +1 / -1
- `OwnPlayApp.kt`: +1 / -11
- `SeriesDetailsPane.kt`: +42 / -12
- `SeriesRoute.kt`: +13 / -3
- `MovieDetailsPane.kt`: +9 / -8
- `VodRoute.kt`: +46 / -9
- `PlaybackRetryDefaultsTest.kt`: focused new unit test

No lifecycle, Media3 engine, Surface, decoder, database, auth, signing, versioning, release, or deployment files are in the final cumulative source diff.

## Static validation completed

- exact PR #66 head verified before branching
- cumulative compare confirms linear ancestry and zero behind commits
- final diff reviewed file-by-file
- Movie primary focus target exists whenever the TV request is issued
- Series focus request is one-frame delayed and gated on target availability
- Movie category focus restore targets selected real provider category, otherwise first real provider category
- Series hierarchy uses existing selected/first real provider category focus restore
- app-level Movie Back shortcut removed
- custom high-memory buffer experiment fully absent from final cumulative diff
- existing playback hardening tests inspected; they inject explicit retry budgets and do not depend on the old default value
- focused default retry test added

## Validation still required

This audit is not executable validation.

Still required before release readiness:

- Gradle/KSP compile
- unit tests
- lint
- AndroidTest compilation
- physical smartphone QA
- physical Android TV / TV Box QA

A skipped workflow is not a build/test PASS.

## Highest-priority physical TV regression check

The previously observed Live Fullscreen -> Preview freeze/restart issue remains a P1 physical-device validation item. This source audit reduces regression surface but cannot prove absence of device restart/freeze without running the exact final build on real TV / TV Box hardware.

Required physical repetitions include:

- Categories -> Channels -> Preview
- Preview -> Fullscreen
- Fullscreen -> Preview
- repeat several times
- verify no black/stuck Surface
- verify no app/device restart
- verify controlled Live stream restart reaches Preview reliably
- verify Movie/Series Play-first focus and full Back hierarchy

## Operational boundary

Draft only. Do not merge, mark ready, deploy, release, publish, force-push, rewrite history, alter signing, or create an APK without explicit authorization.
