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
    private final ProposalManager manager;

    public StudentApplication() {
        this.registry = fixtureRegistry();
        this.manager = new ProposalManager(registry, new ProposalStore(), new PolicyV1(), new SequentialIdGenerator());
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

    public String run(ModelProvider provider, int limit) {
        // Will be done in D2: validate protocol, dispatch tools, retain observation history,
        // bound invocations and stop at pending approval. No automatic commit.
        // Add separate operator approve/reject/execute operations and tick(time).
        throw new UnsupportedOperationException("D2 model/tool coordination");
    }
    // TODO D3: evolve policy, preserve regressions, refactor and specify contracts.
}
