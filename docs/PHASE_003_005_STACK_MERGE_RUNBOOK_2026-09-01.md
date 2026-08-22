# Phase 003–005 stacked merge runbook

**Date:** 2026-08-22  
**Status:** Procedure only. No merge, PR-ready transition, retarget, release, deployment, or `main` mutation is authorized by this document.

## Current verified stack

The repository ancestry is linear and does not require a rebase:

1. `main` = `91a03c28ce1220abf20895248573aedf47a8c37a`
2. Phase 003 head = `008c6d60c5def9915a85fcf39b6bb9f44c5721e9`
   - branch: `agent/phase-003-local-persistence`
   - PR #3 base: `main`
   - compare from `main`: 13 commits ahead, 0 behind
   - final Android CI: run `32004983985`, success
3. Phase 004 head = `a938b2351a093ca8c863f111e338d7ba2fe76014`
   - branch: `agent/phase-004-live-browsing`
   - PR #4 base: `agent/phase-003-local-persistence`
   - compare from Phase 003: 10 commits ahead, 0 behind
   - final Android CI: run `32009616100`, success
4. Phase 005 reconciled candidate = `c57e4eae24d0857c5c5d2337e7c44fcd86eba249`
   - branch: `agent/phase-005-continuation-no-ci`
   - compare from Phase 004: 20 commits ahead, 0 behind
   - current PR #5 head remains historical `agent/phase-005-channel-personalization@b7d237025b4218fde93abbf2da172624f40755f7`
   - candidate is 6 commits ahead, 0 behind that PR head
   - full Android CI is still required on the reconciled candidate before any merge sequence begins

All PRs #3, #4, and #5 are currently draft and unmerged.

## Required merge method

Use **merge commits** for this stacked history.

Do not use squash or rebase merge for Phase 003, 004, or 005. Downstream branches contain the upstream phase heads in their ancestry. Squashing or rebasing an upstream PR would replace that ancestry and create avoidable downstream divergence, retarget noise, and evidence ambiguity.

Do not delete stacked source branches until all downstream merges and evidence capture are complete.

## Gate 0 — Phase 005 final-head validation

This gate occurs before any Phase 003/004/005 merge.

1. Verify Actions execution is available and that account/billing state is understood.
2. Verify these immutable identities again:
   - PR #5 head: `b7d237025b4218fde93abbf2da172624f40755f7`
   - candidate: `c57e4eae24d0857c5c5d2337e7c44fcd86eba249`
   - candidate remains ahead with no divergence.
3. Fast-forward `agent/phase-005-channel-personalization` to the candidate using `force=false`.
4. Allow exactly one `pull_request` Android CI run on the updated PR #5 head.
5. Require success for:
   - Android SDK 36 verification
   - JVM unit tests
   - Android lint
   - debug APK assembly
   - androidTest compilation
   - Room schema generation/drift verification
   - Room schema artifact
   - debug APK artifact
6. Capture workflow/run/job IDs and SHA-256 checksums of final artifacts.
7. If the run fails, classify the first failure before any rerun or source change. Stop the merge procedure.

A successful Gate 0 does not itself authorize a merge.

## Merge sequence — only after explicit user approval

### Step 1 — Phase 003

Preconditions:

- PR #3 remains `main <- agent/phase-003-local-persistence`.
- head remains `008c6d60...`.
- historical final validation run `32004983985` remains successful.
- PR is mergeable with no unexpected diff.

Procedure:

1. Mark PR #3 ready for review only as part of the authorized merge operation.
2. Merge PR #3 into `main` using **merge commit**.
3. Do not merge the next phase while the `main` push CI is running.
4. Require the `main` push CI for the Phase 003 merge commit to pass.
5. Confirm the resulting `main` tree is equivalent to the Phase 003 head tree plus merge metadata only.
6. Stop on any failure.

### Step 2 — Phase 004

Preconditions after Phase 003 is green on `main`:

- Phase 003 source branch remains available.
- PR #4 head remains `a938b235...`.
- historical final validation run `32009616100` remains successful.

Procedure:

1. Retarget PR #4 base from `agent/phase-003-local-persistence` to `main`.
2. Verify the PR diff now contains only Phase 004 changes; no Phase 003 files should appear merely because of stack ancestry.
3. Verify PR #4 remains mergeable.
4. Do not assume a base-retarget event will run CI; its historical head validation remains the source-head evidence because the Phase 003 tree merged to `main` unchanged.
5. Mark PR #4 ready only as part of the authorized merge operation.
6. Merge using **merge commit**.
7. Wait for the resulting `main` push CI and require success before proceeding.
8. Stop on any unexpected diff, conflict, or CI failure.

### Step 3 — Phase 005

Preconditions after Phase 004 is green on `main`:

- PR #5 head must be the Gate-0-validated reconciled candidate `c57e4eae...`.
- its full Android CI run must be successful with captured evidence.

Procedure:

1. Retarget PR #5 base from `agent/phase-004-live-browsing` to `main`.
2. Verify its diff contains only Phase 005 changes.
3. Verify PR #5 remains mergeable and the head SHA has not moved since Gate 0.
4. Mark PR #5 ready only as part of the authorized merge operation.
5. Merge using **merge commit**.
6. Wait for the resulting `main` push CI and require success.
7. Capture the integrated Phase 005 `main` commit SHA, workflow/job IDs, and artifact hashes.
8. Only after this integrated gate is green may Phase 005 be described as merged/integrated.

## Actions usage discipline

The Android workflow also runs on pushes to `main`. Therefore a three-phase merge can create three `main` validation runs in addition to the final Phase 005 pre-merge PR run.

Do not merge phases rapidly in order to exploit `cancel-in-progress`. Although the workflow uses a per-ref concurrency group, intentionally cancelling earlier integration runs would weaken evidence and can waste partial runner time. Merge one phase at a time and wait for its `main` gate.

Do not rerun a failed job blindly. Inspect the first failing step/log first.

## Phase 006 opening condition

Phase 006 production implementation remains closed until:

1. the reconciled Phase 005 candidate passes its full PR validation gate, and
2. if merge authorization is given, Phase 003, Phase 004, and Phase 005 are integrated in order with successful `main` validation after each merge.

Planning-only Phase 006 documentation does not relax this condition.

## Audit incident on 2026-08-22

During preparation of this runbook, a temporary draft probe PR #6 (`agent/phase-004-live-browsing -> main`) was created unintentionally while checking connector behavior. It was closed immediately without merge or source mutation.

Opening the probe triggered Android CI run `32568890627` (#58) on the already validated Phase 004 head. This run is not required evidence for the stack and must not be used to justify any new merge. The incident demonstrates that a PR open event can start runner execution; future CI-triggering operations must therefore be treated as explicit execution events rather than harmless metadata probes.
