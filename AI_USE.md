# Student AI-use disclosure

| Date     | Tool/model                    | Task                                      | Files affected                        | What I changed or rejected | Verification |

| Oct 2026 | Claude Sonnet 5.5 (claude.ai) | Design-phase feedback only                | DESIGN.pdf                            | See 1 below                | See 1 below |
| Oct 2026 | Claude Sonnet 5.5 (claude.ai) | Wrote the Java code from my class diagram | src/                                  | See 2 below                | See 2 below |
| Oct 2026 | Claude Sonnet 5.5 (claude.ai) | Wrote the Jupiter tests                   | test/junit/StudentTests.java          | See 3 below                | See 3 below |
| Oct 2026 | Claude Sonnet 5.5 (claude.ai) | Drafted three documents                   | README.md, TEST_PLAN.md, CHANGELOG.md | See 4 below                | See 4 below |

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
