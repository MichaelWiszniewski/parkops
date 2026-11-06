/** Why raw model text was rejected. Declared in the order the parser checks them. */
public enum ProtocolError {
    MALFORMED_JSON,      // not parseable JSON, or not a JSON object
    WRONG_FIELDS,        // missing or additional properties (e.g. "approved")
    NON_STRING_FIELD,    // tool, bookingId or targetId is not a JSON string
    UNKNOWN_TOOL,        // tool is not one of the four allowed names
    WRONG_BOOKING,       // bookingId is not the assigned goal booking
    TARGET_NOT_ALLOWED,  // non-empty targetId on a tool other than PROPOSE_REASSIGNMENT
    TARGET_MALFORMED     // PROPOSE_REASSIGNMENT target missing or not a valid identifier
}
