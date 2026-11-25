/** Immutable record of a suggested move. It stores identifiers and the versions observed; it is not a completed change. */
public final class Proposal {
    private final Identifier id;
    private final Identifier bookingId;
    private final Identifier targetId;
    private final ProposalStatus status;
    private final VersionStamp versions;

    public Proposal(Identifier id, Identifier bookingId, Identifier targetId, ProposalStatus status, VersionStamp versions) {
        if (id == null || bookingId == null || targetId == null || status == null || versions == null)
            throw new IllegalArgumentException("proposal fields required");
        this.id = id; this.bookingId = bookingId; this.targetId = targetId;
        this.status = status; this.versions = versions;
    }

    /** Convenience form kept for existing callers; the versions are validated by VersionStamp. */
    public Proposal(Identifier id, Identifier bookingId, Identifier targetId,
                    ProposalStatus status, int bookingVersion, int policyVersion) {
        this(id, bookingId, targetId, status, new VersionStamp(bookingVersion, policyVersion));
    }

    /** A new snapshot identical except for its status. This object is unchanged. */
    public Proposal withStatus(ProposalStatus newStatus) {
        return new Proposal(id, bookingId, targetId, newStatus, versions);
    }

    public Identifier getId() { return id; }
    public Identifier getBookingId() { return bookingId; }
    public Identifier getTargetId() { return targetId; }
    public ProposalStatus getStatus() { return status; }
    /** The booking and policy versions this proposal was created under. */
    public VersionStamp getVersions() { return versions; }
    public int getBookingVersion() { return versions.bookingVersion(); }
    public int getPolicyVersion() { return versions.policyVersion(); }
}
