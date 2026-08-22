# Phase 005 final Android CI evidence — 2026-08-22

## Result

**PASS.** The reconciled Phase 005 candidate completed the full established Android CI gate successfully.

Validated commit:

`c57e4eae24d0857c5c5d2337e7c44fcd86eba249`

PR branch:

`agent/phase-005-channel-personalization`

Pull request:

`#5 — Phase 005: implement channel personalization`

Workflow:

- name: `Android CI`
- run: `32569192847`
- run number: `59`
- job: `validate`
- conclusion: `success`

## Gate results

Every required workflow step completed successfully:

- Checkout
- JDK 17 setup
- Gradle 9.5.0 setup
- Android SDK 36 verification
- `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin --stacktrace`
- Room schema artifact upload
- committed Room schema semantic drift verification
- debug APK artifact upload

This closes the Phase 005 Android compile/test/lint/schema gate for the exact reconciled candidate SHA above.

## Workflow artifacts

### Debug APK artifact

- artifact name: `ownplay-debug-apk`
- artifact id: `9474898787`
- artifact archive size reported by GitHub: `12,466,062` bytes
- GitHub artifact digest / downloaded ZIP SHA-256:
  `fde11155d257023676a053fc010d24c824db228973a07f23ccaf0207eaf547ba`
- contained file: `app-debug.apk`
- contained APK SHA-256:
  `e257027508f09836d957920474641383ff1cbecfd8c4382dfa2738511c5a5041`

### Room schema artifact

- artifact name: `ownplay-room-schema`
- artifact id: `9474898528`
- artifact archive size reported by GitHub: `3,768` bytes
- GitHub artifact digest / downloaded ZIP SHA-256:
  `45d8177eac3c270256ea9aa7f69a08488d3d2e73d6bd53a0729c4eaf989a2cb9`
- contained schema v1 SHA-256:
  `5a551b49774ca4e3f000900a0a653b6798e8ae1696ee409618f3fc4f7b9d3c21`
- contained schema v2 SHA-256:
  `48764825397a46464d1c58dc45aa1a423f51998d55178a75cf34c74bb671a9c6`

GitHub reported artifact expiry on 2026-08-29. Local evidence copies were downloaded during the audit.

## Provenance

Before the validation trigger:

- PR #5 head was `b7d237025b4218fde93abbf2da172624f40755f7`.
- The reconciled candidate was verified as a strict fast-forward: 6 commits ahead and 0 behind.
- `agent/phase-005-channel-personalization` was moved to the candidate with `force=false`.
- PR #5 then reported head SHA exactly `c57e4eae24d0857c5c5d2337e7c44fcd86eba249`.
- That synchronization triggered Android CI run #59.

No source change was made after the validated SHA. No rerun was required.

## Status boundary

Phase 005 implementation validation is now complete for the exact candidate SHA above.

This does **not** authorize or perform:

- merge of PR #3, #4, or #5
- retargeting of stacked PRs
- release or deployment
- store publication
- Phase 006 production-code implementation

The stack merge runbook remains the next integration procedure when merge authorization is explicitly granted.

## Audit note: run #58

A temporary probe PR #6 was unintentionally created earlier while checking connector behavior and was immediately closed without merge or source/main mutation. It triggered Android CI run `32568890627` (#58) on the already validated Phase 004 head; that run also completed successfully. It is unrelated to the Phase 005 candidate validation and is retained only as audit evidence.
