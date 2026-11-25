/** One automatically detected piece of work: a booking assigned to a closed space, at specific versions. */
public record WorkItem(Identifier bookingId, Identifier closedSpaceId, int bookingVersion, int policyVersion) {
    public WorkItem {
        if (bookingId == null || closedSpaceId == null) throw new IllegalArgumentException("work item fields required");
        new VersionStamp(bookingVersion, policyVersion);   // single validity rule for a captured version pair
    }

    public WorkItem(Identifier bookingId, Identifier closedSpaceId, VersionStamp versions) {
        this(bookingId, closedSpaceId, required(versions).bookingVersion(), versions.policyVersion());
    }

    private static VersionStamp required(VersionStamp v) {
        if (v == null) throw new IllegalArgumentException("work item fields required");
        return v;
    }

    /** The booking and policy versions this work item was detected at. */
    public VersionStamp versions() { return new VersionStamp(bookingVersion, policyVersion); }
}
