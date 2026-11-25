import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D3.2 refactoring: the single home of the captured-version rule. Behaviour elsewhere is covered by the unchanged D1/D2/D3 tests. */
class VersionStampTests {
    private static final Identifier B1 = new Identifier("B1"), P1 = new Identifier("PROP_1");
    private final ParkingRegistry registry = StudentApplication.fixtureRegistry();
    private Booking b1() { return registry.findBooking(B1).orElseThrow(); }

    @Test @DisplayName("boundaries: booking version 0 and policy version 1 are the smallest valid values")
    void boundaries() {
        VersionStamp min = new VersionStamp(0, 1);
        assertEquals(0, min.bookingVersion());
        assertEquals(1, min.policyVersion());
        assertThrows(IllegalArgumentException.class, () -> new VersionStamp(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new VersionStamp(0, 0));
    }

    @Test @DisplayName("value semantics: equal versions are equal, any difference is not")
    void valueEquality() {
        assertEquals(new VersionStamp(0, 1), new VersionStamp(0, 1));
        assertEquals(new VersionStamp(0, 1).hashCode(), new VersionStamp(0, 1).hashCode());
        assertNotEquals(new VersionStamp(0, 1), new VersionStamp(1, 1));
        assertNotEquals(new VersionStamp(0, 1), new VersionStamp(0, 2));
    }

    @Test @DisplayName("current() reads the booking's version and the policy's current version; a policy switch makes it unequal")
    void currentFollowsPolicy() {
        ActivePolicy policy = new ActivePolicy(new PolicyV1());
        VersionStamp before = VersionStamp.current(b1(), policy);
        assertEquals(new VersionStamp(0, 1), before);
        policy.activate(new PolicyV2());
        assertEquals(new VersionStamp(0, 2), VersionStamp.current(b1(), policy));
        assertNotEquals(before, VersionStamp.current(b1(), policy));
        assertThrows(IllegalArgumentException.class, () -> VersionStamp.current(null, policy));
        assertThrows(IllegalArgumentException.class, () -> VersionStamp.current(b1(), null));
    }

    @Test @DisplayName("Proposal: both constructors agree, invalid versions still rejected, withStatus keeps the stamp")
    void proposalUsesStamp() {
        Proposal a = new Proposal(P1, B1, new Identifier("C03"), ProposalStatus.PENDING, 0, 2);
        Proposal b = new Proposal(P1, B1, new Identifier("C03"), ProposalStatus.PENDING, new VersionStamp(0, 2));
        assertEquals(a.getVersions(), b.getVersions());
        assertEquals(0, a.getBookingVersion());
        assertEquals(2, a.getPolicyVersion());
        assertEquals(a.getVersions(), a.withStatus(ProposalStatus.APPROVED).getVersions());
        assertThrows(IllegalArgumentException.class, () -> new Proposal(P1, B1, B1, ProposalStatus.PENDING, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Proposal(P1, B1, B1, ProposalStatus.PENDING, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Proposal(P1, B1, B1, ProposalStatus.PENDING, null));
    }

    @Test @DisplayName("WorkItem: same validity rule, and versions() round-trips")
    void workItemUsesStamp() {
        Identifier a17 = new Identifier("A17");
        WorkItem w = new WorkItem(B1, a17, new VersionStamp(0, 2));
        assertEquals(new WorkItem(B1, a17, 0, 2), w);
        assertEquals(new VersionStamp(0, 2), w.versions());
        assertThrows(IllegalArgumentException.class, () -> new WorkItem(B1, a17, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new WorkItem(B1, a17, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WorkItem(B1, a17, null));
    }

    @Test @DisplayName("ProposalManager stamps a new proposal with the current versions")
    void managerStampsCurrent() {
        ActivePolicy policy = new ActivePolicy(new PolicyV1());
        policy.activate(new PolicyV2());
        ProposalManager manager = new ProposalManager(registry, new ProposalStore(), policy, new SequentialIdGenerator());
        Proposal p = assertInstanceOf(Created.class, manager.proposeReassignment("C03", "B1")).getProposal();
        assertEquals(VersionStamp.current(b1(), policy), p.getVersions());
        assertTrue(manager.hasActiveProposal(B1, new VersionStamp(0, 2)));
        assertFalse(manager.hasActiveProposal(B1, new VersionStamp(0, 1)));
    }
}
