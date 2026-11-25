/**
 * Declared in precedence order: request checks first, then the three PolicyV1 rules, then the PolicyV2
 * accessibility rule (only ever returned by PolicyV2, and only after every v1 rule has passed).
 */
public enum RefusalReason {
    TARGET_MALFORMED, BOOKING_MALFORMED, BOOKING_UNKNOWN, TARGET_UNKNOWN,
    CURRENT_SPACE, OCCUPIED, NOT_OPEN,
    NOT_ACCESSIBLE
}
