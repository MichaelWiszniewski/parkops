# Change log

## Starter 2026-09-15
Supplied infrastructure and unfinished exercise boundary.

## Student changes

### D1 (due 2026-10-13)

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
- `DESIGN.pdf` (see the D2 note below), `results/d1.txt`, and the completed `README.md`, `TEST_PLAN.md`, `AI_USE.md` and this change log.

Unchanged: all other supplied starter files (listed in `README.md`).

D1 feedback fixes: none yet.

### D2 (due 2026-11-09)

D1 feedback fixes: D1 feedback had not been returned when D2 was prepared. Self-identified corrections:
- **DESIGN.pdf was missing from the submitted D1 ZIP**, and the ZIP also contained `build/` class files and `.DS_Store` files while leaving out `setup-junit.sh`, `run.ps1` and `setup-junit.ps1`. I reported the missing report to the teaching team on 2026-10-26 with the unchanged PDF (its Google Docs history ends on 2026-10-09). The D2 ZIP is made with `git archive` from the repository and checked from a fresh extract, and `.gitignore` now excludes build output, IDE files, `.DS_Store`, `.env` and `*.key`.
- The D1 entry above said D1 was due 2026-10-09; the deadline was 2026-10-13. Corrected.
- Removed the starter's empty `results.txt`; evidence lives in `results/`.

Added (new source files in `src/`):
- Protocol: `Tool`, `ToolRequest`, `ToolRequestParser`, `ParseOutcome`, `ProtocolError`.
- Agent loop: `AgentRunner`, `InvestigationSession`, `Observation`, `RunResult`, `RunStatus`.
- Approval and execution: `DecisionOutcome`, `ExecutionOutcome`, `CommitLog`, `CommitRecord`, `NotificationLog`, `Notification`.
- Periodic detection: `PeriodicDetector`, `TickResult`, `WorkItem`.
- Operator and demonstration: `OperatorConsole`, `OperatorDecision`, `TerminalOperator`, `WorkflowDemo`.
- Supplied infrastructure, unchanged: `GeminiProvider`, `GeminiCredentials` (EECS 3311 D2 Gemini Connection Pack).

Changed:
- `ProposalManager`: now owns the proposal transition table: `approve`, `reject` and `execute` (UNKNOWN_PROPOSAL, DUPLICATE, NOT_APPROVED, STALE, UNAVAILABLE_OR_INELIGIBLE, COMMITTED, in that order), plus `hasActiveProposal` and `findProposal`. The D1 constructor is kept.
- `Proposal`: added `withStatus`, which returns a new snapshot; the class is still immutable.
- `ProposalStore`: added `replace`, which accepts only a status change to an existing proposal.
- `ParkingRegistry`: added `allSpaces`, `allBookings`, the commit step `moveBooking` (checks everything before writing anything) and two simulated external events, `closeSpace` and `occupySpace`.
- `StudentApplication`: `run(...)` is implemented; added `runAgent`, the operator actions `approve`, `reject`, `execute`, plus `proposal`, `proposalStatus`, `commitCount`, `notificationCount`, `notifications`, `tick`, `retryDetection` and `periodicTrace`.
- `Main`: new modes `scripted` (now the D2 replay workflow), `live` (Gemini, interactive operator, transcript saved to results/), `periodic`; the D1 demonstration moved to `d1-scripted`.

Added (tests, in `test/junit/`): `ToolProtocolTests`, `AgentRunnerTests`, `ApprovalExecutionTests`, `PeriodicDetectorTests`, `WorkflowDemoTests`, `GeminiProviderContractTests`. All D1 tests are kept unchanged as regression tests.

Added (documentation and evidence): `capture-results.sh`, `results/README.md`, `results/d2-*.txt`, `results/live-run-*.txt`, and the D2 updates to `README.md`, `TEST_PLAN.md`, `AI_USE.md`, `DESIGN.pdf` and this change log.
