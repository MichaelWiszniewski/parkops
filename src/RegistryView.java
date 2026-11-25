import java.util.List;
import java.util.Optional;

/**
 * Read-only view of the parking registry (D3.3 dependency review). Collaborators that only need to look at
 * bookings and spaces -- InvestigationSession, which is driven by model output, and PeriodicDetector --
 * depend on this type, so the compiler stops them from calling ParkingRegistry's mutators (moveBooking,
 * closeSpace, occupySpace). ProposalManager keeps the full ParkingRegistry because committing a move is its job.
 * All returned objects are immutable snapshots or unmodifiable copies.
 */
public interface RegistryView {
    Optional<Space> findSpace(Identifier id);
    Optional<Booking> findBooking(Identifier id);
    List<Booking> allBookings();
    List<Space> allSpaces();
}
