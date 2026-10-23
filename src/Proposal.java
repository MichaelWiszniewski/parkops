/** Immutable record of a suggested move. It stores identifiers and the versions observed; it is not a completed change. */
public final class Proposal {
    private final Identifier id;
    private final Identifier bookingId;
    private final Identifier targetId;
    private final ProposalStatus status;
    private final int bookingVersion;
    private final int policyVersion;

    public Proposal(Identifier id, Identifier bookingId, Identifier targetId,
                    ProposalStatus status, int bookingVersion, int policyVersion) {
        if (id == null || bookingId == null || targetId == null || status == null)
            throw new IllegalArgumentException("proposal fields required");
        if (bookingVersion < 0 || policyVersion < 1) throw new IllegalArgumentException("invalid captured version");
        this.id = id; this.bookingId = bookingId; this.targetId = targetId;
        this.status = status; this.bookingVersion = bookingVersion; this.policyVersion = policyVersion;
    }

    public Identifier getId() { return id; }
    public Identifier getBookingId() { return bookingId; }
    public Identifier getTargetId() { return targetId; }
    public ProposalStatus getStatus() { return status; }
    public int getBookingVersion() { return bookingVersion; }
    public int getPolicyVersion() { return policyVersion; }
}
