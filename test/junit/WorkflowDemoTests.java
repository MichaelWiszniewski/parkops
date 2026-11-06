import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D2 Step 7: the shared demonstration path and the separate operator boundary. */
class WorkflowDemoTests {
    private static final OperatorConsole MUST_NOT_ASK = p -> { fail("operator must not be asked"); return null; };

    private List<String> demo(StudentApplication app, ModelProvider provider, OperatorConsole operator) {
        List<String> lines = new ArrayList<>();
        WorkflowDemo.run(app, provider, operator, 6, lines::add);
        return lines;
    }
    private static boolean has(List<String> lines, String text) { return lines.stream().anyMatch(l -> l.contains(text)); }

    @Test @DisplayName("Approve: one commit, duplicate on repeat, one notification, nothing new on the next tick")
    void approvePath() {
        StudentApplication app = new StudentApplication();
        List<String> out = demo(app, Fixture.replay(), OperatorConsole.scripted(OperatorDecision.APPROVE));
        assertTrue(has(out, "execute(PROP_1) -> COMMITTED"));
        assertTrue(has(out, "execute(PROP_1) again -> DUPLICATE"));
        assertTrue(has(out, "DETECT tick(60) -> SCANNED work items: 0"));
        assertEquals("B12", app.bookingSnapshot().get("spaceId"));
        assertEquals(1, app.bookingSnapshot().get("version"));
        assertEquals(1, app.commitCount());
        assertEquals(1, app.notificationCount());
    }

    @Test @DisplayName("Reject: execute is NOT_APPROVED and the booking stays at A17/version 0")
    void rejectPath() {
        StudentApplication app = new StudentApplication();
        List<String> out = demo(app, Fixture.replay(), OperatorConsole.scripted(OperatorDecision.REJECT));
        assertTrue(has(out, "execute(PROP_1) -> NOT_APPROVED"));
        assertEquals("A17", app.bookingSnapshot().get("spaceId"));
        assertEquals(0, app.bookingSnapshot().get("version"));
        assertEquals(0, app.commitCount());
        assertEquals(0, app.notificationCount());
    }

    @Test @DisplayName("Defer: the proposal stays PENDING, execute is never attempted, nothing committed")
    void deferPath() {
        StudentApplication app = new StudentApplication();
        List<String> out = demo(app, Fixture.replay(), OperatorConsole.scripted(OperatorDecision.DEFER));
        assertTrue(has(out, "proposal status now: PENDING"));
        assertFalse(has(out, "execute("));
        assertEquals(0, app.commitCount());
    }

    @Test @DisplayName("Provider error: the operator is never asked and nothing is committed")
    void providerErrorNoOperator() {
        StudentApplication app = new StudentApplication();
        List<String> out = demo(app, h -> { throw new IOException("down"); }, MUST_NOT_ASK);
        assertTrue(has(out, "OUTCOME PROVIDER_ERROR"));
        assertTrue(has(out, "no operator decision requested"));
        assertEquals(0, app.commitCount());
    }

    @Test @DisplayName("Step limit: the operator is never asked and nothing is committed")
    void stepLimitNoOperator() {
        StudentApplication app = new StudentApplication();
        List<String> out = demo(app, h -> "not json", MUST_NOT_ASK);
        assertTrue(has(out, "OUTCOME STEP_LIMIT"));
        assertTrue(has(out, "calls=6 invalid=6"));
        assertEquals(0, app.commitCount());
    }

    @Test @DisplayName("The operator receives the pending proposal itself and can see its target")
    void operatorSeesPendingProposal() {
        StudentApplication app = new StudentApplication();
        Proposal[] seen = new Proposal[1];
        demo(app, Fixture.replay(), p -> { seen[0] = p; return OperatorDecision.DEFER; });
        assertEquals(ProposalStatus.PENDING, seen[0].getStatus());
        assertEquals("B12", seen[0].getTargetId().getValue());
    }
}
