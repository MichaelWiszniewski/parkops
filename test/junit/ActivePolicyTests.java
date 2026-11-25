import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D3 step 1: the shared, switchable policy. PolicyV2 does not exist yet, so a stub stands in for "a higher version". */
class ActivePolicyTests {
    private static final Identifier B1 = new Identifier("B1");
    private final ParkingRegistry registry = StudentApplication.fixtureRegistry();

    /** Test double: a policy of the given version that accepts every target. Not the assessed version-2 rule. */
    private static ReassignmentPolicy acceptAll(int version) {
        return new ReassignmentPolicy() {
            @Override public PolicyVerdict evaluate(Booking booking, Space target) { return new Eligible("stub"); }
            @Override public int getVersion() { return version; }
        };
    }

    private Booking b1() { return registry.findBooking(B1).orElseThrow(); }
    private Space space(String id) { return registry.findSpace(new Identifier(id)).orElseThrow(); }

    @Test @DisplayName("starts as the wrapped policy: version 1 and v1 verdicts")
    void delegatesToInitial() {
        ActivePolicy active = new ActivePolicy(new PolicyV1());
        assertEquals(1, active.getVersion());
        assertInstanceOf(Eligible.class, active.evaluate(b1(), space("B12")));
        Ineligible occupied = assertInstanceOf(Ineligible.class, active.evaluate(b1(), space("D09")));
        assertEquals(RefusalReason.OCCUPIED, occupied.getReason());
    }

    @Test @DisplayName("activate with a higher version switches both version and verdicts")
    void activateHigherVersion() {
        ActivePolicy active = new ActivePolicy(new PolicyV1());
        active.activate(acceptAll(2));
        assertEquals(2, active.getVersion());
        assertInstanceOf(Eligible.class, active.evaluate(b1(), space("D09")));   // v1 would say OCCUPIED
    }

    @Test @DisplayName("same or lower version, null and nested policies are refused; active policy unchanged")
    void refusedActivationsChangeNothing() {
        ActivePolicy active = new ActivePolicy(new PolicyV1());
        active.activate(acceptAll(2));
        assertThrows(IllegalArgumentException.class, () -> active.activate(acceptAll(2)));
        assertThrows(IllegalArgumentException.class, () -> active.activate(new PolicyV1()));
        assertThrows(IllegalArgumentException.class, () -> active.activate(null));
        assertThrows(IllegalArgumentException.class, () -> active.activate(new ActivePolicy(acceptAll(3))));
        assertEquals(2, active.getVersion());
        assertInstanceOf(Eligible.class, active.evaluate(b1(), space("D09")));
    }

    @Test @DisplayName("constructor refuses null and nested policies")
    void constructorGuards() {
        assertThrows(IllegalArgumentException.class, () -> new ActivePolicy(null));
        assertThrows(IllegalArgumentException.class, () -> new ActivePolicy(new ActivePolicy(new PolicyV1())));
    }

    @Test @DisplayName("shared instance: a manager built before the switch sees the new version at execute time")
    void managerSeesSwitch() {
        ActivePolicy active = new ActivePolicy(new PolicyV1());
        ProposalManager manager = new ProposalManager(registry, new ProposalStore(), active, new SequentialIdGenerator());
        Proposal p = assertInstanceOf(Created.class, manager.proposeReassignment("B12", "B1")).getProposal();
        assertEquals(1, p.getPolicyVersion());
        assertEquals(DecisionOutcome.APPROVED, manager.approve(p.getId().getValue()));

        active.activate(acceptAll(2));   // the stub would still accept B12, so only the version check can stop it

        assertEquals(ExecutionOutcome.STALE, manager.execute(p.getId().getValue()));
        assertEquals("A17", b1().getSpaceId().getValue());
        assertEquals(0, b1().getVersion());
        assertEquals(ProposalStatus.APPROVED, manager.findProposal(p.getId().getValue()).orElseThrow().getStatus());
    }
}
