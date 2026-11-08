# ParkOps: D2 (working supervised agent)

EECS 3311 B, Fall 2026. Individual project.
Student: Michael Wiszniewski
Student Number: 220 179 131

## What D2 does

D2 extends my D1 code into a supervised agent. The full workflow is:

1. **Detect.** `tick(time)` finds bookings on closed spaces (B1 on A17) and emits a work item. It only detects.
2. **Investigate.** `run()` asks the model for one tool request at a time. Java validates each request, runs the tool and sends the result back to the model as the next message. At most 6 model calls.
3. **Stop at PENDING.** A valid proposal is stored as PENDING. Nothing moves.
4. **Operator decision.** A separate human action approves, rejects or defers the proposal. The model can never approve.
5. **Execute with fresh checks.** Execution rechecks the proposal, the booking and policy versions and the target, then commits once. Repeating it is a DUPLICATE.

All data is synthetic. Notifications are simulated records; nothing is sent.

## What you need

- JDK 21. I developed and ran this with OpenJDK 21.0.12.1 on macOS (Apple silicon).
- Bash (macOS, Linux, or Git Bash on Windows). The starter also provides run.ps1 and setup-junit.ps1 for PowerShell.
- Internet access once, for setup-junit.sh, which downloads the pinned JUnit 6.0.3 jar and checks its checksum.
- For live mode only: your own Gemini API key from Google AI Studio (free tier). No paid account is needed.

## How to run it

From the project root (the folder that contains run.sh):

```
bash run.sh compile
bash run.sh check
bash setup-junit.sh          # once
bash run.sh test
bash run.sh run scripted     # D2 replay workflow
bash run.sh run periodic     # periodic detection trace
bash run.sh run live         # D2 live Gemini workflow (interactive)
```

| Command | What it does |
|---|---|
| `run.sh check` | The course's supplied public checks. Plain Java, not JUnit. |
| `run.sh test` | All JUnit tests: the supplied ones and mine in test/junit/. |
| `run.sh run scripted` | The prepared D2 demonstration with the ReplayProvider. It is labelled REPLAY and is not a live model. The operator decision is scripted (APPROVE) so the run is repeatable. |
| `run.sh run periodic` | Ticks 0, 59, 60, a backwards tick, 119 and 120 with controlled time. No waiting and no model call. |
| `run.sh run live` | The same workflow with Gemini. See below. |
| `run.sh run agent-scripted` | The replay agent loop only, stopping at the pending proposal. |
| `run.sh run d1-scripted` | The original D1 demonstration (this was `run scripted` in D1). |
| `run.sh run` | Prints the raw fixture data. |

### Live mode

Run `bash run.sh run live` directly in a terminal. Do not pipe or redirect it, because the supplied helper reads the key at a hidden prompt.

- Paste your Gemini API key at the prompt. If `GEMINI_API_KEY` is set in the environment, that value is used instead. The key is never printed or saved.
- The model defaults to `gemini-3.5-flash-lite`. `GEMINI_MODEL` overrides it.
- If the model's investigation ends with a pending proposal, the terminal asks the operator: `a` (approve), `r` (reject) or `d` (defer). Live mode never commits without this separate answer. End of input or an unrecognised answer means defer.
- The transcript, including the model id, date and environment, is saved to `results/live-run-<date>-<time>.txt`.

To capture all the non-interactive evidence into results/ at once: `bash capture-results.sh`.

## Outcome names

The brief lets me choose Java names; this is the mapping.

| Brief | My Java name | Where |
|---|---|---|
| NOT_APPROVED, STALE, UNAVAILABLE_OR_INELIGIBLE, COMMITTED, DUPLICATE | the same names | `ExecutionOutcome`, returned by `execute(id)` |
| (proposal identity check) | UNKNOWN_PROPOSAL | `ExecutionOutcome` and `DecisionOutcome` |
| approval / rejection | APPROVED, REJECTED; refused as NOT_PENDING | `DecisionOutcome`, returned by `approve(id)` / `reject(id)` |
| AWAITING_APPROVAL, NO_ACTION, STEP_LIMIT, PROVIDER_ERROR | the same names | `RunStatus` in `RunResult`, returned by `runAgent(...)` |
| request rejected (malformed, extra field, unknown tool, wrong booking) | INVALID_REQUEST observation with a `ProtocolError` | `ToolRequestParser` |
| controlled tool rejection (order, stale, ineligible target) | TOOL_REJECTED observation | `InvestigationSession` |
| clock rejection | CLOCK_REJECTED | `TickResult.Status`, returned by `tick(time)` |

Proposal statuses are exactly PENDING, APPROVED, REJECTED and EXECUTED (`ProposalStatus`).

## Supported scenario

The supplied ParkOps fixture: booking B1 at closed space A17 (3 hours, version 0, needs an accessible space) and spaces A17, B12, C03 and D09. Policy version 1: the target must differ from the current space and be open and not occupied. Accessibility is deliberately ignored, so B12 and C03 are both eligible. A model choosing either is correct.

## Known limitations

- Single process, sequential and in memory. Nothing is saved between runs, and the checks are not safe under concurrent use.
- Everything is in Java's default package, so access modifiers cannot hide `ProposalStore.replace`. Protection comes from reference ownership (only `ProposalManager` holds the store) and from `replace` refusing any change other than the status.
- Responses wrapped in markdown code fences are rejected rather than cleaned up, so a model that adds fences uses up an attempt.
- After a failed or rejected attempt, the periodic check does not retry the same booking and policy versions automatically. An operator retry or a version change is needed (the brief's conservative policy).
- A STALE proposal can never succeed later, because booking versions only increase. An UNAVAILABLE_OR_INELIGIBLE one may succeed if the target becomes available again.
- Calling `propose` directly (the D1 path) twice still creates two PENDING proposals. The periodic check avoids emitting duplicate automatic work.
- The live run depends on Google's service and free-tier quota. A failure there is reported as PROVIDER_ERROR and commits nothing.

## Starter and supplied files I reused

Unchanged:

- In src/: ChatMessage, Fixture, MiniJson, ModelProvider, OllamaProvider (kept but unused; the live route now uses Gemini), ProposalView, ReplayProvider.
- From the D2 Gemini Connection Pack (supplied EECS 3311 infrastructure): src/GeminiProvider.java and src/GeminiCredentials.java, unchanged. My contract tests use GeminiProvider's documented loopback test seam.
- Scripts: run.sh, run.ps1, setup-junit.sh, setup-junit.ps1.
- Supplied tests: test/plain/InfrastructureChecks.java, test/plain/PublicChecks.java, test/junit/InfrastructureTest.java, test/junit/SuppliedChecksTest.java.
- Data and notes: data/scenario.json, data/replay.jsonl, START_HERE.md, STARTER_PROVENANCE.md.

Changed: DomainRules (D1), StudentApplication, Main (new modes, Gemini live branch), and test/junit/StudentTests.java (D1). The starter's empty results.txt was removed because the evidence is in results/.

Everything else in src/ and test/junit/ was written for D1 or D2; CHANGELOG.md lists it. DESIGN.pdf explains the design, TEST_PLAN.md maps every acceptance case to its tests, and AI_USE.md lists the AI help I used.
