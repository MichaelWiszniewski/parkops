/** The only four operations a model may request. Anything else is rejected before dispatch. */
public enum Tool {
    READ_BOOKING, LIST_ALTERNATIVES, PROPOSE_REASSIGNMENT, FINISH
}
