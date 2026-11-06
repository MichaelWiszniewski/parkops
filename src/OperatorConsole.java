/**
 * Source of the separate human decision. The agent loop never sees this interface: decisions are
 * requested only after run() has returned and a proposal is PENDING.
 */
public interface OperatorConsole {
    OperatorDecision decide(Proposal pending);

    /** Fixed decision for the labelled replay demonstration. It is a test double, not a person. */
    static OperatorConsole scripted(OperatorDecision fixed) {
        if (fixed == null) throw new IllegalArgumentException("decision required");
        return pending -> fixed;
    }
}
