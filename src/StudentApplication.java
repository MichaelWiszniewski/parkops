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
    private final ReassignmentPolicy policy = new PolicyV1();
    private final ProposalManager manager;

    public StudentApplication() {
        this.registry = fixtureRegistry();
        this.manager = new ProposalManager(registry, new ProposalStore(), policy, new SequentialIdGenerator());
    }

    /** Builds the registry from the supplied raw fixture without altering it. */
    static ParkingRegistry fixtureRegistry() {
        List<Space> spaces = new ArrayList<>();
        for (Map<String, Object> m : Fixture.spaces())
            spaces.add(new Space(new Identifier((String) m.get("id")),
                    (Boolean) m.get("open"), (Boolean) m.get("occupied"), (Boolean) m.get("accessible")));
        Map<String, Object> b = Fixture.booking();
        Booking booking = new Booking(new Identifier((String) b.get("id")), new Identifier((String) b.get("spaceId")),
                new Duration(((Number) b.get("durationHours")).intValue()),
                ((Number) b.get("version")).intValue(), (Boolean) b.get("accessibilityRequired"));
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
    // TODO D3: evolve policy, preserve regressions, refactor and specify contracts.
}
