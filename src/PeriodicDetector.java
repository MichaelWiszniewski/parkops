import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Periodic detection of bookings on closed spaces. Time is supplied by the caller (seconds on a monotonic
 * clock), so tests and demos never wait. Detection only: it emits work items and never calls the model,
 * creates a proposal or changes a booking. Starting an investigation is a separate, explicit step.
 *
 * Schedule: the first tick scans immediately and fixes boundaries at first + k*PERIOD. A tick before the
 * next boundary is NOT_DUE; a tick at or after it scans. A tick earlier than the previous tick is rejected
 * and changes nothing.
 *
 * Conservative retry policy: each booking-version/policy-version pair is handled at most once. A pair is
 * handled when a work item is emitted for it, or when it is first seen already covered by a PENDING or
 * APPROVED proposal. Failed or rejected attempts are therefore not retried automatically; new work for the
 * same booking needs a version change (a new pair) or an explicit operator retry(bookingId).
 */
public final class PeriodicDetector {
    public static final long PERIOD_SECONDS = 60;

    private record Pair(Identifier bookingId, int bookingVersion, int policyVersion) { }

    private final ParkingRegistry registry;
    private final ProposalManager manager;
    private final ReassignmentPolicy policy;
    private final Set<Pair> handled = new HashSet<>();

    private Long firstTick;   // null until the first tick
    private long lastTick;
    private long nextDue;

    public PeriodicDetector(ParkingRegistry registry, ProposalManager manager, ReassignmentPolicy policy) {
        if (registry == null || manager == null || policy == null) throw new IllegalArgumentException("collaborators required");
        this.registry = registry; this.manager = manager; this.policy = policy;
    }

    public TickResult tick(long time) {
        if (firstTick != null && time < lastTick)
            return new TickResult(TickResult.Status.CLOCK_REJECTED, time, List.of());   // schedule unchanged
        if (firstTick != null && time < nextDue) {
            lastTick = time;
            return new TickResult(TickResult.Status.NOT_DUE, time, List.of());
        }
        if (firstTick == null) firstTick = time;
        lastTick = time;
        nextDue = firstTick + ((time - firstTick) / PERIOD_SECONDS + 1) * PERIOD_SECONDS;
        return new TickResult(TickResult.Status.SCANNED, time, scan());
    }

    /** Explicit operator retry: the booking's current pair may be emitted again at the next due scan. */
    public boolean retry(Identifier bookingId) {
        Booking b = registry.findBooking(bookingId).orElse(null);
        if (b == null) return false;
        return handled.remove(new Pair(bookingId, b.getVersion(), policy.getVersion()));
    }

    /** Next boundary at which a tick will scan, or empty before the first tick. */
    public java.util.OptionalLong nextDue() {
        return firstTick == null ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(nextDue);
    }

    private List<WorkItem> scan() {
        List<WorkItem> out = new ArrayList<>();
        for (Booking b : registry.allBookings()) {
            Space space = registry.findSpace(b.getSpaceId()).orElse(null);
            if (space == null || space.isOpen()) continue;   // only bookings on closed spaces are affected
            Pair pair = new Pair(b.getId(), b.getVersion(), policy.getVersion());
            if (handled.contains(pair)) continue;
            handled.add(pair);
            if (manager.hasActiveProposal(b.getId(), pair.bookingVersion(), pair.policyVersion())) continue;
            out.add(new WorkItem(b.getId(), space.getId(), pair.bookingVersion(), pair.policyVersion()));
        }
        return out;
    }
}
