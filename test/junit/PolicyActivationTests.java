import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D3 step 3: the application-level hooks, activatePolicyV2() and the D3-G fixture variant.
 * The full change cases D3-A..D3-I (stale approval, agent, periodic checker) are tested separately.
 */
class PolicyActivationTests {

    @Test @DisplayName("a new application starts under policy version 1")
    void startsAtV1() {
        assertEquals(1, new StudentApplication().policyVersion());
        assertEquals(1, StudentApplication.withoutAccessibilityRequirement().policyVersion());
    }

    @Test @DisplayName("activatePolicyV2 switches to version 2 once; a second call returns false and changes nothing")
    void activateOnce() {
        StudentApplication app = new StudentApplication();
        assertTrue(app.activatePolicyV2());
        assertEquals(2, app.policyVersion());
        assertFalse(app.activatePolicyV2());
        assertEquals(2, app.policyVersion());
    }

    @Test @DisplayName("activation alone changes no booking, space, commit or notification")
    void activationHasNoDomainEffect() {
        StudentApplication app = new StudentApplication();
        Map<String, Object> before = app.bookingSnapshot();
        app.activatePolicyV2();
        assertEquals(before, app.bookingSnapshot());
        assertEquals(0, app.commitCount());
        assertEquals(0, app.notificationCount());
    }

    @Test @DisplayName("after activation the boundary refuses B12 as NOT_ACCESSIBLE and creates C03 at policy v2")
    void boundaryUsesV2() {
        StudentApplication app = new StudentApplication();
        app.activatePolicyV2();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> app.propose("B12"));
        assertTrue(ex.getMessage().contains("NOT_ACCESSIBLE"), ex.getMessage());
        assertEquals(new ProposalView("PROP_1", "B1", "C03", 0, 2, "PENDING"), app.propose("C03"));
        assertEquals("A17", app.bookingSnapshot().get("spaceId"));
        assertEquals(Integer.valueOf(0), app.bookingSnapshot().get("version"));
    }

    @Test @DisplayName("each application has its own policy: activating one leaves another at version 1")
    void applicationsIndependent() {
        StudentApplication a = new StudentApplication(), b = new StudentApplication();
        a.activatePolicyV2();
        assertEquals(1, b.policyVersion());
        assertEquals("B12", b.propose("B12").targetId());
    }

    @Test @DisplayName("D3-G variant differs from the fixture only in the accessibility flag; Fixture data untouched")
    void variantFixture() {
        Map<String, Object> normal = new StudentApplication().bookingSnapshot();
        Map<String, Object> variant = StudentApplication.withoutAccessibilityRequirement().bookingSnapshot();
        assertEquals(Boolean.TRUE, normal.get("accessibilityRequired"));
        assertEquals(Boolean.FALSE, variant.get("accessibilityRequired"));
        for (String key : new String[] {"id", "spaceId", "durationHours", "version"})
            assertEquals(normal.get(key), variant.get(key), key);
        assertEquals(Boolean.TRUE, Fixture.booking().get("accessibilityRequired"));
    }

    @Test @DisplayName("D3-G variant under policy v2 can still propose B12 (general availability rules only)")
    void variantKeepsB12UnderV2() {
        StudentApplication app = StudentApplication.withoutAccessibilityRequirement();
        app.activatePolicyV2();
        ProposalView v = app.propose("B12");
        assertEquals("B12", v.targetId());
        assertEquals(2, v.policyVersion());
        assertThrows(IllegalArgumentException.class, () -> app.propose("D09"));   // occupied stays refused
    }
}
