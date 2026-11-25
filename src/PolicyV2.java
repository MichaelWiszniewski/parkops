/**
 * Policy version 2: the D3 change request. Runs every PolicyV1 check first and returns a v1 refusal unchanged,
 * so the general availability rules (CURRENT_SPACE, OCCUPIED, NOT_OPEN) live only in PolicyV1. Only when v1
 * finds the target eligible does it add one rule: if the booking requires an accessible space and the target
 * is not accessible, the verdict is NOT_ACCESSIBLE. A booking without the requirement gets exactly v1's verdict.
 *
 * Synthetic baseline for the course exercise; not advice about real accessibility policy.
 */
public final class PolicyV2 implements ReassignmentPolicy {
    private final PolicyV1 base = new PolicyV1();

    @Override public PolicyVerdict evaluate(Booking booking, Space target) {
        PolicyVerdict v1 = base.evaluate(booking, target);   // also rejects null arguments
        if (v1 instanceof Ineligible) return v1;
        if (booking.isAccessibilityRequired() && !target.isAccessible())
            return new Ineligible(RefusalReason.NOT_ACCESSIBLE);
        return v1;
    }

    @Override public int getVersion() { return 2; }
}
