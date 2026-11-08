# Test plan

This plan covers D1 and D2. The D3 cases will be added with that deliverable.

The actual results are the unedited output saved in results/ (d1.txt for D1; d2-check.txt, d2-junit.txt, d2-scripted.txt, d2-periodic.txt and the live-run files for D2). Each records the date, the command and my environment. I do not repeat the numbers here, so the two cannot disagree. Plain-Java check runs, JUnit runs, replay runs and live runs are kept in separate, labelled files.

The D2 section comes first; the D1 section is unchanged below it and is still run as regression tests.

# D2

## How to run

```
bash run.sh check
bash run.sh test
bash run.sh run scripted
bash run.sh run periodic
bash run.sh run live        # interactive; needs a Gemini key
bash capture-results.sh     # saves check, test, scripted and periodic output to results/
```

## Test files

| File | Covers |
|---|---|
| ToolProtocolTests | Strict request parsing: JSON shape, exact fields, string types, tool names, goal booking, target rules (D2-H, D2-I) |
| AgentRunnerTests | The bounded loop: observations feed the next call, call limit, invalid responses, provider errors, tool order (D2-A, D2-H, D2-I, D2-J, D2-K) |
| ApprovalExecutionTests | Approve, reject, execute, duplicates, stale and ineligible checks, every cell of the transition table (D2-B to D2-G) |
| PeriodicDetectorTests | Ticks, boundaries, backwards time, suppression and the retry policy (D2-L, D2-M) |
| WorkflowDemoTests | The shared end-to-end path used by the scripted and live commands, and the operator boundary |
| GeminiProviderContractTests | The supplied GeminiProvider against a local fake endpoint on 127.0.0.1: requests, observations, quota, timeout, code fences. Not a live run |

## What "protected state unchanged" means in D2

The tests compare a full snapshot taken before and after: B1's space and version, the open and occupied flags of all four spaces, the commit count, the notification count and the status of every proposal. Where the only permitted change is a proposal's own status (for example after a rejection), the test says so explicitly.

## The D2 acceptance cases

### D2-A: replay read, list, propose B12

- Precondition: fresh fixture.
- Stimulus: ReplayProvider returns READ_BOOKING, LIST_ALTERNATIVES, PROPOSE_REASSIGNMENT B12.
- Expected: AWAITING_APPROVAL after exactly 3 calls; one PENDING proposal (booking version 0, policy version 1); B1 still at A17, version 0; all spaces unchanged. Each call receives the previous tool observation.
- Tests: d2aReplayHappyPath, observationsFeedNextCall, c03AlsoAccepted (C03 is also eligible) in AgentRunnerTests; fullInvestigationOverHttp in GeminiProviderContractTests.
- Evidence: d2-junit.txt, d2-scripted.txt.

### D2-B: execute a pending proposal

- Stimulus: execute a PENDING proposal.
- Expected: NOT_APPROVED; protected state identical; the proposal stays PENDING.
- Tests: d2bPendingNotApproved, TransitionTable.pendingRow.

### D2-C: approve then execute

- Expected: COMMITTED once; B1 at B12, version 1; A17 no longer occupied; B12 occupied; proposal EXECUTED; one commit record (PROP_1, B1, A17 to B12, version 1); one simulated notification.
- Tests: d2cApproveExecuteCommits, applicationOperatorFlow, WorkflowDemoTests.approvePath.
- Evidence: d2-scripted.txt; live run 2026-11-06 11:50:45.

### D2-D: execute the same committed proposal again

- Expected: DUPLICATE every time; commit and notification counts stay 1; state identical to just after the commit.
- Tests: d2dDuplicate, TransitionTable.executedExecute.
- Evidence: d2-scripted.txt; live run 11:50:45.

### D2-E: reject then execute

- Expected: REJECTED, then NOT_APPROVED; no commit; booking at A17, version 0; the only change is the proposal's status.
- Tests: d2eRejectThenExecute, TransitionTable.rejectedExecute, WorkflowDemoTests.rejectPath.
- Evidence: live runs 11:58:27 and 11:58:49.

### D2-F: version changes after the proposal

- Stimulus (booking): two proposals captured at version 0; the second is approved and committed, then the first is executed. Stimulus (policy): the policy version changes after approval.
- Expected: STALE; nothing moves; the stale proposal stays APPROVED; executing it again is still STALE.
- Tests: d2fStaleBookingVersion, d2fStalePolicyVersion; staleInvestigationRestarts (versions change during an investigation, so LIST is rejected and the agent must READ again).

### D2-G: target closed or occupied after the proposal

- Expected: UNAVAILABLE_OR_INELIGIBLE; nothing moves; the proposal stays APPROVED.
- Tests: d2gTargetClosed, d2gTargetOccupied.

### D2-H: malformed JSON or an extra approved field

- Expected: rejected before dispatch; the attempt is counted; no authority is granted (ToolRequest has no approval field at all).
- Tests: malformedRejected, nullRejected, approvedFieldRejected, wrongFieldsRejected, duplicateKeyRejected, nonStringRejected (ToolProtocolTests); malformedCountedThenRecovers, approvedFieldGrantsNothing (AgentRunnerTests); fencedReplyCounted, modelCannotApprove (GeminiProviderContractTests).

### D2-I: unknown tool or wrong goal booking

- Expected: a controlled INVALID_REQUEST observation; nothing is dispatched; state unchanged.
- Tests: unknownToolRejected, wrongBookingRejected (ToolProtocolTests); unknownToolOrWrongBooking (AgentRunnerTests).

### D2-J: six invalid responses

- Expected: STEP_LIMIT after exactly 6 provider calls (a seventh response is available but never requested); 6 invalid responses counted; no proposal.
- Tests: sixInvalidIsStepLimit; limitOne and limitOutOfRange check the 1 to 6 bounds.

### D2-K: provider throws or times out

- Expected: PROVIDER_ERROR; no proposal; no commit.
- Tests: providerThrows, providerTimesOut, providerRuntimeAndInterrupt, replayExhausted (AgentRunnerTests); quotaIsProviderError (HTTP 429, no retry), timeoutIsProviderError (GeminiProviderContractTests); providerErrorNoOperator (WorkflowDemoTests).

### D2-L: ticks 0, 59, 60 with unchanged versions

- Expected: tick 0 scans and emits one work item (B1 on A17, booking version 0, policy version 1); tick 59 is NOT_DUE; tick 60 scans and emits nothing. One work item in total. No proposal is created by detection.
- Tests: d2lBoundaryTicks, repeatedTicksNoDuplicates, activeProposalSuppresses, approvedProposalSuppresses, rejectedNotRetriedAutomatically, operatorRetry, policyVersionChangeReemits, bookingVersionChangeReemits.
- Evidence: d2-periodic.txt.

### D2-M: a tick below the previous time

- Expected: CLOCK_REJECTED; the schedule does not move; later valid ticks still work.
- Tests: d2mBackwardsClock, backwardsAfterBoundary, sameTimeIsNotBackwards.
- Evidence: d2-periodic.txt.

### D2-N: real model run through the allowed tools

- Expected: a genuine transcript with the model id, the requests and observations, the resulting proposal and the operator's outcome.
- Evidence: results/live-run-*.txt (gemini-3.5-flash-lite, 2026-11-06). Every run read, listed and proposed B12 in 3 calls with no invalid replies; the operator approved one run, deferred one and rejected two.
- The connection code is also tested without network access in GeminiProviderContractTests.

## Not covered in D2

- Concurrency and persistence (out of scope for this single-process system).
- Policy version 2 (accessibility) is D3.
- A live tool rejection: in every live run Gemini's requests were all valid, so rejected model requests are shown by the deterministic tests above, labelled as tests.

# D1

## How to run

```
bash run.sh compile
bash run.sh check
bash run.sh run scripted
bash setup-junit.sh
bash run.sh test
```

check runs the supplied public checks (plain Java). test runs the JUnit tests: the supplied ones plus mine in test/junit/StudentTests.java.

## Starting data

Booking B1 is in space A17 for 3 hours, is at booking version 0, and needs an accessible space. The policy version is 1. The four spaces are:

- A17: closed, occupied, not accessible
- B12: open, free, not accessible
- C03: open, free, accessible
- D09: open, occupied, accessible

Every test creates its own fresh objects, and none of them changes the supplied data. The one test that needs a space that is both occupied and closed uses its own purpose-built spaces.

## What must stay unchanged after a refusal

- Booking B1 stays in A17, at version 0, for 3 hours, still needing an accessible space.
- The four spaces keep the open, occupied and accessible values listed above.
- The ProposalStore holds no proposal for B1.

Each refusal test below says which of these it checks.

## The six D1 cases

### D1-A: boundary durations are accepted

- Precondition: none.
- Input: a Booking built with a duration of 1 hour, and another with 24 hours.
- Expected: the objects are created and report 1 and 24.
- Actual: pass, see results/d1.txt.
- Tests: bookingPreservesBoundaryDurations and durationBoundariesAccepted in StudentTests, and "duration endpoints" in the supplied checks.

### D1-B: durations of 0 and 25 are rejected

- Precondition: none.
- Input: a duration of 0 and of 25 (the Duration tests also try -1 and the largest int).
- Expected: IllegalArgumentException. No usable invalid Booking can be created, because a Booking needs a valid Duration first.
- Actual: pass, see results/d1.txt.
- Tests: bookingRejectsInvalidDurations and durationOutOfRangeRejected in StudentTests, and "invalid duration 0", "invalid duration 25" and "invalid duration -1" in the supplied checks.

### D1-C: B12 is accepted as a PENDING proposal

- Precondition: fresh fixture.
- Input: target B12 for booking B1.
- Expected: a PENDING proposal with id PROP_1, booking B1, target B12, booking version 0 and policy version 1, stored in the ProposalStore. B1 is still at A17, version 0, and every space is unchanged.
- Actual: pass, see results/d1.txt.
- Tests: proposeB12CreatesPendingProposal (checks the proposal and the store), proposeLeavesStateUnchanged (checks the booking and all four spaces), baselineIgnoresAccessibility, and appProposeB12 in StudentTests, and "D1-C pending B12, no booking change" in the supplied checks.

### D1-D: occupied D09 is refused

- Precondition: fresh fixture.
- Input: target D09 for booking B1.
- Expected: refused because the target is occupied. No proposal is created and the protected state is unchanged.
- Actual: pass, see results/d1.txt.
- Tests: occupiedTargetRefused checks the refusal reason, the booking, all four spaces and that the store is empty for B1. appRefusals checks that propose throws IllegalArgumentException and that the booking snapshot is the same as before. The supplied check is "D1-D occupied D09 rejected unchanged".

### D1-E: an unknown target is refused

- Precondition: fresh fixture.
- Input: target Z99 for booking B1.
- Expected: refused as an unknown target. No proposal is created and the protected state is unchanged.
- Actual: pass, see results/d1.txt.
- Tests: unknownTargetRefused checks the reason, the booking, all four spaces and the empty store. appRefusals checks the exception and the unchanged booking snapshot. The supplied check is "D1-E unknown target rejected unchanged".

### D1-F: callers cannot change protected state

- Precondition: a booking snapshot or a list of proposals obtained from the application.
- Input: an attempt to change the snapshot or the list.
- Expected: the attempt is refused or has no effect on the real data.
- Actual: pass, see results/d1.txt.
- Tests: bookingSnapshotCannotMutateState (changing the snapshot throws, and a new snapshot still shows A17 and version 0), storeReturnsUnmodifiableCopies (the list cannot be changed, and an earlier list does not grow when the store grows), and entitiesAreStructurallyImmutable (no setters, and every field is private and final). The supplied check is "D1-F protected snapshot".

## More refusal checks

- malformedTargetRefused: target ids that are null, empty, lowercase (b12) or 17 characters long are each refused as a badly formed target. It checks the booking, the four spaces and the empty store.
- bookingRefusals: a badly formed booking id (b1, null) and an unknown booking (B2) give the matching reasons. It checks the booking and the four spaces.
- argumentOrderMatters: swapping the two arguments is refused as an unknown booking, and nothing is stored.

## Other tests

- validIdentifiersAccepted, invalidIdentifiersRejected and domainRulesBoundary check the identifier rule: one capital letter, then up to 15 capital letters, digits, underscores or hyphens. They accept the 16-character limit and reject null, empty, blank, lowercase, a bad first character, spaces, a trailing newline and 17 characters. They also check that DomainRules reports bad values as IllegalArgumentException.
- distinctProposalIds checks that two proposals get different ids.
- currentSpaceHasPrecedence and requestCheckPrecedence check the order in which problems are reported.
- policyRulePrecedence checks that the current-space reason comes before occupied, which comes before not open. It uses purpose-built spaces, because the supplied fixture has no target that is both occupied and closed without also being the current space.
- policyContract checks that PolicyV1 reports version 1 and only ever gives the three policy reasons.

## Not covered in D1

- Duplicate proposals: repeating a request creates another PENDING proposal. This is a known limitation and is not tested.
- Approval, rejection, carrying out a proposal, out-of-date versions and the periodic check belong to D2 and D3.
- There is no live model in D1. The scripted run is a replay.
