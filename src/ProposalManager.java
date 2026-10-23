import java.util.Optional;

/**
 * Application service for the D1 proposal path. Check order (the precedence list):
 * target malformed, booking malformed, booking unknown, target unknown, then the policy.
 * It never changes a booking or a space; the only state it writes is a PENDING proposal.
 */
public final class ProposalManager {
    private final ParkingRegistry registry;
    private final ProposalStore store;
    private final ReassignmentPolicy policy;
    private final IdGenerator idGenerator;

    public ProposalManager(ParkingRegistry registry, ProposalStore store, ReassignmentPolicy policy, IdGenerator idGenerator) {
        if (registry == null || store == null || policy == null || idGenerator == null)
            throw new IllegalArgumentException("collaborators required");
        this.registry = registry; this.store = store; this.policy = policy; this.idGenerator = idGenerator;
    }

    public Result proposeReassignment(String targetStr, String bookingStr) {
        if (!Identifier.isValid(targetStr))
            return new Refused(RefusalReason.TARGET_MALFORMED, "Target ID is malformed.");
        if (!Identifier.isValid(bookingStr))
            return new Refused(RefusalReason.BOOKING_MALFORMED, "Booking ID is malformed.");
        Identifier targetId = new Identifier(targetStr);
        Identifier bookingId = new Identifier(bookingStr);

        Optional<Booking> booking = registry.findBooking(bookingId);
        if (booking.isEmpty())
            return new Refused(RefusalReason.BOOKING_UNKNOWN, "Booking " + bookingId + " does not exist in registry.");
        Optional<Space> target = registry.findSpace(targetId);
        if (target.isEmpty())
            return new Refused(RefusalReason.TARGET_UNKNOWN, "Space " + targetId + " does not exist in registry.");

        PolicyVerdict verdict = policy.evaluate(booking.get(), target.get());
        return switch (verdict) {
            case Ineligible i -> new Refused(i.getReason(), "Target space " + targetId + " refused: " + i.getReason() + ".");
            case Eligible e -> {
                Proposal proposal = new Proposal(idGenerator.generate(), bookingId, targetId,
                        ProposalStatus.PENDING, booking.get().getVersion(), policy.getVersion());
                store.save(proposal);
                yield new Created(proposal);
            }
        };
    }
}
