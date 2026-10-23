/** Declared in precedence order: request checks first, then the three policy rules. */
public enum RefusalReason {
    TARGET_MALFORMED, BOOKING_MALFORMED, BOOKING_UNKNOWN, TARGET_UNKNOWN,
    CURRENT_SPACE, OCCUPIED, NOT_OPEN
}
