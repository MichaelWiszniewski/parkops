import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Protocol boundary: turns raw, untrusted model text into a ToolRequest or a controlled rejection.
 * It knows only the wire format and the goal booking. It does not read the registry; whether a
 * well-formed target actually exists is decided later, at dispatch, by the domain layer.
 * The response is treated as data only: no reflection, method lookup or execution of its contents.
 */
public final class ToolRequestParser {
    private static final Set<String> FIELDS = Set.of("tool", "bookingId", "targetId");
    private final Identifier goalBooking;

    public ToolRequestParser(Identifier goalBooking) {
        if (goalBooking == null) throw new IllegalArgumentException("goal booking required");
        this.goalBooking = goalBooking;
    }

    public ParseOutcome parse(String raw) {
        Map<String, Object> json;
        try {
            json = MiniJson.object(MiniJson.parse(raw));   // also rejects duplicate keys and trailing input
        } catch (IllegalArgumentException ex) {
            return reject(ProtocolError.MALFORMED_JSON, "Response is not one JSON object.");
        }
        if (!json.keySet().equals(FIELDS))
            return reject(ProtocolError.WRONG_FIELDS, "Exactly tool, bookingId and targetId are required; got " + json.keySet() + ".");
        if (!(json.get("tool") instanceof String toolName)
                || !(json.get("bookingId") instanceof String booking)
                || !(json.get("targetId") instanceof String target))
            return reject(ProtocolError.NON_STRING_FIELD, "tool, bookingId and targetId must all be strings.");

        Tool tool = toolNamed(toolName);
        if (tool == null)
            return reject(ProtocolError.UNKNOWN_TOOL, "Unknown tool. Allowed: READ_BOOKING, LIST_ALTERNATIVES, PROPOSE_REASSIGNMENT, FINISH.");
        if (!booking.equals(goalBooking.getValue()))
            return reject(ProtocolError.WRONG_BOOKING, "bookingId must be the goal booking " + goalBooking.getValue() + ".");

        if (tool != Tool.PROPOSE_REASSIGNMENT) {
            if (!target.isEmpty())
                return reject(ProtocolError.TARGET_NOT_ALLOWED, "targetId must be empty for " + tool + ".");
            return new ParseOutcome.Parsed(new ToolRequest(tool, goalBooking, Optional.empty()));
        }
        if (!Identifier.isValid(target))
            return reject(ProtocolError.TARGET_MALFORMED, "PROPOSE_REASSIGNMENT needs a valid space identifier as targetId.");
        return new ParseOutcome.Parsed(new ToolRequest(tool, goalBooking, Optional.of(new Identifier(target))));
    }

    /** Exact, case-sensitive match against the fixed enum; an unknown name gives null, not an exception. */
    private static Tool toolNamed(String name) {
        for (Tool t : Tool.values()) if (t.name().equals(name)) return t;
        return null;
    }

    private static ParseOutcome reject(ProtocolError error, String message) {
        return new ParseOutcome.Rejected(error, message);
    }
}
