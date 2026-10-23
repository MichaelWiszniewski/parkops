# Change log

## Starter 2026-09-15
Supplied infrastructure and unfinished exercise boundary.

## Student changes

### D1 (due 2026-10-09)

Added (new source files in `src/`):
- Value objects: `Identifier`, `Duration`.
- Enumerations: `ProposalStatus`, `RefusalReason`.
- Entities: `Space`, `Booking`, `Proposal` (immutable).
- Application: `ParkingRegistry`, `ProposalStore`, `IdGenerator`, `SequentialIdGenerator`, `ProposalManager`.
- Policy: `ReassignmentPolicy`, `PolicyV1`, `PolicyVerdict`, `Eligible`, `Ineligible`.
- Outcomes: `Result`, `Created`, `Refused`.

Changed (starter files modified):
- `DomainRules`: `requireDuration` and `requireIdentifier` now delegate to `Duration.isValid` and `Identifier.isValid`, so each rule lives in one place.
- `StudentApplication`: builds the registry from `Fixture`, delegates `propose(String)` to `ProposalManager`, maps `Created` to `ProposalView` and `Refused` to `IllegalArgumentException`. `bookingSnapshot()` is now built from the registry, and its key `requiresAccessible` was renamed to `accessibilityRequired` for consistency with `Booking.isAccessibilityRequired()`. `run(...)` is still the D2 stub.
- `test/junit/StudentTests.java`: starter stub replaced with 25 Jupiter tests.

Added (documentation and evidence):
- `DESIGN.pdf`, `results/d1.txt`, and the completed `README.md`, `TEST_PLAN.md`, `AI_USE.md` and this change log.

Unchanged: all other supplied starter files (listed in `README.md`).

D1 feedback fixes: none yet.
