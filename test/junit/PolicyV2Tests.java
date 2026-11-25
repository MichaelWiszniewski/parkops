import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D3 step 2: PolicyV2 on its own (policy level only). The application-level change cases D3-A..D3-I,
 * with activation, stale proposals and the periodic checker, are tested separately.
 */
class PolicyV2Tests {
    private static final String[] FIXTURE_SPACES = {"A17", "B12", "C03", "D09"};
    private final ParkingRegistry registry = StudentApplication.fixtureRegistry();
    private final PolicyV1 v1 = new PolicyV1();
    private final PolicyV2 v2 = new PolicyV2();

    private Booking b1() { return registry.findBooking(new Identifier("B1")).orElseThrow(); }
    private Space space(String id) { return registry.findSpace(new Identifier(id)).orElseThrow(); }
    private static RefusalReason reason(PolicyVerdict v) { return assertInstanceOf(Ineligible.class, v).getReason(); }

    /** Same booking fields as B1 except the accessibility flag. */
    private Booking b1WithoutRequirement() {
        Booking b = b1();
        return new Booking(b.getId(), b.getSpaceId(), b.getDuration(), b.getVersion(), false);
    }

    private List<String> eligible(ReassignmentPolicy policy, Booking booking) {
        List<String> out = new ArrayList<>();
        for (String id : FIXTURE_SPACES)
            if (policy.evaluate(booking, space(id)) instanceof Eligible) out.add(id);
        return out;
    }

    @Test @DisplayName("PolicyV2 reports version 2; PolicyV1 still reports 1")
    void versions() {
        assertEquals(2, v2.getVersion());
        assertEquals(1, v1.getVersion());
    }

    @Test @DisplayName("B1 fixture: v1 allows B12 and C03, v2 allows exactly C03 and refuses B12 as NOT_ACCESSIBLE")
    void fixtureCandidates() {
        assertEquals(List.of("B12", "C03"), eligible(v1, b1()));
        assertEquals(List.of("C03"), eligible(v2, b1()));
        assertEquals(RefusalReason.NOT_ACCESSIBLE, reason(v2.evaluate(b1(), space("B12"))));
    }

    @Test @DisplayName("booking without the requirement: v2 gives the same verdict as v1 for every fixture space")
    void nonRequiringBookingUnchanged() {
        Booking b = b1WithoutRequirement();
        assertEquals(List.of("B12", "C03"), eligible(v2, b));
        for (String id : FIXTURE_SPACES) {
            PolicyVerdict a = v1.evaluate(b, space(id)), c = v2.evaluate(b, space(id));
            assertEquals(a.getClass(), c.getClass(), id);
            if (a instanceof Ineligible i) assertEquals(i.getReason(), reason(c), id);
        }
    }

    @Test @DisplayName("v1 availability refusals win over NOT_ACCESSIBLE (purpose-built inaccessible spaces)")
    void availabilityChecksKeptAndFirst() {
        Booking booking = new Booking(new Identifier("T1"), new Identifier("S01"), new Duration(1), 0, true);
        // id, open, occupied, accessible -- every target here is also inaccessible
        assertEquals(RefusalReason.CURRENT_SPACE, reason(v2.evaluate(booking, new Space(new Identifier("S01"), true, false, false))));
        assertEquals(RefusalReason.OCCUPIED, reason(v2.evaluate(booking, new Space(new Identifier("X01"), false, true, false))));
        assertEquals(RefusalReason.NOT_OPEN, reason(v2.evaluate(booking, new Space(new Identifier("X02"), false, false, false))));
        assertEquals(RefusalReason.NOT_ACCESSIBLE, reason(v2.evaluate(booking, new Space(new Identifier("X03"), true, false, false))));
    }

    @Test @DisplayName("accessibility alone is not enough: accessible targets still need to be open, free and different")
    void accessibleButUnavailable() {
        Booking booking = new Booking(new Identifier("T1"), new Identifier("S01"), new Duration(1), 0, true);
        assertEquals(RefusalReason.CURRENT_SPACE, reason(v2.evaluate(booking, new Space(new Identifier("S01"), true, false, true))));
        assertEquals(RefusalReason.OCCUPIED, reason(v2.evaluate(booking, new Space(new Identifier("X04"), true, true, true))));
        assertEquals(RefusalReason.NOT_OPEN, reason(v2.evaluate(booking, new Space(new Identifier("X05"), false, false, true))));
        assertInstanceOf(Eligible.class, v2.evaluate(booking, new Space(new Identifier("X06"), true, false, true)));
    }

    @Test @DisplayName("v2 only returns the three v1 reasons or NOT_ACCESSIBLE; v1 never returns NOT_ACCESSIBLE")
    void reasonSets() {
        Set<RefusalReason> v2Allowed = EnumSet.of(RefusalReason.CURRENT_SPACE, RefusalReason.OCCUPIED,
                RefusalReason.NOT_OPEN, RefusalReason.NOT_ACCESSIBLE);
        for (Booking b : List.of(b1(), b1WithoutRequirement()))
            for (String id : FIXTURE_SPACES) {
                if (v2.evaluate(b, space(id)) instanceof Ineligible i) assertTrue(v2Allowed.contains(i.getReason()), id);
                if (v1.evaluate(b, space(id)) instanceof Ineligible i) assertNotEquals(RefusalReason.NOT_ACCESSIBLE, i.getReason(), id);
            }
    }

    @Test @DisplayName("null booking or target is rejected, as in v1")
    void nullArguments() {
        assertThrows(IllegalArgumentException.class, () -> v2.evaluate(null, space("C03")));
        assertThrows(IllegalArgumentException.class, () -> v2.evaluate(b1(), null));
    }

    @Test @DisplayName("ActivePolicy can move from PolicyV1 to PolicyV2 but not back")
    void activeV1ToV2() {
        ActivePolicy active = new ActivePolicy(v1);
        assertEquals(List.of("B12", "C03"), eligible(active, b1()));
        active.activate(new PolicyV2());
        assertEquals(2, active.getVersion());
        assertEquals(List.of("C03"), eligible(active, b1()));
        assertThrows(IllegalArgumentException.class, () -> active.activate(new PolicyV1()));
        assertEquals(2, active.getVersion());
    }
}
