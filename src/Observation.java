import java.util.Optional;

/** What a dispatched tool returns to the model. The JSON is sent back as the next user message. */
public record Observation(Kind kind, String json, Optional<String> proposalId) {
    public enum Kind { OK, TOOL_REJECTED, AWAITING_APPROVAL, NO_ACTION }

    public Observation {
        if (kind == null || json == null || proposalId == null) throw new IllegalArgumentException("fields required");
        if ((kind == Kind.AWAITING_APPROVAL) != proposalId.isPresent())
            throw new IllegalArgumentException("a proposal id is present exactly when awaiting approval");
    }
    public boolean endsRun() { return kind == Kind.AWAITING_APPROVAL || kind == Kind.NO_ACTION; }
}
