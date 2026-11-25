import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D3 required regression and change cases D3-A..D3-I, run through the StudentApplication boundary on a
 * clean fixture per test (JUnit creates a new instance per test). D3-J belongs to the contracts step.
 * Model responses come from ReplayProvider or a scripted lambda: test doubles, not a live model.
 */
class D3ChangeCaseTests {
    private static final int LIMIT = 6;
    private static final String READ = "{\"tool\":\"READ_BOOKING\",\"bookingId\":\"B1\",\"targetId\":\"\"}";
    private static final String LIST = "{\"tool\":\"LIST_ALTERNATIVES\",\"bookingId\":\"B1\",\"targetId\":\"\"}";
    private static final String FINISH = "{\"tool\":\"FINISH\",\"bookingId\":\"B1\",\"targetId\":\"\"}";

    private final StudentApplication app = new StudentApplication();

    // ---- helpers -------------------------------------------------------------------------------------------------

    private static String propose(String target) { return Fixture.proposalRequest(target); }

    /** Content of the observation that answered the given 1-based model call. */
    private static String observationAfterCall(RunResult r, int call) {
        int seen = 0;
        List<ChatMessage> t = r.transcript();
        for (int i = 0; i < t.size(); i++)
            if (t.get(i).role().equals("assistant") && ++seen == call) return t.get(i + 1).content();
        throw new AssertionError("no call " + call);
    }

    private static List<String> allObservations(RunResult r) {
        List<String> out = new ArrayList<>();
        List<ChatMessage> t = r.transcript();
        for (int i = 1; i < t.size(); i++) if (t.get(i - 1).role().equals("assistant")) out.add(t.get(i).content());
        return out;
    }

    /** Runs READ, LIST, PROPOSE(target) and returns the pending proposal id. */
    private static String agentProposes(StudentApplication app, String target) {
        RunResult r = app.runAgent(new ReplayProvider(List.of(READ, LIST, propose(target))), LIMIT);
        assertEquals(RunStatus.AWAITING_APPROVAL, r.status(), allObservations(r).toString());
        return r.proposalId().orElseThrow();
    }

    private static void assertB1(StudentApplication app, String space, int version) {
        Map<String, Object> b = app.bookingSnapshot();
        assertEquals(space, b.get("spaceId"));
        assertEquals(Integer.valueOf(version), b.get("version"));
    }

    private static void assertNoDomainMutation(StudentApplication app) {
        assertB1(app, "A17", 0);
        assertEquals(0, app.commitCount());
        assertEquals(0, app.notificationCount());
    }

    // ---- D3-A / D3-B: candidates before and after the change ----------------------------------------------------

    @Test @DisplayName("D3-A policy 1, B1 fixture: the model is offered B12 and C03")
    void d3aBaselineCandidates() {
        assertEquals(1, app.policyVersion());
        RunResult r = app.runAgent(new ReplayProvider(List.of(READ, LIST, FINISH)), LIMIT);
        assertEquals(RunStatus.NO_ACTION, r.status());
        assertTrue(observationAfterCall(r, 1).contains("\"policyVersion\":1"), observationAfterCall(r, 1));
        assertTrue(observationAfterCall(r, 2).contains("\"candidates\":[\"B12\",\"C03\"]"), observationAfterCall(r, 2));
        assertNoDomainMutation(app);
    }

    @Test @DisplayName("D3-B activate policy 2: the model is offered only C03")
    void d3bVersion2Candidates() {
        assertTrue(app.activatePolicyV2());
        RunResult r = app.runAgent(new ReplayProvider(List.of(READ, LIST, FINISH)), LIMIT);
        assertTrue(observationAfterCall(r, 1).contains("\"policyVersion\":2"), observationAfterCall(r, 1));
        assertTrue(observationAfterCall(r, 2).contains("\"candidates\":[\"C03\"]"), observationAfterCall(r, 2));
        assertNoDomainMutation(app);
    }

    // ---- D3-C: approved under v1, executed under v2 --------------------------------------------------------------

    @Test @DisplayName("D3-C version-1 approved proposal to B12, then policy 2: STALE, no domain mutation")
    void d3cStaleB12() {
        String id = agentProposes(app, "B12");
        assertEquals(1, app.proposal(id).orElseThrow().getPolicyVersion());
        assertEquals(DecisionOutcome.APPROVED, app.approve(id));
        app.activatePolicyV2();
        assertEquals(ExecutionOutcome.STALE, app.execute(id));
        assertNoDomainMutation(app);
        assertEquals(ProposalStatus.APPROVED, app.proposalStatus(id).orElseThrow());
    }

    @Test @DisplayName("D3-C version-1 approved proposal to C03 is STALE too, although C03 is still eligible under v2")
    void d3cStaleEvenIfStillEligible() {
        String id = agentProposes(app, "C03");
        assertEquals(DecisionOutcome.APPROVED, app.approve(id));
        app.activatePolicyV2();
        assertEquals(ExecutionOutcome.STALE, app.execute(id));
        assertNoDomainMutation(app);
    }

    @Test @DisplayName("D3-C a stale proposal stays stale: repeated execute is STALE every time, never a commit")
    void d3cStaleRepeated() {
        String id = agentProposes(app, "B12");
        app.approve(id);
        app.activatePolicyV2();
        assertEquals(ExecutionOutcome.STALE, app.execute(id));
        assertEquals(ExecutionOutcome.STALE, app.execute(id));
        assertNoDomainMutation(app);
    }

    @Test @DisplayName("D3-C investigation read under v1 and proposing after the switch must restart; fresh v2 proposal is C03")
    void d3cFreshInvestigationRequired() {
        List<String> script = List.of(READ, LIST, propose("B12"), READ, LIST, propose("C03"));
        int[] call = {0};
        ModelProvider provider = history -> {
            if (call[0] == 2) app.activatePolicyV2();   // policy changes between LIST and PROPOSE
            return script.get(call[0]++);
        };
        RunResult r = app.runAgent(provider, LIMIT);
        assertTrue(observationAfterCall(r, 3).contains("STALE_INVESTIGATION"), observationAfterCall(r, 3));
        assertTrue(observationAfterCall(r, 5).contains("\"candidates\":[\"C03\"]"), observationAfterCall(r, 5));
        assertEquals(RunStatus.AWAITING_APPROVAL, r.status());
        Proposal p = app.proposal(r.proposalId().orElseThrow()).orElseThrow();
        assertEquals("C03", p.getTargetId().getValue());
        assertEquals(2, p.getPolicyVersion());
        assertNoDomainMutation(app);
    }

    // ---- D3-D / D3-H: B12 under v2 is refused by Java ------------------------------------------------------------

    @Test @DisplayName("D3-D fresh version-2 proposal to B12: rejected, no proposal, no commit")
    void d3dFreshB12Rejected() {
        app.activatePolicyV2();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> app.propose("B12"));
        assertTrue(ex.getMessage().contains("NOT_ACCESSIBLE"), ex.getMessage());
        assertTrue(app.proposal("PROP_1").isEmpty(), "no proposal may be stored");
        assertNoDomainMutation(app);
    }

    @Test @DisplayName("D3-H model ignores the v2 candidates and proposes B12: Java rejects it despite the model output")
    void d3hModelProposesB12() {
        app.activatePolicyV2();
        RunResult r = app.runAgent(new ReplayProvider(List.of(READ, LIST, propose("B12"), FINISH)), LIMIT);
        assertTrue(observationAfterCall(r, 2).contains("\"candidates\":[\"C03\"]"), "model was told only C03");
        String obs = observationAfterCall(r, 3);
        assertTrue(obs.contains("TOOL_REJECTED") && obs.contains("NOT_ACCESSIBLE"), obs);
        assertEquals(RunStatus.NO_ACTION, r.status());
        assertEquals(1, r.toolRejections());
        assertTrue(r.proposalId().isEmpty());
        assertTrue(app.proposal("PROP_1").isEmpty(), "no proposal may be stored");
        assertNoDomainMutation(app);
    }

    // ---- D3-E / D3-F: fresh v2 proposal commits exactly once -----------------------------------------------------

    @Test @DisplayName("D3-E fresh version-2 proposal to C03, approve, execute: one commit to C03")
    void d3eCommitC03() {
        app.activatePolicyV2();
        String id = agentProposes(app, "C03");
        assertEquals(2, app.proposal(id).orElseThrow().getPolicyVersion());
        assertB1(app, "A17", 0);                                   // proposing alone moves nothing
        assertEquals(DecisionOutcome.APPROVED, app.approve(id));
        assertEquals(ExecutionOutcome.COMMITTED, app.execute(id));
        assertB1(app, "C03", 1);
        assertEquals(1, app.commitCount());
        assertEquals(1, app.notificationCount());
        assertEquals(ProposalStatus.EXECUTED, app.proposalStatus(id).orElseThrow());
    }

    @Test @DisplayName("D3-F repeat the D3-E execution: DUPLICATE, no second commit or notification")
    void d3fRepeatExecution() {
        app.activatePolicyV2();
        String id = agentProposes(app, "C03");
        app.approve(id);
        assertEquals(ExecutionOutcome.COMMITTED, app.execute(id));
        assertEquals(ExecutionOutcome.DUPLICATE, app.execute(id));
        assertEquals(ExecutionOutcome.DUPLICATE, app.execute(id));
        assertB1(app, "C03", 1);
        assertEquals(1, app.commitCount());
        assertEquals(1, app.notificationCount());
    }

    @Test @DisplayName("D3-C then D3-E: stale v1 approval refused, then a fresh v2 proposal commits once")
    void staleThenFreshCommit() {
        String old = agentProposes(app, "B12");
        app.approve(old);
        app.activatePolicyV2();
        assertEquals(ExecutionOutcome.STALE, app.execute(old));
        String fresh = agentProposes(app, "C03");
        app.approve(fresh);
        assertEquals(ExecutionOutcome.COMMITTED, app.execute(fresh));
        assertEquals(ExecutionOutcome.STALE, app.execute(old));   // old one still never runs
        assertB1(app, "C03", 1);
        assertEquals(1, app.commitCount());
    }

    // ---- D3-G: booking without the accessibility requirement -----------------------------------------------------

    @Test @DisplayName("D3-G non-accessibility-requiring booking under v2: B12 and C03 offered; B12 commits")
    void d3gGeneralAlternativesRemain() {
        StudentApplication variant = StudentApplication.withoutAccessibilityRequirement();
        variant.activatePolicyV2();
        RunResult r = variant.runAgent(new ReplayProvider(List.of(READ, LIST, propose("B12"))), LIMIT);
        assertTrue(observationAfterCall(r, 2).contains("\"candidates\":[\"B12\",\"C03\"]"), observationAfterCall(r, 2));
        String id = r.proposalId().orElseThrow();
        assertEquals(2, variant.proposal(id).orElseThrow().getPolicyVersion());
        variant.approve(id);
        assertEquals(ExecutionOutcome.COMMITTED, variant.execute(id));
        assertB1(variant, "B12", 1);
        assertEquals(1, variant.commitCount());
    }

    @Test @DisplayName("D3-G general availability is still enforced under v2: occupied D09 and current A17 refused")
    void d3gAvailabilityStillChecked() {
        StudentApplication variant = StudentApplication.withoutAccessibilityRequirement();
        variant.activatePolicyV2();
        assertTrue(assertThrows(IllegalArgumentException.class, () -> variant.propose("D09")).getMessage().contains("OCCUPIED"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> variant.propose("A17")).getMessage().contains("CURRENT_SPACE"));
        assertNoDomainMutation(variant);
    }

    // ---- D3-I: periodic checker across the version change --------------------------------------------------------

    @Test @DisplayName("D3-I tick after policy-version change: new work emitted once for (B1 v0, policy v2)")
    void d3iPeriodicAfterChange() {
        List<WorkItem> first = app.tick(0).workItems();
        assertEquals(1, first.size());
        assertEquals(1, first.get(0).policyVersion());
        assertTrue(app.tick(60).workItems().isEmpty(), "same pair is not emitted twice");

        app.activatePolicyV2();
        assertEquals(TickResult.Status.NOT_DUE, app.tick(90).status());   // activation does not force a scan

        TickResult after = app.tick(120);
        assertEquals(TickResult.Status.SCANNED, after.status());
        assertEquals(1, after.workItems().size());
        WorkItem w = after.workItems().get(0);
        assertEquals("B1", w.bookingId().getValue());
        assertEquals("A17", w.closedSpaceId().getValue());
        assertEquals(0, w.bookingVersion());
        assertEquals(2, w.policyVersion());

        assertTrue(app.tick(180).workItems().isEmpty(), "the new pair is emitted only once");
        assertNoDomainMutation(app);
    }

    @Test @DisplayName("D3-I a pending v1 proposal does not hide v2 work, but a pending v2 proposal does")
    void d3iActiveProposalByVersion() {
        agentProposes(app, "B12");                                   // PENDING at policy v1
        assertTrue(app.tick(0).workItems().isEmpty(), "v1 pair already covered by a pending v1 proposal");
        app.activatePolicyV2();
        assertEquals(1, app.tick(60).workItems().size(), "the v1 proposal does not cover the v2 pair");

        StudentApplication other = new StudentApplication();
        other.activatePolicyV2();
        agentProposes(other, "C03");                                 // PENDING at policy v2
        assertTrue(other.tick(0).workItems().isEmpty(), "v2 pair already covered by a pending v2 proposal");
    }
}
