import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D1 student tests. Default package, like the starter sources. Each test builds its own objects (JUnit creates a new instance per test). */
class StudentTests {
    private static final Identifier B1 = new Identifier("B1");
    private final ParkingRegistry registry = StudentApplication.fixtureRegistry();
    private final ProposalStore store = new ProposalStore();
    private final ProposalManager manager = new ProposalManager(registry, store, new PolicyV1(), new SequentialIdGenerator());

    // ---- helpers -------------------------------------------------------------------------------------------------

    /** Asserts the supplied fixture is exactly as shipped: booking at A17 version 0 and all four spaces' flags. */
    private void assertFixtureUnchanged() {
        Booking b = registry.findBooking(B1).orElseThrow();
        assertEquals("A17", b.getSpaceId().getValue());
        assertEquals(0, b.getVersion());
        assertEquals(3, b.getDuration().getHours());
        assertTrue(b.isAccessibilityRequired());
        assertSpace("A17", false, true, false);
        assertSpace("B12", true, false, false);
        assertSpace("C03", true, false, true);
        assertSpace("D09", true, true, true);
    }

    private void assertSpace(String id, boolean open, boolean occupied, boolean accessible) {
        Space s = registry.findSpace(new Identifier(id)).orElseThrow();
        assertEquals(open, s.isOpen(), id + " open");
        assertEquals(occupied, s.isOccupied(), id + " occupied");
        assertEquals(accessible, s.isAccessible(), id + " accessible");
    }

    private Refused refused(String target, String booking) {
        return assertInstanceOf(Refused.class, manager.proposeReassignment(target, booking));
    }

    // ---- D1-A / D1-B: duration -----------------------------------------------------------------------------------

    @Test @DisplayName("D1-A duration 1 and 24 are accepted and preserved")
    void durationBoundariesAccepted() {
        assertEquals(1, new Duration(1).getHours());
        assertEquals(24, new Duration(24).getHours());
        assertTrue(Duration.isValid(1));
        assertTrue(Duration.isValid(24));
    }

    @Test @DisplayName("D1-B duration 0, 25 and negative are rejected; no usable object")
    void durationOutOfRangeRejected() {
        for (int bad : new int[] {0, 25, -1, Integer.MAX_VALUE}) {
            assertFalse(Duration.isValid(bad));
            assertThrows(IllegalArgumentException.class, () -> new Duration(bad));
        }
    }

    @Test @DisplayName("D1-A a Booking built with 1 or 24 hours preserves that duration")
    void bookingPreservesBoundaryDurations() {
        for (int hours : new int[] {1, 24}) {
            Booking b = new Booking(new Identifier("T1"), new Identifier("S01"), new Duration(hours), 0, false);
            assertEquals(hours, b.getDuration().getHours());
        }
    }

    @Test @DisplayName("D1-B a Booking cannot be built with 0 or 25 hours: no usable invalid booking exists")
    void bookingRejectsInvalidDurations() {
        for (int hours : new int[] {0, 25}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new Booking(new Identifier("T1"), new Identifier("S01"), new Duration(hours), 0, false));
        }
    }

    // ---- identifiers ---------------------------------------------------------------------------------------------

    @Test @DisplayName("valid identifiers, including the 16-character boundary, are accepted")
    void validIdentifiersAccepted() {
        for (String ok : new String[] {"A", "B1", "A17", "PROP_1", "A-_9", "A234567890123456"}) {
            assertTrue(Identifier.isValid(ok), ok);
            assertEquals(ok, new Identifier(ok).getValue());
        }
    }

    @Test @DisplayName("null, empty, blank, lowercase, bad initial, spaces and 17+ characters are rejected")
    void invalidIdentifiersRejected() {
        String[] bad = {null, "", "   ", "b12", "1B", "_A", "-A", "A B", "B12\n", "A2345678901234567"};
        for (String s : bad) {
            assertFalse(Identifier.isValid(s), String.valueOf(s));
            assertThrows(IllegalArgumentException.class, () -> new Identifier(s));
        }
    }

    @Test @DisplayName("DomainRules (starter boundary) reports violations as IllegalArgumentException")
    void domainRulesBoundary() {
        DomainRules.requireDuration(1);
        DomainRules.requireDuration(24);
        assertThrows(IllegalArgumentException.class, () -> DomainRules.requireDuration(0));
        assertThrows(IllegalArgumentException.class, () -> DomainRules.requireDuration(25));
        DomainRules.requireIdentifier("B1");
        assertThrows(IllegalArgumentException.class, () -> DomainRules.requireIdentifier(null));
        assertThrows(IllegalArgumentException.class, () -> DomainRules.requireIdentifier("b1"));
    }

    // ---- D1-C: eligible proposal ---------------------------------------------------------------------------------

    @Test @DisplayName("D1-C B12 creates a PENDING proposal with captured versions 0/1 and an application id")
    void proposeB12CreatesPendingProposal() {
        Created created = assertInstanceOf(Created.class, manager.proposeReassignment("B12", "B1"));
        Proposal p = created.getProposal();
        assertEquals("PROP_1", p.getId().getValue());
        assertEquals("B1", p.getBookingId().getValue());
        assertEquals("B12", p.getTargetId().getValue());
        assertEquals(ProposalStatus.PENDING, p.getStatus());
        assertEquals(0, p.getBookingVersion());
        assertEquals(1, p.getPolicyVersion());
        assertEquals(List.of(p), store.findByBooking(B1));
        assertSame(p, store.get(p.getId()).orElseThrow());
    }

    @Test @DisplayName("D1-C proposing leaves booking location, version and every space's occupancy unchanged")
    void proposeLeavesStateUnchanged() {
        manager.proposeReassignment("B12", "B1");
        assertFixtureUnchanged();
    }

    @Test @DisplayName("baseline policy ignores accessibility: B12 (not accessible) and C03 are both eligible")
    void baselineIgnoresAccessibility() {
        assertInstanceOf(Created.class, manager.proposeReassignment("B12", "B1"));
        assertInstanceOf(Created.class, manager.proposeReassignment("C03", "B1"));
    }

    @Test @DisplayName("each proposal gets a distinct application-generated id")
    void distinctProposalIds() {
        Created first = assertInstanceOf(Created.class, manager.proposeReassignment("B12", "B1"));
        Created second = assertInstanceOf(Created.class, manager.proposeReassignment("C03", "B1"));
        assertNotEquals(first.getProposal().getId(), second.getProposal().getId());
    }

    // ---- D1-D / D1-E: refusals -----------------------------------------------------------------------------------

    @Test @DisplayName("D1-D D09 is refused as OCCUPIED; no proposal; nothing changed")
    void occupiedTargetRefused() {
        assertEquals(RefusalReason.OCCUPIED, refused("D09", "B1").getReason());
        assertTrue(store.findByBooking(B1).isEmpty());
        assertFixtureUnchanged();
    }

    @Test @DisplayName("D1-E unknown target is refused as TARGET_UNKNOWN; no proposal; nothing changed")
    void unknownTargetRefused() {
        assertEquals(RefusalReason.TARGET_UNKNOWN, refused("Z99", "B1").getReason());
        assertTrue(store.findByBooking(B1).isEmpty());
        assertFixtureUnchanged();
    }

    @Test @DisplayName("malformed target ids (null, empty, lowercase, overlong) are refused as TARGET_MALFORMED")
    void malformedTargetRefused() {
        for (String bad : new String[] {null, "", "b12", "A2345678901234567"}) {
            assertEquals(RefusalReason.TARGET_MALFORMED, refused(bad, "B1").getReason(), String.valueOf(bad));
        }
        assertTrue(store.findByBooking(B1).isEmpty());
        assertFixtureUnchanged();
    }

    @Test @DisplayName("malformed and unknown booking ids are separate refusal reasons")
    void bookingRefusals() {
        assertEquals(RefusalReason.BOOKING_MALFORMED, refused("B12", "b1").getReason());
        assertEquals(RefusalReason.BOOKING_MALFORMED, refused("B12", null).getReason());
        assertEquals(RefusalReason.BOOKING_UNKNOWN, refused("B12", "B2").getReason());
        assertFixtureUnchanged();
    }

    @Test @DisplayName("argument order is (target, booking): swapping them does not create a proposal")
    void argumentOrderMatters() {
        assertEquals(RefusalReason.BOOKING_UNKNOWN, refused("B1", "B12").getReason());
        assertTrue(store.findByBooking(B1).isEmpty());
    }

    @Test @DisplayName("precedence: A17 is current space, occupied and closed; CURRENT_SPACE wins")
    void currentSpaceHasPrecedence() {
        assertEquals(RefusalReason.CURRENT_SPACE, refused("A17", "B1").getReason());
    }

    @Test @DisplayName("request checks run before the policy: malformed target beats unknown booking")
    void requestCheckPrecedence() {
        assertEquals(RefusalReason.TARGET_MALFORMED, refused("b12", "B2").getReason());
        assertEquals(RefusalReason.BOOKING_UNKNOWN, refused("Z99", "B2").getReason());
    }

    // ---- policy contract (purpose-built registry; the supplied fixture is not altered) ----------------------------

    @Test @DisplayName("policy precedence CURRENT_SPACE > OCCUPIED > NOT_OPEN, using purpose-built spaces")
    void policyRulePrecedence() {
        Booking booking = new Booking(new Identifier("T1"), new Identifier("S01"), new Duration(1), 0, false);
        PolicyV1 policy = new PolicyV1();
        // id, open, occupied, accessible
        Space current = new Space(new Identifier("S01"), false, true, false);
        Space closedAndOccupied = new Space(new Identifier("X01"), false, true, false);
        Space closedOnly = new Space(new Identifier("X02"), false, false, false);
        Space occupiedOnly = new Space(new Identifier("X03"), true, true, false);
        assertEquals(RefusalReason.CURRENT_SPACE, reason(policy.evaluate(booking, current)));
        assertEquals(RefusalReason.OCCUPIED, reason(policy.evaluate(booking, closedAndOccupied)));
        assertEquals(RefusalReason.NOT_OPEN, reason(policy.evaluate(booking, closedOnly)));
        assertEquals(RefusalReason.OCCUPIED, reason(policy.evaluate(booking, occupiedOnly)));
    }

    @Test @DisplayName("PolicyV1 version is 1 and it only ever returns the three policy reasons")
    void policyContract() {
        PolicyV1 policy = new PolicyV1();
        assertEquals(1, policy.getVersion());
        Booking booking = registry.findBooking(B1).orElseThrow();
        Set<RefusalReason> allowed = EnumSet.of(RefusalReason.CURRENT_SPACE, RefusalReason.OCCUPIED, RefusalReason.NOT_OPEN);
        for (String id : new String[] {"A17", "B12", "C03", "D09"}) {
            PolicyVerdict v = policy.evaluate(booking, registry.findSpace(new Identifier(id)).orElseThrow());
            if (v instanceof Ineligible i) assertTrue(allowed.contains(i.getReason()), id);
        }
    }

    private static RefusalReason reason(PolicyVerdict v) { return assertInstanceOf(Ineligible.class, v).getReason(); }

    // ---- D1-F: controlled access ---------------------------------------------------------------------------------

    @Test @DisplayName("D1-F booking snapshot is immutable and later snapshots still show A17/version 0")
    void bookingSnapshotCannotMutateState() {
        StudentApplication app = new StudentApplication();
        Map<String, Object> snap = app.bookingSnapshot();
        assertThrows(UnsupportedOperationException.class, () -> snap.put("spaceId", "B12"));
        assertThrows(UnsupportedOperationException.class, () -> snap.put("version", 99));
        assertThrows(UnsupportedOperationException.class, () -> snap.remove("id"));
        Map<String, Object> again = app.bookingSnapshot();
        assertEquals("A17", again.get("spaceId"));
        assertEquals(Integer.valueOf(0), again.get("version"));
    }

    @Test @DisplayName("D1-F store hands out unmodifiable copies, not its own collection")
    void storeReturnsUnmodifiableCopies() {
        manager.proposeReassignment("B12", "B1");
        List<Proposal> first = store.findByBooking(B1);
        assertThrows(UnsupportedOperationException.class, () -> first.add(first.get(0)));
        assertThrows(UnsupportedOperationException.class, () -> first.remove(0));
        manager.proposeReassignment("C03", "B1");
        assertEquals(1, first.size(), "an earlier copy must not change when the store grows");
        assertEquals(2, store.findByBooking(B1).size());
    }

    @Test @DisplayName("D1-F entities are structurally immutable: no setters, every declared field private final")
    void entitiesAreStructurallyImmutable() {
        for (Class<?> c : List.of(Identifier.class, Duration.class, Space.class, Booking.class, Proposal.class)) {
            for (Method m : c.getMethods())
                assertFalse(m.getName().startsWith("set"), c.getSimpleName() + " has setter " + m.getName());
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                assertTrue(Modifier.isPrivate(f.getModifiers()) && Modifier.isFinal(f.getModifiers()),
                        c.getSimpleName() + "." + f.getName() + " must be private final");
            }
        }
    }

    // ---- starter boundary (StudentApplication) -------------------------------------------------------------------

    @Test @DisplayName("starter boundary: propose(B12) returns a PENDING view and the booking stays at A17/0")
    void appProposeB12() {
        StudentApplication app = new StudentApplication();
        ProposalView v = app.propose("B12");
        assertEquals(new ProposalView("PROP_1", "B1", "B12", 0, 1, "PENDING"), v);
        assertEquals("A17", app.bookingSnapshot().get("spaceId"));
        assertEquals(Integer.valueOf(0), app.bookingSnapshot().get("version"));
    }

    @Test @DisplayName("starter boundary: D09 and unknown targets throw IllegalArgumentException; booking unchanged")
    void appRefusals() {
        StudentApplication app = new StudentApplication();
        Map<String, Object> before = app.bookingSnapshot();
        assertThrows(IllegalArgumentException.class, () -> app.propose("D09"));
        assertThrows(IllegalArgumentException.class, () -> app.propose("Z99"));
        assertThrows(IllegalArgumentException.class, () -> app.propose(null));
        assertEquals(before, app.bookingSnapshot());
    }
}
