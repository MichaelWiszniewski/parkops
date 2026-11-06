import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application coordination for ONE investigation of the goal booking. It enforces tool order
 * (read, then list, then propose) and rechecks the captured booking/policy versions before LIST and
 * PROPOSE. If they are stale, the investigation restarts: a fresh READ_BOOKING is required.
 * It never moves a booking; the only state it can cause is one PENDING proposal, via ProposalManager.
 */
public final class InvestigationSession {
    private final Identifier goal;
    private final ParkingRegistry registry;
    private final ProposalManager manager;
    private final ReassignmentPolicy policy;

    private Integer readBookingVersion;   // null until READ_BOOKING succeeds
    private Integer readPolicyVersion;
    private boolean listed;

    public InvestigationSession(Identifier goal, ParkingRegistry registry, ProposalManager manager, ReassignmentPolicy policy) {
        if (goal == null || registry == null || manager == null || policy == null)
            throw new IllegalArgumentException("collaborators required");
        this.goal = goal; this.registry = registry; this.manager = manager; this.policy = policy;
    }

    public Observation dispatch(ToolRequest request) {
        if (request == null || !request.bookingId().equals(goal))
            throw new IllegalArgumentException("only validated requests for the goal booking may be dispatched");
        return switch (request.tool()) {
            case READ_BOOKING -> read();
            case LIST_ALTERNATIVES -> list();
            case PROPOSE_REASSIGNMENT -> propose(request.targetId().orElseThrow());
            case FINISH -> new Observation(Observation.Kind.NO_ACTION, json("status", "NO_ACTION"), Optional.empty());
        };
    }

    private Observation read() {
        Booking b = currentBooking();
        readBookingVersion = b.getVersion();
        readPolicyVersion = policy.getVersion();
        listed = false;
        Map<String, Object> booking = new LinkedHashMap<>();
        booking.put("id", b.getId().getValue());
        booking.put("spaceId", b.getSpaceId().getValue());
        booking.put("durationHours", b.getDuration().getHours());
        booking.put("version", b.getVersion());
        booking.put("accessibilityRequired", b.isAccessibilityRequired());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "OK");
        out.put("tool", "READ_BOOKING");
        out.put("booking", booking);
        out.put("policyVersion", readPolicyVersion);
        return ok(out);
    }

    private Observation list() {
        if (readBookingVersion == null) return rejected("ORDER", "LIST_ALTERNATIVES requires READ_BOOKING first.");
        if (stale()) return restart();
        Booking b = currentBooking();
        List<String> candidates = new ArrayList<>();
        for (Space s : registry.allSpaces())
            if (policy.evaluate(b, s) instanceof Eligible) candidates.add(s.getId().getValue());
        listed = true;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "OK");
        out.put("tool", "LIST_ALTERNATIVES");
        out.put("candidates", candidates);
        return ok(out);
    }

    private Observation propose(Identifier target) {
        if (readBookingVersion == null || !listed)
            return rejected("ORDER", "PROPOSE_REASSIGNMENT requires READ_BOOKING then LIST_ALTERNATIVES first.");
        if (stale()) return restart();
        return switch (manager.proposeReassignment(target.getValue(), goal.getValue())) {
            case Created c -> {
                String id = c.getProposal().getId().getValue();
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("status", "AWAITING_APPROVAL");
                out.put("proposalId", id);
                out.put("targetId", target.getValue());
                yield new Observation(Observation.Kind.AWAITING_APPROVAL, MiniJson.stringify(out), Optional.of(id));
            }
            case Refused r -> rejected(r.getReason().name(), r.getMessage());
        };
    }

    private boolean stale() {
        return currentBooking().getVersion() != readBookingVersion || policy.getVersion() != readPolicyVersion;
    }

    private Observation restart() {
        readBookingVersion = null; readPolicyVersion = null; listed = false;
        return rejected("STALE_INVESTIGATION", "Booking or policy version changed; call READ_BOOKING again.");
    }

    private Booking currentBooking() {
        return registry.findBooking(goal).orElseThrow(() -> new IllegalStateException("goal booking missing"));
    }

    private static Observation ok(Map<String, Object> out) {
        return new Observation(Observation.Kind.OK, MiniJson.stringify(out), Optional.empty());
    }
    private static Observation rejected(String error, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "TOOL_REJECTED");
        out.put("error", error);
        out.put("message", message);
        return new Observation(Observation.Kind.TOOL_REJECTED, MiniJson.stringify(out), Optional.empty());
    }
    private static String json(String k, String v) { return MiniJson.stringify(Map.of(k, v)); }
}
