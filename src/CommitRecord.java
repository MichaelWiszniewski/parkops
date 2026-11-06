/** One committed reassignment. Immutable. */
public record CommitRecord(Identifier proposalId, Identifier bookingId, Identifier fromSpace, Identifier toSpace, int newVersion) {
    public CommitRecord {
        if (proposalId == null || bookingId == null || fromSpace == null || toSpace == null || newVersion < 1)
            throw new IllegalArgumentException("commit fields required");
    }
}
