# D3.4 contracts: specifications and manual proof

## Tool status

The course has not supplied or piloted an OpenJML environment for D3, so this follows the brief's tooling
contingency. The contracts are JML written inside Java comments in `src/AttemptBudget.java` and
`src/CommitLog.java`. They compile as ordinary Java with `bash run.sh lint` (`javac --release 21 -Xlint:all`),
but javac ignores comments, so compiling them checks nothing about the JML.

No verifier was run. There is no OpenJML command, version or output to report, and none is claimed here.
Every proof obligation below is discharged by hand only. In OpenJML terms, all of them are unresolved.
The boundary tests in `test/junit/ContractTests.java` exercise the edges of each specification; they are
tests, not proofs.

The scope is two small deterministic operations. Nothing here says anything about the model, the agent
loop as a whole, or the end-to-end workflow.

## Operation 1: `AttemptBudget.consume()` (attempt consumption, case D3-J)

`AttemptBudget` was extracted from `AgentRunner.run()`, which used to count calls in a local `int`. The
runner now creates one budget per run, calls `consume()` before each provider call and loops while
`remaining() > 0`.

**Class invariant**

```
MIN_LIMIT <= limit && limit <= MAX_LIMIT      (1..6)
0 <= used && used <= limit
```

**Specification of `consume()`**

| Case | Precondition | Frame | Postcondition |
|---|---|---|---|
| normal | `used < limit` | `assignable used` | `used == \old(used) + 1` and `remaining() == \old(remaining()) - 1` |
| exceptional | `used == limit` | `assignable \nothing` | throws `IllegalStateException` (only); `used == \old(used)` |

The two preconditions together cover every state the invariant allows, since `0 <= used <= limit` means
either `used < limit` or `used == limit`.

**Proof that the invariant holds**

- *Constructor.* A limit outside 1..6 throws before any field is written, so no object exists to break the
  invariant. Otherwise `limit` is in range and `used = 0`, which gives `0 <= 0 <= limit`.
- *`consume()`, normal case.* From `used < limit` we get `used + 1 <= limit`, and from `used >= 0` we get
  `used + 1 >= 0`. `limit` is `final`. So the invariant still holds after `used++`. There is no overflow,
  because `used <= 6`.
- *`consume()`, exceptional case.* The guard `used == limit` throws before the increment, so no field
  changes and the invariant is unchanged. This matches `assignable \nothing`.
- *`remaining()`, `used()`, `limit()`.* They are pure and write nothing. `remaining() == limit - used >= 0`
  follows directly from `used <= limit`.

**D3-J.** With a limit of 3, three `consume()` calls take `remaining()` to 2, 1 and 0. It never goes
negative, because of the invariant. A fourth call is in the prohibited pre-state `used == limit`: it throws
and leaves `used == 3`. In `AgentRunner`, the loop guard `remaining() > 0` means the runner never reaches
that state, so the provider is called at most `limit` times.

## Operation 2: `CommitLog.contains(proposalId)` (searching, with loop invariant and variant)

This is the search behind the `DUPLICATE` outcome (D3-F): `ProposalManager.execute()` asks it before
committing, and `CommitLog.add()` uses it to refuse a second commit for the same proposal.

**Assumptions about referenced objects**

- `Identifier.equals` is pure, deterministic and null-safe. It is a final String comparison.
- Every `CommitRecord` is an immutable record with a non-null `proposalId()`; its constructor enforces this.
- The log is used in one thread, and nothing changes `commits` during a call. `commits` is private and only
  `add()` writes to it.

**Class invariant**

```
commits != null
every element of commits is non-null
no two records in commits have equal proposalId()          (at most one commit per proposal)
```

`add()` preserves the invariant. A `null` record is refused before anything is written. A record whose
proposal is already present is refused by `contains()` before the append. Otherwise exactly one new,
non-null record with a fresh proposal id is appended.

**Specification**

```
requires proposalId != null;
assignable \nothing;
ensures \result <==> (\exists int j; 0 <= j < commits.size(); commits.get(j).proposalId().equals(proposalId));
```

**Loop**

```java
int i = 0;
while (i < commits.size()) {
    if (commits.get(i).proposalId().equals(proposalId)) return true;
    i++;
}
return false;
```

- Loop invariant I1: `0 <= i <= commits.size()`
- Loop invariant I2: `(\forall int j; 0 <= j < i; !commits.get(j).proposalId().equals(proposalId))`
- Variant: `commits.size() - i`
- Loop frame: only the local `i`

**Proof**

1. *Initialisation.* With `i = 0`, I1 holds because the size is at least 0. I2 quantifies over an empty
   range, so it is true.
2. *Maintenance.* Assume I1 and I2 at the top of an iteration, and the guard `i < size`.
   - If record `i` matches, the method returns `true` and the loop ends. That case is covered in step 4.
   - Otherwise record `i` does not match. Together with I2 for `0..i-1`, no record in `0..i` matches, which
     is I2 for `i + 1`. From `i < size`, `i + 1 <= size`, which is I1 for `i + 1`. Then `i++`.
3. *Termination.* The variant `size - i` is at least 0 by I1, since `i <= size`. Each iteration either
   returns or increases `i` by 1, so the variant drops by exactly 1. `size` cannot change, because nothing
   in the loop writes to `commits`. A non-negative integer that strictly decreases cannot do so forever.
4. *Postcondition.*
   - Early return: the method returns `true` only when record `i` matches, with `0 <= i < size`. So `j = i`
     is a witness for the `\exists`, and `\result == true` is correct.
   - Normal exit: the guard is false, so `i >= size`, and I1 gives `i == size`. I2 then says no record in
     `0..size-1` matches, so the `\exists` is false, and `\result == false` is correct.
5. *Frame.* The method writes only the local `i`. `commits.size()`, `commits.get(...)` and
   `Identifier.equals` are all reads, so `assignable \nothing` holds and the method can be called `pure`.

## Boundary tests (`test/junit/ContractTests.java`)

| Test | Boundary it covers |
|---|---|
| `d3jBudgetReachesLimit` | D3-J: remaining 2, 1, 0; consuming again throws and leaves state unchanged, twice |
| `everyLegalLimit` (1..6) | exactly `limit` successful consumes; postcondition and invariant after each one |
| `illegalLimits` | 0, 7, -1, `Integer.MIN_VALUE`, `Integer.MAX_VALUE` are refused by the constructor |
| `runnerStopsAtBudget` (1, 6) | a model that never gives a valid reply is called exactly `limit` times, then `STEP_LIMIT` |
| `containsEmpty` | zero loop iterations |
| `containsFoundPositions` | match at the first, a middle and the last index (early return) |
| `containsNotFound` | full scan, including a prefix-only near-miss (`PROP_1` vs `PROP_10`) |
| `containsByValue` | equality by value, not object identity |
| `containsChangesNothing` | the frame: count and records unchanged after searches |
| `uniquenessInvariant` | a duplicate or null `add` is refused and the log is unchanged |

To check that these tests can fail, two faults were introduced by hand and then reverted:

- A `consume()` without the `used == limit` guard made 7 tests fail.
- A `contains()` loop that stops one record early (`i < size - 1`) made 3 contract tests fail. The D3-F
  duplicate-execution tests still passed, because `execute()` also refuses a proposal already marked
  `EXECUTED`. That second check hides this bug, so only the contract-level tests detect it.
