/** One automatically detected piece of work: a booking assigned to a closed space, at specific versions. */
public record WorkItem(Identifier bookingId, Identifier closedSpaceId, int bookingVersion, int policyVersion) {
    public WorkItem {
        if (bookingId == null || closedSpaceId == null || bookingVersion < 0 || policyVersion < 1)
            throw new IllegalArgumentException("work item fields required");
    }
}
