/**
 * Baseline policy, version 1. Contract: returns only CURRENT_SPACE, OCCUPIED or NOT_OPEN as refusal reasons,
 * checked in that order. The accessibility flag is deliberately unused here (controlled evolution exercise).
 */
public final class PolicyV1 implements ReassignmentPolicy {
    @Override public PolicyVerdict evaluate(Booking booking, Space target) {
        if (booking == null || target == null) throw new IllegalArgumentException("booking and target required");
        if (target.getId().equals(booking.getSpaceId())) return new Ineligible(RefusalReason.CURRENT_SPACE);
        if (target.isOccupied()) return new Ineligible(RefusalReason.OCCUPIED);
        if (!target.isOpen()) return new Ineligible(RefusalReason.NOT_OPEN);
        return new Eligible("successful request");
    }
    @Override public int getVersion() { return 1; }
}
