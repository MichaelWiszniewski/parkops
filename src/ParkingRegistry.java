import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Owns every space and booking. Contents are private; callers only receive immutable snapshots.
 * Mutators: moveBooking (the commit step, called only by ProposalManager.execute after validation) and two
 * simulated external events, closeSpace and occupySpace. Each mutator checks every precondition before
 * writing anything, so a refused call leaves the registry unchanged.
 */
public final class ParkingRegistry {
    private final Map<Identifier, Space> spaces = new LinkedHashMap<>();
    private final Map<Identifier, Booking> bookings = new LinkedHashMap<>();

    public ParkingRegistry(Collection<Space> spaces, Collection<Booking> bookings) {
        for (Space s : List.copyOf(spaces))
            if (this.spaces.putIfAbsent(s.getId(), s) != null) throw new IllegalArgumentException("duplicate space " + s.getId());
        for (Booking b : List.copyOf(bookings))
            if (this.bookings.putIfAbsent(b.getId(), b) != null) throw new IllegalArgumentException("duplicate booking " + b.getId());
    }

    public Optional<Space> findSpace(Identifier id) { return Optional.ofNullable(spaces.get(id)); }
    public Optional<Booking> findBooking(Identifier id) { return Optional.ofNullable(bookings.get(id)); }

    /** Every space in insertion order, as an unmodifiable copy. Space objects are immutable. */
    public List<Space> allSpaces() { return List.copyOf(spaces.values()); }

    /**
     * Commit step. Releases the old space, occupies the target, moves the booking and increments its
     * version exactly once. Eligibility is the caller's decision; this only guards structural consistency.
     */
    public Booking moveBooking(Identifier bookingId, Identifier targetId, int expectedVersion) {
        Booking b = bookings.get(bookingId);
        if (b == null) throw new IllegalArgumentException("unknown booking " + bookingId);
        if (b.getVersion() != expectedVersion) throw new IllegalStateException("booking version changed");
        Space from = spaces.get(b.getSpaceId());
        Space to = spaces.get(targetId);
        if (from == null || to == null) throw new IllegalArgumentException("unknown space");
        if (from.getId().equals(to.getId())) throw new IllegalStateException("target is the current space");
        if (!to.isOpen() || to.isOccupied()) throw new IllegalStateException("target not available");
        // All checks passed; the four writes below cannot fail.
        Booking moved = new Booking(b.getId(), to.getId(), b.getDuration(), b.getVersion() + 1, b.isAccessibilityRequired());
        spaces.put(from.getId(), new Space(from.getId(), from.isOpen(), false, from.isAccessible()));
        spaces.put(to.getId(), new Space(to.getId(), to.isOpen(), true, to.isAccessible()));
        bookings.put(moved.getId(), moved);
        return moved;
    }

    /** Simulated external event: a space closes (e.g. maintenance). */
    public void closeSpace(Identifier id) {
        Space s = requireSpace(id);
        spaces.put(id, new Space(id, false, s.isOccupied(), s.isAccessible()));
    }

    /** Simulated external event: a space becomes occupied by a vehicle outside the booking system. */
    public void occupySpace(Identifier id) {
        Space s = requireSpace(id);
        spaces.put(id, new Space(id, s.isOpen(), true, s.isAccessible()));
    }

    private Space requireSpace(Identifier id) {
        Space s = spaces.get(id);
        if (s == null) throw new IllegalArgumentException("unknown space " + id);
        return s;
    }
}
