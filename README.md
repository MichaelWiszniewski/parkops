# ParkOps: D1 (design and executable proposal path)

EECS 3311 B, Fall 2026. Individual project.
Student: Michael Wiszniewski 
Student Number: 220 179 131

## What D1 does

D1 builds the checked domain model and the first step of the workflow. Given booking B1 (which is in the closed space A17) and a suggested new space, the program either creates a PENDING proposal or refuses the request and says why. It never moves a booking. Approval, carrying out a proposal, the agent loop and a live model are not part of D1. All data is synthetic.

## What you need

- JDK 21. I developed and ran this with OpenJDK 21.0.12.1 on macOS (Apple silicon).
- Bash (macOS, Linux, or Git Bash on Windows). The starter also provides run.ps1 and setup-junit.ps1 for PowerShell.
- Internet access once, for setup-junit.sh. It downloads the pinned JUnit 6.0.3 jar and checks its checksum.

## How to run it

From the project root (the folder that contains run.sh):

```
bash run.sh compile
bash run.sh check
bash run.sh run scripted
bash setup-junit.sh
bash run.sh test
```

- compile builds everything with all compiler warnings turned on.
- check runs the course's supplied public checks. These are plain Java, not JUnit.
- run scripted is the D1 demonstration. It feeds the fixed target B12 and prints the proposal and then the booking. It is a replay of a fixed answer, not a live model.
- setup-junit.sh is only needed once. It downloads the pinned JUnit jar.
- test runs the supplied JUnit tests plus my own tests in test/junit/StudentTests.java.

You can also run `bash run.sh run` to print the raw fixture data.

The real output of these commands, with the date and my environment, is saved in results/d1.txt.

## What the program handles

For booking B1:

- Target B12: a PENDING proposal is created (PROP_1, booking version 0, policy version 1). B1 stays at A17, version 0.
- Target D09: refused because it is occupied. No proposal is created and nothing changes.
- An unknown target such as Z99: refused as an unknown target. No proposal is created and nothing changes.
- A badly formed or unknown id for the booking or the target: refused, with a reason that says which one.

The run scripted command only feeds B12, because it uses the supplied Main. The D09 and unknown-target cases are run by the check and test commands.

If a request has more than one problem, the first one in this list is reported: target id badly formed, booking id badly formed, booking unknown, target unknown, target is the booking's current space, target occupied, target not open.

Policy version 1 deliberately ignores accessibility. That means both B12 and C03 are acceptable targets for B1, even though B1 needs an accessible space. The course uses this as an exercise in changing a policy later. It is not a realistic parking rule.

## How my code connects to the starter code

The starter gave a small public surface (DomainRules and StudentApplication) that the supplied checks call. I kept those methods and turned them into thin wrappers around my own classes.

- DomainRules.requireDuration and DomainRules.requireIdentifier now call Duration.isValid and Identifier.isValid. They throw IllegalArgumentException when the value is not valid, so each rule exists in one place.
- Creating a StudentApplication builds the parking data from the supplied Fixture, then sets up a ProposalManager with PolicyV1, a ProposalStore and a SequentialIdGenerator.
- propose(targetId) calls ProposalManager.proposeReassignment(targetId, "B1"). If a proposal is created, it is returned as a ProposalView. If the request is refused, it throws IllegalArgumentException with the refusal message.
- bookingSnapshot() returns an unchangeable map built from B1 in the registry. I renamed one key from requiresAccessible to accessibilityRequired so that it matches Booking.isAccessibilityRequired(). The raw Fixture is unchanged and still uses the old name.
- run(...) is untouched. It is still the D2 placeholder, and the agent-scripted mode reports STARTER_NOT_IMPLEMENTED.

## Known limitations

- Repeating the same request creates another PENDING proposal for the same booking, versions and target.
- D1 ends at PENDING. Nothing can approve, reject or carry out a proposal yet. My booking and space objects cannot be changed after they are created, so D2 will need a way for the registry and the store to replace them.
- There is no live model. run scripted replays a fixed answer. The live and agent-scripted modes are for D2.
- The program runs in one process, one step at a time, and keeps everything in memory. It makes no claims about concurrent use or saving data.

## Starter files I reused

Unchanged:

- In src/: ChatMessage, Fixture, Main, MiniJson, ModelProvider, OllamaProvider, ProposalView, ReplayProvider.
- Scripts: run.sh, run.ps1, setup-junit.sh, setup-junit.ps1.
- Supplied tests: test/plain/InfrastructureChecks.java, test/plain/PublicChecks.java, test/junit/InfrastructureTest.java, test/junit/SuppliedChecksTest.java.
- Data and notes: data/scenario.json, data/replay.jsonl, START_HERE.md, STARTER_PROVENANCE.md, results.txt, results/README.md.

Changed: DomainRules, StudentApplication, and test/junit/StudentTests.java (the starter placeholder was replaced with my tests). The starter templates README.md, TEST_PLAN.md, CHANGELOG.md and AI_USE.md were filled in.

Everything else in src/ was written for D1. DESIGN.pdf explains the design, and AI_USE.md lists the AI help I used.
