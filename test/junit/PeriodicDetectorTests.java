import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D2 Step 6: periodic detection with controllable time (D2-L, D2-M) and the conservative retry policy. */
class PeriodicDetectorTests {
    private static final Identifier B1 = new Identifier("B1");

    private ParkingRegistry registry;
    private ApprovalExecutionTests.VersionedPolicy policy;
    private ProposalManager manager;
    private PeriodicDetector detector;

    @BeforeEach void setUp() {
        registry = StudentApplication.fixtureRegistry();
        policy = new ApprovalExecutionTests.VersionedPolicy();
        manager = new ProposalManager(registry, new ProposalStore(), policy, new SequentialIdGenerator(),
                new CommitLog(), new NotificationLog());
        detector = new PeriodicDetector(registry, manager, policy);
    }

    private TickResult.Status status(long t) { return detector.tick(t).status(); }
    private List<WorkItem> work(long t) {
        TickResult r = detector.tick(t);
        assertEquals(TickResult.Status.SCANNED, r.status(), "tick " + t + " should scan");
        return r.workItems();
    }
    private String bookingState() {
        Booking b = registry.findBooking(B1).orElseThrow();
        return b.getSpaceId().getValue() + "@v" + b.getVersion() + " proposals=" + manager.findProposal("PROP_1").isPresent();
    }

    // ---- D2-L ---------------------------------------------------------------------------------------------------

    @Test @DisplayName("D2-L ticks 0, 59, 60 with unchanged versions: exactly one work item in total")
    void d2lBoundaryTicks() {
        String before = bookingState();
        TickResult t0 = detector.tick(0), t59 = detector.tick(59), t60 = detector.tick(60);

        assertEquals(TickResult.Status.SCANNED, t0.status());
        assertEquals(List.of(new WorkItem(B1, new Identifier("A17"), 0, 1)), t0.workItems());
        assertEquals(TickResult.Status.NOT_DUE, t59.status());
        assertEquals(List.of(), t59.workItems());
        assertEquals(TickResult.Status.SCANNED, t60.status(), "the boundary tick scans");
        assertEquals(List.of(), t60.workItems(), "same versions: suppressed");
        assertEquals(1, t0.workItems().size() + t59.workItems().size() + t60.workItems().size());
        assertEquals(before, bookingState(), "detection never creates a proposal or moves a booking");
    }

    @Test @DisplayName("Many later boundary ticks with unchanged versions emit nothing more")
    void repeatedTicksNoDuplicates() {
        assertEquals(1, work(0).size());
        for (long t = 60; t <= 600; t += 60) assertEquals(List.of(), work(t));
        assertFalse(manager.findProposal("PROP_1").isPresent());
    }

    // ---- D2-M ---------------------------------------------------------------------------------------------------

    @Test @DisplayName("D2-M a tick below the previous time is rejected and does not move the schedule")
    void d2mBackwardsClock() {
        work(0);
        assertEquals(TickResult.Status.NOT_DUE, status(50));
        TickResult back = detector.tick(49);
        assertEquals(TickResult.Status.CLOCK_REJECTED, back.status());
        assertEquals(List.of(), back.workItems());
        assertEquals(60, detector.nextDue().getAsLong(), "schedule unchanged");
        assertEquals(TickResult.Status.CLOCK_REJECTED, status(-1));
        assertEquals(TickResult.Status.NOT_DUE, status(59), "later valid ticks still work");
        assertEquals(TickResult.Status.SCANNED, status(60));
    }

    @Test @DisplayName("A rejected backwards tick after a scan does not let a later tick rescan early")
    void backwardsAfterBoundary() {
        work(0); work(60);
        assertEquals(TickResult.Status.CLOCK_REJECTED, status(30));
        assertEquals(TickResult.Status.NOT_DUE, status(119));
        assertEquals(TickResult.Status.SCANNED, status(120));
    }

    @Test @DisplayName("Repeating the same time is not backwards: NOT_DUE")
    void sameTimeIsNotBackwards() {
        work(0);
        assertEquals(TickResult.Status.NOT_DUE, status(0));
    }

    // ---- schedule -----------------------------------------------------------------------------------------------

    @Test @DisplayName("The first tick scans immediately at any start time; boundaries are start + k*60")
    void boundariesFromFirstTick() {
        work(1000);
        assertEquals(1060, detector.nextDue().getAsLong());
        assertEquals(TickResult.Status.NOT_DUE, status(1059));
        assertEquals(TickResult.Status.SCANNED, status(1250), "a late tick scans");
        assertEquals(1300, detector.nextDue().getAsLong(), "and the next boundary stays aligned");
        assertEquals(TickResult.Status.NOT_DUE, status(1299));
    }

    @Test @DisplayName("No schedule before the first tick")
    void noScheduleYet() {
        assertTrue(detector.nextDue().isEmpty());
    }

    // ---- suppression and retry policy ---------------------------------------------------------------------------

    @Test @DisplayName("An active PENDING proposal for the same versions suppresses the work item")
    void activeProposalSuppresses() {
        assertInstanceOf(Created.class, manager.proposeReassignment("B12", "B1"));
        assertEquals(List.of(), work(0));
    }

    @Test @DisplayName("An APPROVED proposal for the same versions also suppresses")
    void approvedProposalSuppresses() {
        manager.proposeReassignment("B12", "B1");
        manager.approve("PROP_1");
        assertEquals(List.of(), work(0));
    }

    @Test @DisplayName("Conservative policy: after a rejected attempt, no automatic re-emission for the same versions")
    void rejectedNotRetriedAutomatically() {
        assertEquals(1, work(0).size());
        manager.proposeReassignment("B12", "B1");
        manager.reject("PROP_1");
        assertEquals(List.of(), work(60));
        assertEquals(List.of(), work(120));
    }

    @Test @DisplayName("Explicit operator retry: the same booking is emitted again at the next due scan")
    void operatorRetry() {
        assertEquals(1, work(0).size());
        assertTrue(detector.retry(B1));
        assertEquals(TickResult.Status.NOT_DUE, status(30), "retry does not bypass the schedule");
        assertEquals(1, work(60).size());
        assertEquals(List.of(), work(120), "and only once");
        assertFalse(detector.retry(new Identifier("B9")), "unknown booking");
    }

    @Test @DisplayName("A policy version change is a new pair: emitted again")
    void policyVersionChangeReemits() {
        assertEquals(1, work(0).size());
        policy.version = 2;
        assertEquals(List.of(new WorkItem(B1, new Identifier("A17"), 0, 2)), work(60));
    }

    @Test @DisplayName("A booking version change on a closed space is a new pair: emitted again")
    void bookingVersionChangeReemits() {
        assertEquals(1, work(0).size());
        manager.proposeReassignment("B12", "B1");
        manager.approve("PROP_1");
        assertEquals(ExecutionOutcome.COMMITTED, manager.execute("PROP_1"));   // B1 -> B12, version 1
        assertEquals(List.of(), work(60), "B12 is open: B1 no longer affected");
        registry.closeSpace(new Identifier("B12"));
        assertEquals(List.of(new WorkItem(B1, new Identifier("B12"), 1, 1)), work(120));
    }

    @Test @DisplayName("Bookings on open spaces are never emitted")
    void openSpaceNotAffected() {
        manager.proposeReassignment("C03", "B1");
        manager.approve("PROP_1");
        manager.execute("PROP_1");
        assertEquals(List.of(), work(0));
    }

    // ---- application wiring -------------------------------------------------------------------------------------

    @Test @DisplayName("StudentApplication.tick detects only; the investigation is a separate explicit run")
    void applicationDetectionThenSeparateRun() {
        StudentApplication app = new StudentApplication();
        TickResult r = app.tick(0);
        assertEquals(1, r.workItems().size());
        assertEquals(0, app.bookingSnapshot().get("version"));
        assertEquals(java.util.Optional.empty(), app.proposalStatus("PROP_1"), "tick created no proposal");
        RunResult run = app.runAgent(Fixture.replay(), 6);
        assertEquals(RunStatus.AWAITING_APPROVAL, run.status());
        assertEquals(List.of(), app.tick(60).workItems(), "active proposal now covers the pair");
    }

    @Test @DisplayName("The periodic trace shows the boundary ticks and the backwards rejection")
    void periodicTrace() {
        String trace = new StudentApplication().periodicTrace();
        assertTrue(trace.contains("tick(  0) -> SCANNED        work: B1 on closed A17"), trace);
        assertTrue(trace.contains("tick( 59) -> NOT_DUE"), trace);
        assertTrue(trace.contains("tick( 60) -> SCANNED        no new work"), trace);
        assertTrue(trace.contains("tick( 30) -> CLOCK_REJECTED"), trace);
    }
}
