/** Immutable transport view for the starter checks; your domain model remains your design. */
public record ProposalView(String proposalId,String bookingId,String targetId,
                           int bookingVersion,int policyVersion,String status) { }
