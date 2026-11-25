/**
 * The pair of versions a decision was based on: the booking's version and the policy's version.
 * D3.2 refactoring: this pair used to travel as two loose ints, and the "is it still fresh?" comparison was
 * written out separately in ProposalManager, InvestigationSession, PeriodicDetector and ProposalStore.
 * Now the validity rule lives here once, and freshness is captured.equals(VersionStamp.current(...)).
 */
public record VersionStamp(int bookingVersion, int policyVersion) {
    public VersionStamp {
        if (bookingVersion < 0 || policyVersion < 1) throw new IllegalArgumentException("invalid captured version");
    }

    /** The versions in force right now for this booking under this policy. */
    public static VersionStamp current(Booking booking, ReassignmentPolicy policy) {
        if (booking == null || policy == null) throw new IllegalArgumentException("booking and policy required");
        return new VersionStamp(booking.getVersion(), policy.getVersion());
    }
}
