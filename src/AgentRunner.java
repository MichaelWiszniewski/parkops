import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bounded model/tool loop. Each iteration makes exactly one provider call, validates the raw reply,
 * dispatches it if valid, and appends the observation to the history the next call receives.
 * Every call counts toward the limit, including invalid replies. It never approves or executes.
 */
public final class AgentRunner {
    public static final int MIN_CALLS = 1, MAX_CALLS = 6;

    static final String SYSTEM_PROMPT = String.join("\n",
        "You are a parking-operations agent. Space A17 has closed. Resolve booking B1 using only these tools.",
        "Reply with exactly one JSON object and nothing else: no prose, no markdown, no code fences.",
        "The object has exactly three string properties: tool, bookingId, targetId.",
        "tool is one of READ_BOOKING, LIST_ALTERNATIVES, PROPOSE_REASSIGNMENT, FINISH.",
        "bookingId is always \"B1\". targetId is \"\" except for PROPOSE_REASSIGNMENT, where it is a space id.",
        "Order: READ_BOOKING, then LIST_ALTERNATIVES, then PROPOSE_REASSIGNMENT with one listed candidate.",
        "Use FINISH only if no reassignment is possible. You cannot approve or execute anything;",
        "a human operator decides after you propose.",
        "Example: {\"tool\":\"READ_BOOKING\",\"bookingId\":\"B1\",\"targetId\":\"\"}");
    static final String GOAL = "Resolve the booking affected by the closure of A17.";

    private final ModelProvider provider;
    private final ToolRequestParser parser;
    private final InvestigationSession session;
    private final int limit;

    public AgentRunner(ModelProvider provider, ToolRequestParser parser, InvestigationSession session, int limit) {
        if (provider == null || parser == null || session == null) throw new IllegalArgumentException("collaborators required");
        if (limit < MIN_CALLS || limit > MAX_CALLS)
            throw new IllegalArgumentException("call limit must be " + MIN_CALLS + ".." + MAX_CALLS + ", got " + limit);
        this.provider = provider; this.parser = parser; this.session = session; this.limit = limit;
    }

    public RunResult run() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(new ChatMessage("system", SYSTEM_PROMPT));
        history.add(new ChatMessage("user", GOAL));
        int calls = 0, invalid = 0, toolRejections = 0;

        while (calls < limit) {
            String raw;
            calls++;
            try {
                raw = provider.next(List.copyOf(history));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return end(RunStatus.PROVIDER_ERROR, Optional.empty(), calls, invalid, toolRejections, history);
            } catch (Exception ex) {   // IOException incl. HttpTimeoutException, RuntimeException, anything the provider throws
                return end(RunStatus.PROVIDER_ERROR, Optional.empty(), calls, invalid, toolRejections, history);
            }
            history.add(new ChatMessage("assistant", raw == null ? "" : raw));

            switch (parser.parse(raw)) {
                case ParseOutcome.Rejected r -> {
                    invalid++;
                    history.add(new ChatMessage("user", invalidObservation(r)));
                }
                case ParseOutcome.Parsed p -> {
                    Observation obs = session.dispatch(p.request());
                    history.add(new ChatMessage("user", obs.json()));
                    if (obs.kind() == Observation.Kind.TOOL_REJECTED) toolRejections++;
                    if (obs.kind() == Observation.Kind.AWAITING_APPROVAL)
                        return end(RunStatus.AWAITING_APPROVAL, obs.proposalId(), calls, invalid, toolRejections, history);
                    if (obs.kind() == Observation.Kind.NO_ACTION)
                        return end(RunStatus.NO_ACTION, Optional.empty(), calls, invalid, toolRejections, history);
                }
            }
        }
        return end(RunStatus.STEP_LIMIT, Optional.empty(), calls, invalid, toolRejections, history);
    }

    private static String invalidObservation(ParseOutcome.Rejected r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "INVALID_REQUEST");
        out.put("error", r.error().name());
        out.put("message", r.message());
        return MiniJson.stringify(out);
    }

    private static RunResult end(RunStatus s, Optional<String> id, int calls, int invalid, int rej, List<ChatMessage> h) {
        return new RunResult(s, id, calls, invalid, rej, h);
    }
}
