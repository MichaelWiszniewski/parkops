import java.util.function.Consumer;

/**
 * Prepared end-to-end demonstration, shared by the replay and live commands so both follow the same path:
 * detect (tick) -> investigate (run, model + tools) -> PENDING -> separate operator decision -> execute
 * with fresh validation -> repeat execute (duplicate check) -> tick again. Output goes to the supplied sink
 * line by line, so an interactive operator prompt appears after the transcript it relates to.
 */
public final class WorkflowDemo {
    private WorkflowDemo() { }

    public static void run(StudentApplication app, ModelProvider provider, OperatorConsole operator,
                           int limit, Consumer<String> out) {
        TickResult first = app.tick(0);
        out.accept("DETECT tick(0) -> " + first.status() + " work items: " + first.workItems().size());
        for (WorkItem w : first.workItems())
            out.accept("  work: " + w.bookingId().getValue() + " on closed " + w.closedSpaceId().getValue()
                    + " (booking v" + w.bookingVersion() + ", policy v" + w.policyVersion() + ")");

        out.accept("INVESTIGATE (call limit " + limit + ")");
        RunResult result = app.runAgent(provider, limit);
        for (String line : app.format(result).split("\n")) out.accept("  " + line);

        if (result.status() != RunStatus.AWAITING_APPROVAL) {
            out.accept("No pending proposal; no operator decision requested and nothing committed.");
        } else {
            String id = result.proposalId().orElseThrow();
            Proposal pending = app.proposal(id).orElseThrow();
            out.accept("PENDING " + id + ": " + pending.getBookingId().getValue() + " -> "
                    + pending.getTargetId().getValue() + " (captured booking v" + pending.getBookingVersion()
                    + ", policy v" + pending.getPolicyVersion() + ")");
            OperatorDecision decision = operator.decide(pending);
            out.accept("OPERATOR decision (separate action): " + decision);
            switch (decision) {
                case APPROVE -> {
                    out.accept("  approve(" + id + ") -> " + app.approve(id));
                    out.accept("  execute(" + id + ") -> " + app.execute(id));
                    out.accept("  execute(" + id + ") again -> " + app.execute(id));
                }
                case REJECT -> {
                    out.accept("  reject(" + id + ") -> " + app.reject(id));
                    out.accept("  execute(" + id + ") -> " + app.execute(id));
                }
                case DEFER -> out.accept("  left PENDING; execute is not attempted");
            }
            out.accept("  proposal status now: " + app.proposalStatus(id).orElseThrow());
        }

        out.accept("STATE booking B1 at " + app.bookingSnapshot().get("spaceId") + " version "
                + app.bookingSnapshot().get("version") + "; commits " + app.commitCount()
                + "; simulated notifications " + app.notificationCount());
        for (Notification n : app.notifications()) out.accept("  notification (simulated): " + n.message());
        TickResult again = app.tick(60);
        out.accept("DETECT tick(60) -> " + again.status() + " work items: " + again.workItems().size());
    }
}
