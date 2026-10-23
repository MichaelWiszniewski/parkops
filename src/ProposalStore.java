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

    public Optional<Proposal> get(Identifier proposalId) { return Optional.ofNullable(proposals.get(proposalId)); }

    /** Returns an unmodifiable copy, never the store's own collection. */
    public List<Proposal> findByBooking(Identifier bookingId) {
        List<Proposal> found = new ArrayList<>();
        for (Proposal p : proposals.values()) if (p.getBookingId().equals(bookingId)) found.add(p);
        return List.copyOf(found);
    }
}
