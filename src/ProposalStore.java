import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Holds proposals in every status from the moment they are created. Callers only receive immutable snapshots. */
public final class ProposalStore {
    private final Map<Identifier, Proposal> proposals = new LinkedHashMap<>();

    public void save(Proposal p) {
        if (p == null) throw new IllegalArgumentException("proposal required");
        if (proposals.putIfAbsent(p.getId(), p) != null) throw new IllegalStateException("proposal id already stored");
    }

    /**
     * Swaps in a new snapshot of an EXISTING proposal. Only the status may differ: id, booking, target and
     * captured versions must match, so this cannot introduce a different proposal. Which status changes are
     * legal is decided by ProposalManager, the only holder of this store in the application.
     */
    public void replace(Proposal next) {
        if (next == null) throw new IllegalArgumentException("proposal required");
        Proposal current = proposals.get(next.getId());
        if (current == null) throw new IllegalStateException("no stored proposal " + next.getId());
        if (!current.getBookingId().equals(next.getBookingId()) || !current.getTargetId().equals(next.getTargetId())
                || current.getBookingVersion() != next.getBookingVersion()
                || current.getPolicyVersion() != next.getPolicyVersion())
            throw new IllegalStateException("replace may change only the status");
        proposals.put(next.getId(), next);
    }

    public Optional<Proposal> get(Identifier proposalId) { return Optional.ofNullable(proposals.get(proposalId)); }

    /** Returns an unmodifiable copy, never the store's own collection. */
    public List<Proposal> findByBooking(Identifier bookingId) {
        List<Proposal> found = new ArrayList<>();
        for (Proposal p : proposals.values()) if (p.getBookingId().equals(bookingId)) found.add(p);
        return List.copyOf(found);
    }
}
