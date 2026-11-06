import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** D2 Step 5: operator approval, execution and duplicate protection (D2-B to D2-G) and the transition table. */
class ApprovalExecutionTests {
    private static final Identifier B1 = new Identifier("B1");

    /** Policy 1 rules with a version the test can change, to simulate a policy update after proposing. */
    static final class VersionedPolicy implements ReassignmentPolicy {
        private final PolicyV1 rules = new PolicyV1();
        int version = 1;
        @Override public PolicyVerdict evaluate(Booking b, Space s) { return rules.evaluate(b, s); }
        @Override public int getVersion() { return version; }
    }

    private ParkingRegistry registry;
    private ProposalStore store;
    private VersionedPolicy policy;
    private CommitLog commits;
    private NotificationLog notifications;
    private ProposalManager manager;

    @BeforeEach void setUp() {
        registry = StudentApplication.fixtureRegistry();
        store = new ProposalStore();
        policy = new VersionedPolicy();
        commits = new CommitLog();
        notifications = new NotificationLog();
        manager = new ProposalManager(registry, store, policy, new SequentialIdGenerator(), commits, notifications);
    }

    /** Proposes a target for B1 and returns the new proposal id. */
    private String propose(String target) {
        return assertInstanceOf(Created.class, manager.proposeReassignment(target, "B1")).getProposal().getId().getValue();
    }
    private ProposalStatus status(String id) { return manager.findProposal(id).orElseThrow().getStatus(); }

    /** Every piece of protected state: booking location/version, all space flags, both logs, all proposal statuses. */
    private Map<String, Object> protectedState() {
        Map<String, Object> s = new LinkedHashMap<>();
        Booking b = registry.findBooking(B1).orElseThrow();
        s.put("booking", b.getSpaceId().getValue() + "@v" + b.getVersion());
        for (Space sp : registry.allSpaces())
            s.put(sp.getId().getValue(), "open=" + sp.isOpen() + ",occupied=" + sp.isOccupied());
        s.put("commits", commits.count());
        s.put("notifications", notifications.count());
        for (Proposal p : store.findByBooking(B1)) s.put(p.getId().getValue(), p.getStatus());
        return s;
    }
    private void assertBooking(String space, int version) {
        Booking b = registry.findBooking(B1).orElseThrow();
        assertEquals(space, b.getSpaceId().getValue());
        assertEquals(version, b.getVersion());
    }
    private boolean occupied(String id) { return registry.findSpace(new Identifier(id)).orElseThrow().isOccupied(); }

    // ---- acceptance cases ---------------------------------------------------------------------------------------

    @Test @DisplayName("D2-B execute a PENDING proposal: NOT_APPROVED, all protected state unchanged")
    void d2bPendingNotApproved() {
        String id = propose("B12");
        Map<String, Object> before = protectedState();
        assertEquals(ExecutionOutcome.NOT_APPROVED, manager.execute(id));
        assertEquals(before, protectedState());
        assertEquals(ProposalStatus.PENDING, status(id));
        assertBooking("A17", 0);
    }

    @Test @DisplayName("D2-C approve then execute: one COMMITTED, B1 at B12/version 1, one notification")
    void d2cApproveExecuteCommits() {
        String id = propose("B12");
        assertEquals(DecisionOutcome.APPROVED, manager.approve(id));
        assertBooking("A17", 0);   // approval alone moves nothing

        assertEquals(ExecutionOutcome.COMMITTED, manager.execute(id));
        assertBooking("B12", 1);
        assertFalse(occupied("A17"), "old occupancy released");
        assertTrue(occupied("B12"), "target occupied");
        assertFalse(occupied("C03"));
        assertTrue(occupied("D09"));
        assertEquals(ProposalStatus.EXECUTED, status(id));
        assertEquals(1, commits.count());
        assertEquals(1, notifications.count());
        CommitRecord c = commits.all().get(0);
        assertEquals(List.of(id, "B1", "A17", "B12", 1), List.of(c.proposalId().getValue(), c.bookingId().getValue(),
                c.fromSpace().getValue(), c.toSpace().getValue(), c.newVersion()));
        assertEquals(id, notifications.all().get(0).proposalId().getValue());
    }

    @Test @DisplayName("D2-D execute the same committed proposal again: DUPLICATE, counts and state unchanged")
    void d2dDuplicate() {
        String id = propose("B12");
        manager.approve(id);
        assertEquals(ExecutionOutcome.COMMITTED, manager.execute(id));
        Map<String, Object> afterCommit = protectedState();
        for (int i = 0; i < 3; i++) assertEquals(ExecutionOutcome.DUPLICATE, manager.execute(id));
        assertEquals(afterCommit, protectedState());
        assertEquals(1, commits.count());
        assertEquals(1, notifications.count());
        assertBooking("B12", 1);
    }

    @Test @DisplayName("D2-E reject a PENDING proposal then execute: no commit, booking unchanged")
    void d2eRejectThenExecute() {
        String id = propose("B12");
        Map<String, Object> before = protectedState();
        assertEquals(DecisionOutcome.REJECTED, manager.reject(id));
        assertEquals(ExecutionOutcome.NOT_APPROVED, manager.execute(id));
        before.put(id, ProposalStatus.REJECTED);   // the only permitted change is the proposal's own status
        assertEquals(before, protectedState());
        assertEquals(0, commits.count());
        assertBooking("A17", 0);
    }

    @Test @DisplayName("D2-F booking version changes after proposing: STALE, no move, proposal stays APPROVED")
    void d2fStaleBookingVersion() {
        String first = propose("B12");
        String second = propose("C03");   // both captured booking version 0
        manager.approve(first);
        manager.approve(second);
        assertEquals(ExecutionOutcome.COMMITTED, manager.execute(second));   // B1 -> C03, version 1
        Map<String, Object> before = protectedState();

        assertEquals(ExecutionOutcome.STALE, manager.execute(first));
        assertEquals(before, protectedState());
        assertBooking("C03", 1);
        assertFalse(occupied("B12"), "stale target was not occupied");
        assertEquals(ProposalStatus.APPROVED, status(first));
        assertEquals(ExecutionOutcome.STALE, manager.execute(first), "versions only increase: stays stale");
    }

    @Test @DisplayName("D2-F policy version changes after proposing: STALE, no move")
    void d2fStalePolicyVersion() {
        String id = propose("B12");
        manager.approve(id);
        policy.version = 2;
        Map<String, Object> before = protectedState();
        assertEquals(ExecutionOutcome.STALE, manager.execute(id));
        assertEquals(before, protectedState());
        assertBooking("A17", 0);
    }

    @Test @DisplayName("D2-G target closes after proposing: UNAVAILABLE_OR_INELIGIBLE, no move")
    void d2gTargetClosed() {
        String id = propose("B12");
        manager.approve(id);
        registry.closeSpace(new Identifier("B12"));
        Map<String, Object> before = protectedState();
        assertEquals(ExecutionOutcome.UNAVAILABLE_OR_INELIGIBLE, manager.execute(id));
        assertEquals(before, protectedState());
        assertBooking("A17", 0);
        assertEquals(ProposalStatus.APPROVED, status(id));
    }

    @Test @DisplayName("D2-G target becomes occupied after proposing: UNAVAILABLE_OR_INELIGIBLE, no move")
    void d2gTargetOccupied() {
        String id = propose("C03");
        manager.approve(id);
        registry.occupySpace(new Identifier("C03"));
        Map<String, Object> before = protectedState();
        assertEquals(ExecutionOutcome.UNAVAILABLE_OR_INELIGIBLE, manager.execute(id));
        assertEquals(before, protectedState());
        assertEquals(0, commits.count());
    }

    // ---- the transition table, cell by cell ---------------------------------------------------------------------

    @Nested @DisplayName("Transition table")
    class TransitionTable {
        private String inState(ProposalStatus s) {
            String id = propose("B12");
            switch (s) {
                case PENDING -> { }
                case APPROVED -> manager.approve(id);
                case REJECTED -> manager.reject(id);
                case EXECUTED -> { manager.approve(id); assertEquals(ExecutionOutcome.COMMITTED, manager.execute(id)); }
            }
            assertEquals(s, status(id));
            return id;
        }

        @Test @DisplayName("PENDING: approve -> APPROVED, reject -> REJECTED, execute -> stays PENDING (NOT_APPROVED)")
        void pendingRow() {
            assertEquals(DecisionOutcome.APPROVED, manager.approve(inState(ProposalStatus.PENDING)));
            setUp();
            String r = inState(ProposalStatus.PENDING);
            assertEquals(DecisionOutcome.REJECTED, manager.reject(r));
            assertEquals(ProposalStatus.REJECTED, status(r));
            setUp();
            String e = inState(ProposalStatus.PENDING);
            assertEquals(ExecutionOutcome.NOT_APPROVED, manager.execute(e));
            assertEquals(ProposalStatus.PENDING, status(e));
        }

        @ParameterizedTest(name = "{0}: approve and reject refused, state unchanged")
        @ValueSource(strings = {"APPROVED", "REJECTED", "EXECUTED"})
        @DisplayName("approve/reject from any non-PENDING state: NOT_PENDING and nothing changes")
        void decisionsRefusedOutsidePending(String state) {
            String id = inState(ProposalStatus.valueOf(state));
            Map<String, Object> before = protectedState();
            assertEquals(DecisionOutcome.NOT_PENDING, manager.approve(id));
            assertEquals(DecisionOutcome.NOT_PENDING, manager.reject(id));
            assertEquals(before, protectedState());
        }

        @Test @DisplayName("REJECTED: execute -> stays REJECTED (NOT_APPROVED)")
        void rejectedExecute() {
            String id = inState(ProposalStatus.REJECTED);
            assertEquals(ExecutionOutcome.NOT_APPROVED, manager.execute(id));
            assertEquals(ProposalStatus.REJECTED, status(id));
        }

        @Test @DisplayName("EXECUTED: execute -> stays EXECUTED (DUPLICATE), no second commit or notification")
        void executedExecute() {
            String id = inState(ProposalStatus.EXECUTED);
            assertEquals(ExecutionOutcome.DUPLICATE, manager.execute(id));
            assertEquals(ProposalStatus.EXECUTED, status(id));
            assertEquals(1, commits.count());
            assertEquals(1, notifications.count());
        }
    }

    // ---- identity and guard rails -------------------------------------------------------------------------------

    @ParameterizedTest(name = "id: \"{0}\"")
    @ValueSource(strings = {"PROP_99", "prop_1", "", "B1"})
    @DisplayName("Unknown or malformed proposal ids are refused by every operator action")
    void unknownProposal(String id) {
        propose("B12");
        Map<String, Object> before = protectedState();
        assertEquals(DecisionOutcome.UNKNOWN_PROPOSAL, manager.approve(id));
        assertEquals(DecisionOutcome.UNKNOWN_PROPOSAL, manager.reject(id));
        assertEquals(ExecutionOutcome.UNKNOWN_PROPOSAL, manager.execute(id));
        assertEquals(before, protectedState());
    }

    @Test @DisplayName("Null proposal id is refused, not thrown")
    void nullProposalId() {
        assertEquals(DecisionOutcome.UNKNOWN_PROPOSAL, manager.approve(null));
        assertEquals(ExecutionOutcome.UNKNOWN_PROPOSAL, manager.execute(null));
    }

    @Test @DisplayName("ProposalStore.replace can change only the status of an existing proposal")
    void storeReplaceGuard() {
        String id = propose("B12");
        Proposal p = store.get(new Identifier(id)).orElseThrow();
        Proposal otherTarget = new Proposal(p.getId(), B1, new Identifier("C03"), ProposalStatus.APPROVED, 0, 1);
        Proposal otherVersion = new Proposal(p.getId(), B1, p.getTargetId(), ProposalStatus.APPROVED, 5, 1);
        Proposal unknown = new Proposal(new Identifier("PROP_9"), B1, p.getTargetId(), ProposalStatus.EXECUTED, 0, 1);
        assertThrows(IllegalStateException.class, () -> store.replace(otherTarget));
        assertThrows(IllegalStateException.class, () -> store.replace(otherVersion));
        assertThrows(IllegalStateException.class, () -> store.replace(unknown));
        assertEquals(ProposalStatus.PENDING, status(id));
        assertEquals("B12", store.get(new Identifier(id)).orElseThrow().getTargetId().getValue());
    }

    @Test @DisplayName("withStatus returns a new snapshot and leaves the original unchanged")
    void withStatusIsImmutable() {
        Proposal p = store.get(new Identifier(propose("B12"))).orElseThrow();
        Proposal q = p.withStatus(ProposalStatus.APPROVED);
        assertNotSame(p, q);
        assertEquals(ProposalStatus.PENDING, p.getStatus());
        assertEquals(ProposalStatus.APPROVED, q.getStatus());
    }

    @Test @DisplayName("Registry moveBooking refuses an unavailable target and writes nothing")
    void registryMoveIsAllOrNothing() {
        Map<String, Object> before = protectedState();
        assertThrows(IllegalStateException.class, () -> registry.moveBooking(B1, new Identifier("D09"), 0));
        assertThrows(IllegalStateException.class, () -> registry.moveBooking(B1, new Identifier("B12"), 7));
        assertThrows(IllegalStateException.class, () -> registry.moveBooking(B1, new Identifier("A17"), 0));
        assertEquals(before, protectedState());
    }

    @Test @DisplayName("CommitLog refuses a second commit for the same proposal")
    void commitLogOnePerProposal() {
        CommitRecord c = new CommitRecord(new Identifier("PROP_1"), B1, new Identifier("A17"), new Identifier("B12"), 1);
        commits.add(c);
        assertThrows(IllegalStateException.class, () -> commits.add(c));
        assertEquals(1, commits.count());
    }

    // ---- deferred from Step 4: stale versions during an agent investigation --------------------------------------

    @Test @DisplayName("Booking version changes mid-investigation: LIST is rejected as STALE_INVESTIGATION, agent re-reads")
    void staleInvestigationRestarts() {
        String read = "{\"tool\":\"READ_BOOKING\",\"bookingId\":\"B1\",\"targetId\":\"\"}";
        String list = "{\"tool\":\"LIST_ALTERNATIVES\",\"bookingId\":\"B1\",\"targetId\":\"\"}";
        int[] n = {0};
        ModelProvider provider = history -> switch (n[0]++) {
            case 0 -> read;
            case 1 -> {   // an operator commits a different proposal while the agent is mid-investigation
                String other = propose("C03");
                manager.approve(other);
                assertEquals(ExecutionOutcome.COMMITTED, manager.execute(other));
                yield list;
            }
            case 2 -> list;   // still stale: the agent must READ again first
            case 3 -> read;
            default -> list;
        };
        InvestigationSession session = new InvestigationSession(B1, registry, manager, policy);
        RunResult r = new AgentRunner(provider, new ToolRequestParser(B1), session, 5).run();

        assertTrue(r.transcript().get(5).content().contains("STALE_INVESTIGATION"), "first LIST after the change");
        assertTrue(r.transcript().get(7).content().contains("\"error\":\"ORDER\""), "LIST before a fresh READ");
        assertTrue(r.transcript().get(9).content().contains("\"version\":1"), "fresh READ sees version 1");
        // B1 now sits on C03; A17 is free but still closed, D09 occupied: only B12 remains eligible.
        assertTrue(r.transcript().get(11).content().contains("\"candidates\":[\"B12\"]"), r.transcript().get(11).content());
        assertEquals(2, r.toolRejections());
    }

    // ---- application-level operator flow ------------------------------------------------------------------------

    @Test @DisplayName("StudentApplication: agent run, then separate operator approve and execute")
    void applicationOperatorFlow() {
        StudentApplication app = new StudentApplication();
        RunResult r = app.runAgent(Fixture.replay(), 6);
        String id = r.proposalId().orElseThrow();
        assertEquals(0, app.commitCount());
        assertEquals(ExecutionOutcome.NOT_APPROVED, app.execute(id));
        assertEquals(DecisionOutcome.APPROVED, app.approve(id));
        assertEquals(ExecutionOutcome.COMMITTED, app.execute(id));
        assertEquals(ExecutionOutcome.DUPLICATE, app.execute(id));
        assertEquals("B12", app.bookingSnapshot().get("spaceId"));
        assertEquals(1, app.bookingSnapshot().get("version"));
        assertEquals(1, app.commitCount());
        assertEquals(1, app.notificationCount());
    }
}
