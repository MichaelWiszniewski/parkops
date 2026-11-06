/** How one agent run ended. None of these outcomes commits a booking. */
public enum RunStatus {
    AWAITING_APPROVAL,  // a PENDING proposal was created; a separate operator decision is now required
    NO_ACTION,          // the model called FINISH
    STEP_LIMIT,         // the configured number of model calls was used up
    PROVIDER_ERROR      // the provider threw or timed out
}
