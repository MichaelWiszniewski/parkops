# Test plan

This plan covers D1. The D2 and D3 cases will be added with those deliverables.

The actual results are the unedited output saved in results/d1.txt, which records the date, the commands and my environment. I do not repeat the numbers here, so the two cannot disagree. That file keeps the plain-Java check run and the JUnit run separate.

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
