import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Starter boundary kept for the public checks, now a thin adapter over the domain design.
 * It builds the registry from the supplied fixture, delegates to ProposalManager, and translates:
 * Created -> ProposalView, Refused -> IllegalArgumentException (the starter API's rejection convention).
 */
public final class StudentApplication {
    private static final String BOOKING_ID = "B1";
    private final ParkingRegistry registry;
    /** One shared policy object: every collaborator below sees a policy change at the same moment. */
    private final ActivePolicy policy = new ActivePolicy(new PolicyV1());
    private final CommitLog commits = new CommitLog();
    private final NotificationLog notifications = new NotificationLog();
    private final ProposalManager manager;   // the only holder of the ProposalStore
    private final PeriodicDetector detector;

    /** Supplied B1 fixture, exactly as shipped (B1 requires an accessible space). Starts under policy version 1. */
    public StudentApplication() { this(fixtureRegistry()); }

    /**
     * D3-G variant: the same fixture except that B1 does not require an accessible space. Only this
     * application's copy differs; the raw Fixture data is never altered. Starts under policy version 1.
     */
    public static StudentApplication withoutAccessibilityRequirement() {
        return new StudentApplication(fixtureRegistry(false));
    }

    private StudentApplication(ParkingRegistry registry) {
        this.registry = registry;
        this.manager = new ProposalManager(registry, new ProposalStore(), policy, new SequentialIdGenerator(),
                commits, notifications);
        this.detector = new PeriodicDetector(registry, manager, policy);
    }

    // ---- policy evolution (D3) -------------------------------------------------------------------------------

    /** Version of the policy currently enforced by proposal, execution, investigation and detection. */
    public int policyVersion() { return policy.getVersion(); }

    /**
     * Operator action: switch every collaborator to policy version 2 at once. Returns true if the version
     * changed, false if version 2 (or later) is already active, in which case nothing changes. Existing
     * proposals keep their captured version 1, so they go STALE at execution; bookings and spaces are untouched.
     */
    public boolean activatePolicyV2() {
        if (policy.getVersion() >= 2) return false;
        policy.activate(new PolicyV2());
        return true;
    }

    // ---- periodic detection (detection only; investigations are started separately) ---------------------------

    public TickResult tick(long timeSeconds) { return detector.tick(timeSeconds); }
    public boolean retryDetection(String bookingId) {
        return Identifier.isValid(bookingId) && detector.retry(new Identifier(bookingId));
    }

    /** Demonstration trace for results/: boundary ticks 0, 59, 60, 120 and a backwards tick. */
    public String periodicTrace() {
        StringBuilder out = new StringBuilder();
        for (long t : new long[]{0, 59, 60, 30, 119, 120}) {
            TickResult r = tick(t);
            out.append(String.format("tick(%3d) -> %-14s", t, r.status()));
            for (WorkItem w : r.workItems())
                out.append(" work: ").append(w.bookingId().getValue()).append(" on closed ")
                   .append(w.closedSpaceId().getValue()).append(" (booking v").append(w.bookingVersion())
                   .append(", policy v").append(w.policyVersion()).append(')');
            if (r.status() == TickResult.Status.SCANNED && r.workItems().isEmpty()) out.append(" no new work");
            if (r.status() == TickResult.Status.NOT_DUE) out.append(" before the next 60 s boundary");
            if (r.status() == TickResult.Status.CLOCK_REJECTED) out.append(" time went backwards; schedule unchanged");
            out.append('\n');
        }
        return out.toString().stripTrailing();
    }

    // ---- separate operator actions (never called by the agent loop) -----------------------------------------------

    public DecisionOutcome approve(String proposalId) { return manager.approve(proposalId); }
    public DecisionOutcome reject(String proposalId) { return manager.reject(proposalId); }
    public ExecutionOutcome execute(String proposalId) { return manager.execute(proposalId); }

    /** Immutable snapshot of a proposal for the operator to review. */
    public Optional<Proposal> proposal(String proposalId) { return manager.findProposal(proposalId); }

    public Optional<ProposalStatus> proposalStatus(String proposalId) {
        return manager.findProposal(proposalId).map(Proposal::getStatus);
    }
    public int commitCount() { return commits.count(); }
    public int notificationCount() { return notifications.count(); }
    public List<Notification> notifications() { return notifications.all(); }

    /** Builds the registry from the supplied raw fixture without altering it. */
    static ParkingRegistry fixtureRegistry() {
        return fixtureRegistry((Boolean) Fixture.booking().get("accessibilityRequired"));
    }

    /** Same as fixtureRegistry(), but B1's accessibility requirement is set by the caller. */
    static ParkingRegistry fixtureRegistry(boolean accessibilityRequired) {
        List<Space> spaces = new ArrayList<>();
        for (Map<String, Object> m : Fixture.spaces())
            spaces.add(new Space(new Identifier((String) m.get("id")),
                    (Boolean) m.get("open"), (Boolean) m.get("occupied"), (Boolean) m.get("accessible")));
        Map<String, Object> b = Fixture.booking();
        Booking booking = new Booking(new Identifier((String) b.get("id")), new Identifier((String) b.get("spaceId")),
                new Duration(((Number) b.get("durationHours")).intValue()),
                ((Number) b.get("version")).intValue(), accessibilityRequired);
        return new ParkingRegistry(spaces, List.of(booking));
    }

    /** Immutable snapshot with the same keys as the raw fixture; callers cannot reach protected state through it. */
    public Map<String, Object> bookingSnapshot() {
        Booking b = registry.findBooking(new Identifier(BOOKING_ID)).orElseThrow();
        return Map.<String, Object>of("id", b.getId().getValue(), "spaceId", b.getSpaceId().getValue(),
                "durationHours", b.getDuration().getHours(), "version", b.getVersion(),
                "accessibilityRequired", b.isAccessibilityRequired());
    }

    public ProposalView propose(String targetId) {
        return switch (manager.proposeReassignment(targetId, BOOKING_ID)) {
            case Created c -> {
                Proposal p = c.getProposal();
                yield new ProposalView(p.getId().getValue(), p.getBookingId().getValue(), p.getTargetId().getValue(),
                        p.getBookingVersion(), p.getPolicyVersion(), p.getStatus().name());
            }
            case Refused r -> throw new IllegalArgumentException(r.getMessage());
        };
    }

    /** One bounded investigation of B1. Stops at a pending proposal at most; never approves or executes. */
    public RunResult runAgent(ModelProvider provider, int limit) {
        Identifier goal = new Identifier(BOOKING_ID);
        InvestigationSession session = new InvestigationSession(goal, registry, manager, policy);
        return new AgentRunner(provider, new ToolRequestParser(goal), session, limit).run();
    }

    /** Starter entry point used by Main: runs the agent and returns a printable transcript and outcome. */
    public String run(ModelProvider provider, int limit) {
        return format(runAgent(provider, limit));
    }

    String format(RunResult r) {
        StringBuilder out = new StringBuilder();
        int call = 0;
        for (ChatMessage m : r.transcript()) {
            switch (m.role()) {
                case "system" -> out.append("SYSTEM PROMPT (").append(m.content().length()).append(" chars)\n");
                case "assistant" -> out.append("CALL ").append(++call).append(" model:       ").append(m.content()).append('\n');
                default -> out.append(call == 0 ? "GOAL:                " : "       observation: ").append(m.content()).append('\n');
            }
        }
        out.append("OUTCOME ").append(r.status())
           .append(r.proposalId().map(id -> " proposal=" + id).orElse(""))
           .append(" calls=").append(r.calls())
           .append(" invalid=").append(r.invalidResponses())
           .append(" toolRejections=").append(r.toolRejections()).append('\n');
        Booking b = registry.findBooking(new Identifier(BOOKING_ID)).orElseThrow();
        out.append("Booking: B1 at ").append(b.getSpaceId().getValue()).append(" version ").append(b.getVersion());
        return out.toString();
    }
    // TODO D3: refactor and specify contracts.
}
