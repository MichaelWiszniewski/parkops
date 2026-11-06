import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** D2 Step 4: bounded model/tool loop (acceptance cases D2-A, D2-H, D2-I, D2-J, D2-K). */
class AgentRunnerTests {
    private static final Identifier B1 = new Identifier("B1");
    private final ParkingRegistry registry = StudentApplication.fixtureRegistry();
    private final ProposalStore store = new ProposalStore();
    private final ReassignmentPolicy policy = new PolicyV1();
    private final ProposalManager manager = new ProposalManager(registry, store, policy, new SequentialIdGenerator());

    private static final String READ = req("READ_BOOKING", ""), LIST = req("LIST_ALTERNATIVES", ""),
            FINISH = req("FINISH", "");
    private static String req(String tool, String target) {
        return "{\"tool\":\"" + tool + "\",\"bookingId\":\"B1\",\"targetId\":\"" + target + "\"}";
    }
    private static String propose(String target) { return req("PROPOSE_REASSIGNMENT", target); }

    private RunResult run(ModelProvider provider, int limit) {
        InvestigationSession session = new InvestigationSession(B1, registry, manager, policy);
        return new AgentRunner(provider, new ToolRequestParser(B1), session, limit).run();
    }

    /** Booking B1 still at A17/version 0 and every space flag exactly as supplied. */
    private void assertDomainUnchanged() {
        Booking b = registry.findBooking(B1).orElseThrow();
        assertEquals("A17", b.getSpaceId().getValue());
        assertEquals(0, b.getVersion());
        assertSpace("A17", false, true); assertSpace("B12", true, false);
        assertSpace("C03", true, false); assertSpace("D09", true, true);
    }
    private void assertSpace(String id, boolean open, boolean occupied) {
        Space s = registry.findSpace(new Identifier(id)).orElseThrow();
        assertEquals(open, s.isOpen(), id + " open");
        assertEquals(occupied, s.isOccupied(), id + " occupied");
    }
    private void assertNoProposals() { assertEquals(List.of(), store.findByBooking(B1)); }

    // ---- D2-A ---------------------------------------------------------------------------------------------------

    @Test @DisplayName("D2-A replay read, list, propose B12: one PENDING proposal and no booking change")
    void d2aReplayHappyPath() {
        ReplayProvider replay = new ReplayProvider(List.of(READ, LIST, propose("B12")));
        RunResult r = run(replay, 6);

        assertEquals(RunStatus.AWAITING_APPROVAL, r.status());
        assertEquals(3, r.calls());
        assertEquals(3, replay.calls());
        List<Proposal> proposals = store.findByBooking(B1);
        assertEquals(1, proposals.size());
        Proposal p = proposals.get(0);
        assertEquals(r.proposalId().orElseThrow(), p.getId().getValue());
        assertEquals(ProposalStatus.PENDING, p.getStatus());
        assertEquals("B12", p.getTargetId().getValue());
        assertEquals(0, p.getBookingVersion());
        assertEquals(1, p.getPolicyVersion());
        assertDomainUnchanged();
    }

    @Test @DisplayName("D2-A each call receives the previous tool observation (successive observations)")
    void observationsFeedNextCall() {
        ReplayProvider replay = new ReplayProvider(List.of(READ, LIST, propose("B12")));
        run(replay, 6);
        List<List<ChatMessage>> h = replay.histories();
        assertEquals(2, h.get(0).size());   // system + goal only
        assertEquals(4, h.get(1).size());   // + model READ + its observation
        assertEquals(6, h.get(2).size());
        assertTrue(h.get(1).get(3).content().contains("\"version\":0"), "READ observation reaches call 2");
        assertTrue(h.get(2).get(5).content().contains("\"candidates\":[\"B12\",\"C03\"]"), "LIST observation reaches call 3");
        assertEquals("user", h.get(1).get(3).role());
    }

    @Test @DisplayName("A model may choose C03 instead of B12; it is also eligible under policy 1")
    void c03AlsoAccepted() {
        RunResult r = run(new ReplayProvider(List.of(READ, LIST, propose("C03"))), 6);
        assertEquals(RunStatus.AWAITING_APPROVAL, r.status());
        assertEquals("C03", store.findByBooking(B1).get(0).getTargetId().getValue());
        assertDomainUnchanged();
    }

    // ---- D2-H ---------------------------------------------------------------------------------------------------

    @Test @DisplayName("D2-H malformed JSON is rejected, counts as an attempt, and the agent can recover")
    void malformedCountedThenRecovers() {
        RunResult r = run(new ReplayProvider(List.of("not json", READ, LIST, propose("B12"))), 6);
        assertEquals(RunStatus.AWAITING_APPROVAL, r.status());
        assertEquals(4, r.calls());
        assertEquals(1, r.invalidResponses());
        assertTrue(r.transcript().get(3).content().contains("MALFORMED_JSON"));
    }

    @Test @DisplayName("D2-H approved:true is rejected, counted, and grants no authority")
    void approvedFieldGrantsNothing() {
        String sneaky = "{\"tool\":\"PROPOSE_REASSIGNMENT\",\"bookingId\":\"B1\",\"targetId\":\"B12\",\"approved\":true}";
        RunResult r = run(new ReplayProvider(List.of(READ, LIST, sneaky)), 3);
        assertEquals(RunStatus.STEP_LIMIT, r.status());
        assertEquals(1, r.invalidResponses());
        assertNoProposals();
        assertDomainUnchanged();
    }

    // ---- D2-I ---------------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "rejected request: {0}")
    @ValueSource(strings = {"{\"tool\":\"EXECUTE\",\"bookingId\":\"B1\",\"targetId\":\"\"}",
            "{\"tool\":\"DELETE_BOOKING\",\"bookingId\":\"B1\",\"targetId\":\"\"}",
            "{\"tool\":\"READ_BOOKING\",\"bookingId\":\"B2\",\"targetId\":\"\"}",
            "{\"tool\":\"PROPOSE_REASSIGNMENT\",\"bookingId\":\"B2\",\"targetId\":\"B12\"}"})
    @DisplayName("D2-I unknown tool or wrong booking: controlled rejection, nothing dispatched")
    void unknownToolOrWrongBooking(String raw) {
        RunResult r = run(new ReplayProvider(List.of(raw)), 1);
        assertEquals(RunStatus.STEP_LIMIT, r.status());
        assertEquals(1, r.invalidResponses());
        assertTrue(r.transcript().get(3).content().contains("INVALID_REQUEST"));
        assertNoProposals();
        assertDomainUnchanged();
    }

    // ---- tool order and domain-level tool rejections ------------------------------------------------------------

    @Test @DisplayName("LIST before READ, and PROPOSE before LIST, are rejected as ORDER errors")
    void orderEnforced() {
        RunResult r = run(new ReplayProvider(List.of(LIST, READ, propose("B12"))), 3);
        assertEquals(RunStatus.STEP_LIMIT, r.status());
        assertEquals(2, r.toolRejections());
        assertEquals(0, r.invalidResponses());
        assertTrue(r.transcript().get(3).content().contains("\"error\":\"ORDER\""));
        assertTrue(r.transcript().get(7).content().contains("\"error\":\"ORDER\""));
        assertNoProposals();
    }

    @Test @DisplayName("An ineligible or unknown target is a tool rejection; the agent can then propose a valid one")
    void ineligibleTargetThenRecovers() {
        RunResult r = run(new ReplayProvider(List.of(READ, LIST, propose("D09"), propose("Z99"), propose("C03"))), 6);
        assertEquals(RunStatus.AWAITING_APPROVAL, r.status());
        assertEquals(2, r.toolRejections());
        assertTrue(r.transcript().get(7).content().contains("OCCUPIED"));
        assertTrue(r.transcript().get(9).content().contains("TARGET_UNKNOWN"));
        assertEquals(1, store.findByBooking(B1).size());
        assertDomainUnchanged();
    }

    @Test @DisplayName("FINISH ends the run with NO_ACTION and no proposal")
    void finishIsNoAction() {
        RunResult r = run(new ReplayProvider(List.of(READ, FINISH)), 6);
        assertEquals(RunStatus.NO_ACTION, r.status());
        assertEquals(2, r.calls());
        assertNoProposals();
        assertDomainUnchanged();
    }

    // ---- D2-J ---------------------------------------------------------------------------------------------------

    @Test @DisplayName("D2-J six invalid responses: STEP_LIMIT after exactly six calls")
    void sixInvalidIsStepLimit() {
        List<String> bad = new ArrayList<>();
        for (int i = 0; i < 7; i++) bad.add("invalid " + i);   // a seventh is available but must never be requested
        ReplayProvider replay = new ReplayProvider(bad);
        RunResult r = run(replay, 6);
        assertEquals(RunStatus.STEP_LIMIT, r.status());
        assertEquals(6, r.calls());
        assertEquals(6, replay.calls());
        assertEquals(6, r.invalidResponses());
        assertNoProposals();
        assertDomainUnchanged();
    }

    @Test @DisplayName("A limit of 1 allows exactly one call")
    void limitOne() {
        ReplayProvider replay = new ReplayProvider(List.of(READ, LIST));
        RunResult r = run(replay, 1);
        assertEquals(RunStatus.STEP_LIMIT, r.status());
        assertEquals(1, replay.calls());
    }

    @ParameterizedTest(name = "limit {0}")
    @ValueSource(ints = {-1, 0, 7, 100})
    @DisplayName("Limits outside 1..6 are refused before any call")
    void limitOutOfRange(int limit) {
        ReplayProvider replay = new ReplayProvider(List.of(READ));
        assertThrows(IllegalArgumentException.class, () -> run(replay, limit));
        assertEquals(0, replay.calls());
    }

    // ---- D2-K ---------------------------------------------------------------------------------------------------

    @Test @DisplayName("D2-K provider IOException: PROVIDER_ERROR and no commit")
    void providerThrows() {
        RunResult r = run(history -> { throw new IOException("service down"); }, 6);
        assertEquals(RunStatus.PROVIDER_ERROR, r.status());
        assertEquals(1, r.calls());
        assertNoProposals();
        assertDomainUnchanged();
    }

    @Test @DisplayName("D2-K provider timeout after a successful read: PROVIDER_ERROR and no proposal")
    void providerTimesOut() {
        int[] n = {0};
        RunResult r = run(history -> {
            if (n[0]++ == 0) return READ;
            throw new HttpTimeoutException("request timed out");
        }, 6);
        assertEquals(RunStatus.PROVIDER_ERROR, r.status());
        assertEquals(2, r.calls());
        assertNoProposals();
        assertDomainUnchanged();
    }

    @Test @DisplayName("D2-K unchecked exceptions and interruption are also PROVIDER_ERROR")
    void providerRuntimeAndInterrupt() {
        assertEquals(RunStatus.PROVIDER_ERROR, run(h -> { throw new IllegalStateException("bad"); }, 6).status());
        assertEquals(RunStatus.PROVIDER_ERROR, run(h -> { throw new InterruptedException(); }, 6).status());
        assertTrue(Thread.interrupted(), "interrupt flag is restored");   // also clears it for later tests
        assertNoProposals();
    }

    @Test @DisplayName("Replay running out of responses is a provider error, not a hang or a commit")
    void replayExhausted() {
        RunResult r = run(new ReplayProvider(List.of(READ)), 6);
        assertEquals(RunStatus.PROVIDER_ERROR, r.status());
        assertEquals(2, r.calls());
    }

    // ---- StudentApplication entry point --------------------------------------------------------------------------

    @Test @DisplayName("StudentApplication.run prints the transcript and outcome without moving the booking")
    void applicationEntryPoint() {
        StudentApplication app = new StudentApplication();
        String out = app.run(Fixture.replay(), 6);
        assertTrue(out.contains("OUTCOME AWAITING_APPROVAL proposal=PROP_1 calls=3"), out);
        assertEquals("A17", app.bookingSnapshot().get("spaceId"));
        assertEquals(0, app.bookingSnapshot().get("version"));
    }
}
