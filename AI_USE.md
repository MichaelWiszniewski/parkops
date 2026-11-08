# Student AI-use disclosure

| Date     | Tool/model                    | Task                                      | Files affected                        | What I changed or rejected | Verification |

| Oct 2026 | Claude Sonnet 5.5 (claude.ai) | Design-phase feedback only                | DESIGN.pdf                            | See 1 below                | See 1 below |
| Oct 2026 | Claude Sonnet 5.5 (claude.ai) | Wrote the Java code from my class diagram | src/                                  | See 2 below                | See 2 below |
| Oct 2026 | Claude Sonnet 5.5 (claude.ai) | Wrote the Jupiter tests                   | test/junit/StudentTests.java          | See 3 below                | See 3 below |
| Oct 2026 | Claude Sonnet 5.5 (claude.ai) | Drafted three documents                   | README.md, TEST_PLAN.md, CHANGELOG.md | See 4 below                | See 4 below |
| Oct-Nov 2026 | Claude Opus 5.5 (claude.ai) | D2 planning and design review         | DESIGN.pdf (state table, decisions)   | See 5 below                | See 5 below |
| Nov 2026 | Claude Opus 5.5 (claude.ai)   | Wrote the D2 Java code                    | src/ (listed in CHANGELOG.md, D2)     | See 6 below                | See 6 below |
| Nov 2026 | Claude Opus 5.5 (claude.ai)   | Wrote the D2 JUnit tests                  | test/junit/ (six D2 test files)       | See 7 below                | See 7 below |
| Nov 2026 | Claude Opus 5.5 (claude.ai)   | Drafted D2 documents and capture script   | README.md, TEST_PLAN.md, CHANGELOG.md, results/README.md, capture-results.sh | See 8 below | See 8 below |

## Details

### 1. Design-phase feedback

I first created a rough draft general design of the ParkOps application without generative AI. 

For the creation of the class and sequence diagrams, I used diagrams.net. No Generative AI was used in the creation of the class diagram and sequence diagram. 

I then had Claude scrutinize my system design, and it gave feedback and questions on my class list, my choice of outcome types, my refusal reasons, my class diagram source, and my sequence diagram. It did not draw my diagrams or write my DESIGN.pdf text.

- What I changed or rejected: I wrote the class list and made the design decisions myself. These were separate Created and Refused outcome types instead of an Optional proposal, a separate ProposalStore, and the refusal reasons and their order. After feedback I corrected the multiplicities, added missing fields, and removed an unnecessary third domain diagram.
- How I checked: I repeatedly compared the diagrams and every claim in the text with the source code.

### 2. Java code

Claude wrote the Java implementation from my class diagram. That covers the domain, policy, outcome and application classes, plus the edits to DomainRules and StudentApplication. The new classes are Identifier, Duration, ProposalStatus, RefusalReason, Space, Booking, Proposal, ParkingRegistry, ProposalStore, IdGenerator, SequentialIdGenerator, ReassignmentPolicy, PolicyV1, PolicyVerdict, Eligible, Ineligible, Result, Created, Refused and ProposalManager.

- What I changed or rejected: the class names, method names and the order in which problems are reported come from my design. I renamed the bookingSnapshot() key from requiresAccessible to accessibilityRequired for consistency, and documented that in README.md.
- How I checked: I ran compile, check, run scripted, setup-junit.sh and test on my own machine. The output is in results/d1.txt. Claude also compiled and ran the code in its own sandbox, but that output is not part of my evidence.

### 3. Tests

Claude wrote the Jupiter tests in StudentTests.java from the D1.4 requirements and the cases D1-A to D1-F.

- What I changed or rejected: I kept the supplied public checks exactly as they were, and only replaced the starter placeholder file.
- How I checked: I ran the tests on my own machine with bash run.sh test. The output is in results/d1.txt. I also had Claude break the code on purpose in its sandbox to confirm that the tests fail when they should.

### 4. README, TEST_PLAN and CHANGELOG

Claude drafted these three files from the supplied templates and my recorded results.

- What I changed or rejected: I filled in my name and student number and reviewed the content, I later reformatted them for better readability in markdown format..
- How I checked: I compared each statement with the code and with results/d1.txt.

## D2

The Gemini model (gemini-3.5-flash-lite) is not AI assistance in writing this project: it is the model the application itself calls at run time in live mode. Its transcripts are in results/live-run-*.txt.

### 5. D2 planning and design review

Claude broke D2 into steps and, for each design question, explained the relevant requirement and asked me questions. It did not make the decisions below.

- What I decided: the proposal transition table (I filled in every state and action cell, and corrected the cells Claude pointed out were incomplete); keeping `Proposal` immutable and replacing snapshots in the store; making `ProposalManager` the owner of the transition rules; making `tick()` detection-only, with the investigation started separately (the alternative was `tick()` calling `run()`).
- What Claude pointed out that changed my design: because the project uses Java's default package, a package-private store method would not be hidden, so the protection relies on reference ownership plus the store's same-proposal check. I recorded this as a limitation.
- I drew the D2 diagrams in diagrams.net and wrote DESIGN.pdf myself. Claude reviewed several drafts and pointed out where diagrams and text did not match the code (for example signatures, enum values, a wrong state transition and the tick/run arrow), supplying the correct names and some suggested wording; I made the corrections. 
- How I checked: I compared the state table and decisions against the overview and the D2 brief, and against the code.

### 6. D2 Java code

Claude wrote the D2 classes listed in CHANGELOG.md (D2), following the decisions in item 5. It also wired in the supplied Gemini helper, which is unchanged course infrastructure.

- What I changed: I did not modify the code Claude wrote; the design decisions it implements are mine (item 5)
- How I checked: I ran compile, check, test, run scripted, run periodic and run live on my own machine (results/). I read understand all new java files. Claude also compiled and tested in its own sandbox with a stand-in JUnit 5 jar; that output is not part of my evidence.

### 7. D2 tests

Claude wrote the six D2 test files from the D2 acceptance cases D2-A to D2-N and my transition table. The supplied tests and my D1 tests are unchanged.

- How I checked: I ran `bash run.sh test` with the pinned JUnit 6.0.3 jar on my machine (results/d2-junit.txt) and checked that TEST_PLAN.md names a real test for every case.

### 8. D2 documents and capture script

Claude drafted the D2 updates to README.md, TEST_PLAN.md, CHANGELOG.md, results/README.md, this file and capture-results.sh.

- What I changed or rejected: I added in the November 8 live runs to the results/README.md file. I was previously unable to get an unsuccessful or rejected model/tool interaction (like the D2 brief asked for), so I had to continue doing live runs until one appeared.
- How I checked: I compared each statement with the code and with the files in results/.
