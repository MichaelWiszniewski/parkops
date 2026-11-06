import java.util.Optional;

/**
 * Application service that owns the proposal lifecycle and its transition table.
 * propose: check order target malformed, booking malformed, booking unknown, target unknown, then policy;
 *          writes only a PENDING proposal and never changes a booking or a space.
 * approve/reject: legal only from PENDING; any other state is refused and left unchanged.
 * execute: UNKNOWN_PROPOSAL, DUPLICATE, NOT_APPROVED, STALE, UNAVAILABLE_OR_INELIGIBLE, then COMMITTED.
 *          Validation and all four writes happen in one call with no model or external call in between.
 *          (Single-process and sequential only; this is not a concurrency guarantee.)
 */
public final class ProposalManager {
    private final ParkingRegistry registry;
    private final ProposalStore store;
    private final ReassignmentPolicy policy;
    private final IdGenerator idGenerator;
    private final CommitLog commits;
    private final NotificationLog notifications;

    /** D1 constructor, kept for the D1 tests: uses fresh, private commit and notification logs. */
    public ProposalManager(ParkingRegistry registry, ProposalStore store, ReassignmentPolicy policy, IdGenerator idGenerator) {
        this(registry, store, policy, idGenerator, new CommitLog(), new NotificationLog());
    }

    public ProposalManager(ParkingRegistry registry, ProposalStore store, ReassignmentPolicy policy, IdGenerator idGenerator,
                           CommitLog commits, NotificationLog notifications) {
        if (registry == null || store == null || policy == null || idGenerator == null || commits == null || notifications == null)
            throw new IllegalArgumentException("collaborators required");
        this.registry = registry; this.store = store; this.policy = policy; this.idGenerator = idGenerator;
        this.commits = commits; this.notifications = notifications;
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

    // ---- operator decisions --------------------------------------------------------------------------------------

    public DecisionOutcome approve(String proposalId) { return decide(proposalId, ProposalStatus.APPROVED); }

    public DecisionOutcome reject(String proposalId) { return decide(proposalId, ProposalStatus.REJECTED); }

    private DecisionOutcome decide(String proposalId, ProposalStatus decision) {
        Optional<Proposal> found = find(proposalId);
        if (found.isEmpty()) return DecisionOutcome.UNKNOWN_PROPOSAL;
        Proposal p = found.get();
        if (p.getStatus() != ProposalStatus.PENDING) return DecisionOutcome.NOT_PENDING;   // state unchanged
        store.replace(p.withStatus(decision));
        return decision == ProposalStatus.APPROVED ? DecisionOutcome.APPROVED : DecisionOutcome.REJECTED;
    }

    // ---- execution -----------------------------------------------------------------------------------------------

    public ExecutionOutcome execute(String proposalId) {
        Optional<Proposal> found = find(proposalId);
        if (found.isEmpty()) return ExecutionOutcome.UNKNOWN_PROPOSAL;
        Proposal p = found.get();

        if (commits.contains(p.getId()) || p.getStatus() == ProposalStatus.EXECUTED) return ExecutionOutcome.DUPLICATE;
        if (p.getStatus() != ProposalStatus.APPROVED) return ExecutionOutcome.NOT_APPROVED;

        Booking booking = registry.findBooking(p.getBookingId())
                .orElseThrow(() -> new IllegalStateException("proposal refers to a missing booking"));
        if (booking.getVersion() != p.getBookingVersion() || policy.getVersion() != p.getPolicyVersion())
            return ExecutionOutcome.STALE;

        Optional<Space> target = registry.findSpace(p.getTargetId());
        if (target.isEmpty() || !(policy.evaluate(booking, target.get()) instanceof Eligible))
            return ExecutionOutcome.UNAVAILABLE_OR_INELIGIBLE;

        // Commit. Every check above used current state; nothing external runs between them and these writes.
        Identifier from = booking.getSpaceId();
        Booking moved = registry.moveBooking(booking.getId(), p.getTargetId(), p.getBookingVersion());
        store.replace(p.withStatus(ProposalStatus.EXECUTED));
        commits.add(new CommitRecord(p.getId(), moved.getId(), from, moved.getSpaceId(), moved.getVersion()));
        notifications.add(new Notification(p.getId(), moved.getId(), "Booking " + moved.getId().getValue()
                + " moved from " + from.getValue() + " to " + moved.getSpaceId().getValue()
                + " (proposal " + p.getId().getValue() + ")."));
        return ExecutionOutcome.COMMITTED;
    }

    /** Read-only snapshot for the operator and tests; never the store's own objects to mutate. */
    public Optional<Proposal> findProposal(String proposalId) { return find(proposalId); }

    private Optional<Proposal> find(String proposalId) {
        if (!Identifier.isValid(proposalId)) return Optional.empty();
        return store.get(new Identifier(proposalId));
    }
}
