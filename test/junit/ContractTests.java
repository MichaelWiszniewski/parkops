import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * D3.4 boundary tests for the two contracted operations, AttemptBudget.consume and CommitLog.contains.
 * These are ordinary JUnit tests under the brief's tooling contingency (no OpenJML environment supplied):
 * they exercise the boundaries of the JML specifications; they are not formal verification.
 */
class ContractTests {

    /** Checks AttemptBudget's class invariant from outside, through its pure queries. */
    private static void assertBudgetInvariant(AttemptBudget b) {
        assertTrue(AttemptBudget.MIN_LIMIT <= b.limit() && b.limit() <= AttemptBudget.MAX_LIMIT, "limit in range");
        assertTrue(0 <= b.used() && b.used() <= b.limit(), "0 <= used <= limit");
        assertEquals(b.limit() - b.used(), b.remaining());
        assertTrue(b.remaining() >= 0, "remaining nonnegative");
    }

    // ---- AttemptBudget ---------------------------------------------------------------------------------------------

    @Test @DisplayName("D3-J budget of 3 reaches its limit: remaining 2,1,0, never negative; the next consume fails and changes nothing")
    void d3jBudgetReachesLimit() {
        AttemptBudget b = new AttemptBudget(3);
        assertBudgetInvariant(b);
        assertEquals(3, b.remaining());
        for (int expected = 2; expected >= 0; expected--) {
            b.consume();
            assertEquals(expected, b.remaining());
            assertBudgetInvariant(b);
        }
        assertEquals(3, b.used());
        assertThrows(IllegalStateException.class, b::consume);    // prohibited pre-state: used == limit
        assertEquals(3, b.used(), "a refused consume leaves used unchanged");
        assertEquals(0, b.remaining());
        assertThrows(IllegalStateException.class, b::consume);    // still refused, still unchanged
        assertBudgetInvariant(b);
    }

    @ParameterizedTest(name = "limit {0}: exactly limit consumes succeed, invariant holds at every step")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void everyLegalLimit(int limit) {
        AttemptBudget b = new AttemptBudget(limit);
        int ok = 0;
        while (b.remaining() > 0) {
            int before = b.used();
            b.consume();
            assertEquals(before + 1, b.used(), "postcondition used == old(used) + 1");
            assertBudgetInvariant(b);
            ok++;
        }
        assertEquals(limit, ok);
        assertThrows(IllegalStateException.class, b::consume);
        assertEquals(limit, b.used());
    }

    @ParameterizedTest(name = "limit {0} is outside 1..6 and is refused")
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 7, Integer.MAX_VALUE})
    void illegalLimits(int limit) {
        assertThrows(IllegalArgumentException.class, () -> new AttemptBudget(limit));
    }

    @Test @DisplayName("AgentRunner bounds match the budget's bounds")
    void runnerUsesBudgetBounds() {
        assertEquals(AttemptBudget.MIN_LIMIT, AgentRunner.MIN_CALLS);
        assertEquals(AttemptBudget.MAX_LIMIT, AgentRunner.MAX_CALLS);
    }

    @ParameterizedTest(name = "model that never gives a valid reply, limit {0}: exactly limit provider calls, then STEP_LIMIT")
    @ValueSource(ints = {1, 6})
    void runnerStopsAtBudget(int limit) {
        int[] providerCalls = {0};
        ModelProvider garbage = history -> { providerCalls[0]++; return "not json"; };
        StudentApplication app = new StudentApplication();
        RunResult r = app.runAgent(garbage, limit);
        assertEquals(RunStatus.STEP_LIMIT, r.status());
        assertEquals(limit, providerCalls[0], "the provider is never called beyond the budget");
        assertEquals(limit, r.calls());
        assertEquals(limit, r.invalidResponses());
        assertEquals("A17", app.bookingSnapshot().get("spaceId"));
    }

    // ---- CommitLog.contains ----------------------------------------------------------------------------------------

    private static Identifier id(String s) { return new Identifier(s); }
    private static CommitRecord record(String proposal) {
        return new CommitRecord(id(proposal), id("B1"), id("A17"), id("C03"), 1);
    }
    private static CommitLog logOf(String... proposals) {
        CommitLog log = new CommitLog();
        for (String p : proposals) log.add(record(p));
        return log;
    }

    @Test @DisplayName("contains on an empty log: loop body never runs, result false")
    void containsEmpty() {
        assertFalse(new CommitLog().contains(id("PROP_1")));
    }

    @Test @DisplayName("contains finds the first, a middle and the last record")
    void containsFoundPositions() {
        CommitLog log = logOf("PROP_1", "PROP_2", "PROP_3");
        assertTrue(log.contains(id("PROP_1")));
        assertTrue(log.contains(id("PROP_2")));
        assertTrue(log.contains(id("PROP_3")));
    }

    @Test @DisplayName("contains scans the whole log and returns false when no record matches")
    void containsNotFound() {
        assertFalse(logOf("PROP_1", "PROP_2", "PROP_3").contains(id("PROP_4")));
        assertFalse(logOf("PROP_1").contains(id("PROP_10")), "prefix is not a match");
    }

    @Test @DisplayName("contains matches by value, not by object identity")
    void containsByValue() {
        CommitLog log = logOf("PROP_7");
        assertTrue(log.contains(new Identifier("PROP_7")));
    }

    @Test @DisplayName("frame: contains assigns nothing (count and records unchanged)")
    void containsChangesNothing() {
        CommitLog log = logOf("PROP_1", "PROP_2");
        List<CommitRecord> before = log.all();
        log.contains(id("PROP_2"));
        log.contains(id("PROP_9"));
        assertEquals(2, log.count());
        assertEquals(before, log.all());
    }

    @Test @DisplayName("class invariant: a second record for the same proposal is refused and the log is unchanged")
    void uniquenessInvariant() {
        CommitLog log = logOf("PROP_1", "PROP_2");
        assertThrows(IllegalStateException.class, () -> log.add(record("PROP_2")));
        assertThrows(IllegalArgumentException.class, () -> log.add(null));
        assertEquals(2, log.count());
        List<Identifier> ids = new ArrayList<>();
        for (CommitRecord c : log.all()) ids.add(c.proposalId());
        assertEquals(List.of(id("PROP_1"), id("PROP_2")), ids);
    }
}
