/**
 * The single policy object shared by ProposalManager, PeriodicDetector and every InvestigationSession.
 * It delegates to whichever policy is currently active, so a policy change is seen by all of them at once
 * without rebuilding anything (the proposal store, commit log and detector state survive the change).
 *
 * activate(next) only accepts a strictly higher version. Allowing an equal or lower version would let an
 * old proposal's captured policyVersion match again, so a proposal that went STALE could become executable.
 * Nesting another ActivePolicy is refused to keep the delegation one level deep.
 *
 * Single-process and sequential only, like ProposalManager; this is not a concurrency guarantee.
 */
public final class ActivePolicy implements ReassignmentPolicy {
    private ReassignmentPolicy current;

    public ActivePolicy(ReassignmentPolicy initial) {
        if (initial == null) throw new IllegalArgumentException("initial policy required");
        if (initial instanceof ActivePolicy) throw new IllegalArgumentException("nested ActivePolicy not allowed");
        this.current = initial;
    }

    /** Switches to next. Throws (and changes nothing) unless next is a non-null, non-nested, higher version. */
    public void activate(ReassignmentPolicy next) {
        if (next == null) throw new IllegalArgumentException("policy required");
        if (next instanceof ActivePolicy) throw new IllegalArgumentException("nested ActivePolicy not allowed");
        if (next.getVersion() <= current.getVersion())
            throw new IllegalArgumentException("policy version must increase: current v" + current.getVersion()
                    + ", requested v" + next.getVersion());
        current = next;
    }

    @Override public PolicyVerdict evaluate(Booking booking, Space target) { return current.evaluate(booking, target); }

    @Override public int getVersion() { return current.getVersion(); }
}
