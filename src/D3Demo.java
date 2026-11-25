import java.util.List;
import java.util.function.Consumer;

/**
 * Prepared D3 demonstration (policy version 2), run by "bash run.sh run d3-scripted". Every model reply
 * comes from ReplayProvider (a test double, not a live model) and every operator decision is scripted, so
 * the output is repeatable. Each part starts from a clean fixture. The case labels match TEST_PLAN.md and
 * D3ChangeCaseTests; the tests, not this printout, are the assertions.
 */
public final class D3Demo {
    private static final int LIMIT = 6;
    private static final String READ = "{\"tool\":\"READ_BOOKING\",\"bookingId\":\"B1\",\"targetId\":\"\"}";
    private static final String LIST = "{\"tool\":\"LIST_ALTERNATIVES\",\"bookingId\":\"B1\",\"targetId\":\"\"}";
    private static final String FINISH = "{\"tool\":\"FINISH\",\"bookingId\":\"B1\",\"targetId\":\"\"}";

    private D3Demo() { }

    public static void run(Consumer<String> out) {
        baseline(out);
        change(out);
        nonRequiringBooking(out);
    }

    /** Part 1, clean fixture: what policy 1 offers. */
    private static void baseline(Consumer<String> out) {
        out.accept("=== PART 1: baseline, policy v1, clean B1 fixture ===");
        StudentApplication app = new StudentApplication();
        out.accept("policy version: " + app.policyVersion());
        investigate(app, List.of(READ, LIST, FINISH), "[D3-A] read and list only", out);
        state(app, out);
    }

    /** Part 2, clean fixture: v1 approval, switch to v2, stale refusal, periodic tick, fresh v2 proposal. */
    private static void change(Consumer<String> out) {
        out.accept("");
        out.accept("=== PART 2: change to policy v2, clean B1 fixture ===");
        StudentApplication app = new StudentApplication();
        tick(app, 0, out);
        RunResult old = investigate(app, List.of(READ, LIST, Fixture.proposalRequest("B12")),
                "v1 investigation proposes B12", out);
        String oldId = old.proposalId().orElseThrow();
        out.accept("OPERATOR (scripted) approve(" + oldId + ") -> " + app.approve(oldId));

        out.accept("ACTIVATE activatePolicyV2() -> " + app.activatePolicyV2() + "; policy version now " + app.policyVersion());
        out.accept("  activatePolicyV2() again -> " + app.activatePolicyV2() + " (already active; nothing changes)");
        out.accept("[D3-C] execute(" + oldId + ") captured policy v" + app.proposal(oldId).orElseThrow().getPolicyVersion()
                + " -> " + app.execute(oldId));
        state(app, out);

        out.accept("[D3-I] periodic checker after the version change:");
        tick(app, 30, out);
        tick(app, 60, out);
        tick(app, 120, out);

        RunResult fresh = investigate(app, List.of(READ, LIST, Fixture.proposalRequest("B12"), Fixture.proposalRequest("C03")),
                "[D3-B][D3-H] fresh v2 investigation; model first ignores the list and proposes B12", out);
        out.accept("[D3-D] direct propose(B12) under v2 -> " + refusal(app, "B12"));
        String newId = fresh.proposalId().orElseThrow();
        out.accept("PENDING " + newId + " -> " + app.proposal(newId).orElseThrow().getTargetId().getValue()
                + " (captured policy v" + app.proposal(newId).orElseThrow().getPolicyVersion() + ")");
        out.accept("OPERATOR (scripted) approve(" + newId + ") -> " + app.approve(newId));
        out.accept("[D3-E] execute(" + newId + ") -> " + app.execute(newId));
        out.accept("[D3-F] execute(" + newId + ") again -> " + app.execute(newId));
        out.accept("  execute(" + oldId + ") again -> " + app.execute(oldId) + " (old proposal still never runs)");
        state(app, out);
        for (Notification n : app.notifications()) out.accept("  notification (simulated): " + n.message());
    }

    /** Part 3, clean variant fixture: a booking that does not require an accessible space. */
    private static void nonRequiringBooking(Consumer<String> out) {
        out.accept("");
        out.accept("=== PART 3: B1 without the accessibility requirement, policy v2, clean fixture ===");
        StudentApplication app = StudentApplication.withoutAccessibilityRequirement();
        out.accept("ACTIVATE activatePolicyV2() -> " + app.activatePolicyV2() + "; policy version now " + app.policyVersion());
        RunResult r = investigate(app, List.of(READ, LIST, Fixture.proposalRequest("B12")),
                "[D3-G] v2 investigation for a non-requiring booking", out);
        String id = r.proposalId().orElseThrow();
        out.accept("OPERATOR (scripted) approve(" + id + ") -> " + app.approve(id));
        out.accept("  execute(" + id + ") -> " + app.execute(id));
        out.accept("  direct propose(D09) -> " + refusal(app, "D09") + " (general availability still enforced)");
        state(app, out);
    }

    // ---- printing helpers ----------------------------------------------------------------------------------------

    private static RunResult investigate(StudentApplication app, List<String> replies, String label, Consumer<String> out) {
        out.accept("INVESTIGATE " + label + " (replay, call limit " + LIMIT + ")");
        RunResult r = app.runAgent(new ReplayProvider(replies), LIMIT);
        for (String line : app.format(r).split("\n")) out.accept("  " + line);
        return r;
    }

    private static void tick(StudentApplication app, long t, Consumer<String> out) {
        TickResult r = app.tick(t);
        StringBuilder line = new StringBuilder("  tick(" + t + ") -> " + r.status() + ", work items: " + r.workItems().size());
        for (WorkItem w : r.workItems())
            line.append(" [").append(w.bookingId().getValue()).append(" on closed ").append(w.closedSpaceId().getValue())
                .append(", booking v").append(w.bookingVersion()).append(", policy v").append(w.policyVersion()).append(']');
        out.accept(line.toString());
    }

    private static String refusal(StudentApplication app, String target) {
        try {
            return "unexpectedly created " + app.propose(target);
        } catch (IllegalArgumentException ex) {
            return "refused: " + ex.getMessage();
        }
    }

    private static void state(StudentApplication app, Consumer<String> out) {
        out.accept("STATE booking B1 at " + app.bookingSnapshot().get("spaceId") + " version "
                + app.bookingSnapshot().get("version") + "; commits " + app.commitCount()
                + "; simulated notifications " + app.notificationCount());
    }
}
