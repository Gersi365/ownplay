# Phase 005 CI re-entry plan — 2026-09-01

## Status

Prepared only. Do not trigger GitHub Actions while the included Actions allowance remains exhausted.

This runbook defines the lowest-risk path for validating the reconciled Phase 005 candidate after Actions capacity resets. It does not authorize merge, release, deployment, publication, force-push, workflow changes, or Phase 006 production implementation.

## Immutable candidate

Phase 005 reconciled candidate:

`c57e4eae24d0857c5c5d2337e7c44fcd86eba249`

The candidate must remain unchanged until validation completes. Documentation/planning work should continue on separate branches so that the CI target remains stable.

## Existing PR path

Draft PR #5 currently uses:

- base: `agent/phase-004-live-browsing`
- head branch: `agent/phase-005-channel-personalization`
- current head: `b7d237025b4218fde93abbf2da172624f40755f7`

The reconciled candidate is currently six commits ahead of `b7d2370...` and zero commits behind. Therefore, if this ancestry remains true at re-entry time, the existing PR head branch can be fast-forwarded to the candidate without force-push.

This is preferred over opening a second Phase 005 PR or pushing to `main` because the current workflow triggers on `pull_request` and on pushes to `main` only.

## Preflight before any ref update

On or after the Actions reset, perform these checks in order:

1. Confirm the included Actions capacity is available again. Do not infer this from the calendar alone if GitHub still reports a billing/spending block.
2. Confirm `agent/phase-005-continuation-no-ci` still resolves to `c57e4eae24d0857c5c5d2337e7c44fcd86eba249`.
3. Confirm PR #5 is still open and draft, with head branch `agent/phase-005-channel-personalization`.
4. Compare PR #5 head to `c57e4eae...` again.
5. Proceed only if the candidate is ahead with `behind_by = 0`; otherwise stop and reconcile the changed history before any branch mutation.
6. Confirm `.github/workflows/android-ci.yml` is unchanged from the audited candidate and still uses standard `ubuntu-latest` runners.

## Single-run trigger

If all preflight conditions pass:

1. Fast-forward `agent/phase-005-channel-personalization` to `c57e4eae24d0857c5c5d2337e7c44fcd86eba249` with `force = false`.
2. Do not make any other source or PR-head commit at the same time.
3. The existing draft PR should then produce one `pull_request` Android CI run for the candidate.
4. Do not manually rerun while that run is queued or in progress.

## Required validation gate

The candidate is green only if the workflow completes all established gates:

- Android SDK 36 verification
- `:app:testDebugUnitTest`
- `:app:lintDebug`
- `:app:assembleDebug`
- `:app:compileDebugAndroidTestKotlin`
- Room schema artifact generation
- committed Room schema drift verification
- debug APK artifact generation

Compilation alone is not sufficient.

## Failure classification

If the run does not succeed, do not rerun blindly.

Classify the first failure as one of:

### Infrastructure / billing

Examples:

- no runner starts
- Actions spending/billing restriction
- GitHub-hosted runner infrastructure failure

Action: preserve evidence, do not change source, and retry only after the external blocker is resolved.

### Toolchain / environment

Examples:

- expected Android SDK/build-tools missing
- action/setup failure unrelated to repository source

Action: inspect official runner/toolchain status and make the smallest justified infrastructure correction only if evidence shows the workflow assumption is stale.

### Source / test / lint / compile

Examples:

- Kotlin compilation error
- unit-test failure
- lint failure
- androidTest compilation failure
- Room schema drift
- missing APK/schema artifact caused by repository output

Action: record the exact failing step and logs, create a focused repair branch/commit, run dependency-free checks where applicable, and trigger another full CI only after the specific defect is addressed.

## Success evidence capture

If the run succeeds:

1. Record workflow run ID/number and candidate SHA.
2. Fetch job steps and verify every required step completed successfully.
3. Download `ownplay-debug-apk` and `ownplay-room-schema` artifacts.
4. Compute SHA-256 checksums for downloaded artifacts.
5. Record artifact IDs, names, checksums, and retention information in Phase 005 validation evidence.
6. Confirm no unexpected Room schema changes exist.
7. Keep PR #5 draft until the broader stacked-branch/merge decision is explicitly authorized.

## Gate transition

Only after the full Android gate succeeds on the reconciled candidate may Phase 005 be marked build-validated and the Phase 006 implementation gate be opened.

Opening the Phase 006 gate still does not authorize merge, release, deployment, publication, or unrelated architecture changes.
