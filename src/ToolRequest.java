import java.util.Objects;
import java.util.Optional;

/**
 * A model request that has passed strict protocol validation. Immutable.
 * The target is present only for PROPOSE_REASSIGNMENT. It carries no approval flag:
 * the model can never supply authority.
 */
public record ToolRequest(Tool tool, Identifier bookingId, Optional<Identifier> targetId) {
    public ToolRequest {
        Objects.requireNonNull(tool, "tool required");
        Objects.requireNonNull(bookingId, "bookingId required");
        Objects.requireNonNull(targetId, "targetId must be Optional, not null");
        if ((tool == Tool.PROPOSE_REASSIGNMENT) != targetId.isPresent())
            throw new IllegalArgumentException("a target is required for, and only for, PROPOSE_REASSIGNMENT");
    }
}
