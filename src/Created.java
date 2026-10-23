/** A proposal was created. A Created outcome always holds one. */
public final class Created implements Result {
    private final Proposal proposal;
    public Created(Proposal proposal) {
        if (proposal == null) throw new IllegalArgumentException("proposal required");
        this.proposal = proposal;
    }
    public Proposal getProposal() { return proposal; }
}
