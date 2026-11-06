/**
 * Result of an execute request, declared in the order execute checks them (after UNKNOWN_PROPOSAL).
 * Only COMMITTED changes domain state.
 */
public enum ExecutionOutcome { UNKNOWN_PROPOSAL, DUPLICATE, NOT_APPROVED, STALE, UNAVAILABLE_OR_INELIGIBLE, COMMITTED }
