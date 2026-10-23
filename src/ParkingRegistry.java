import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Owns every space and booking. Contents are private; callers only receive immutable snapshots. D1 has no mutator. */
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
}
