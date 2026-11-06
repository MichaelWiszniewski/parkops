import java.util.List;
import java.util.Optional;

/**
 * Immutable summary of one agent run: outcome, the pending proposal (if any), call counts and the full
 * request/observation transcript that was sent to the provider.
 */
public record RunResult(RunStatus status, Optional<String> proposalId, int calls, int invalidResponses,
                        int toolRejections, List<ChatMessage> transcript) {
    public RunResult {
        if (status == null || proposalId == null || transcript == null) throw new IllegalArgumentException("fields required");
        if ((status == RunStatus.AWAITING_APPROVAL) != proposalId.isPresent())
            throw new IllegalArgumentException("a proposal id is present exactly when awaiting approval");
        if (calls < 0 || invalidResponses < 0 || toolRejections < 0 || invalidResponses + toolRejections > calls)
            throw new IllegalArgumentException("inconsistent counts");
        transcript = List.copyOf(transcript);
    }
}
