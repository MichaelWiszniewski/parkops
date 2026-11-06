import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** D2 Step 3: strict model-request protocol (supports acceptance cases D2-H and D2-I). */
class ToolProtocolTests {
    private final ToolRequestParser parser = new ToolRequestParser(new Identifier("B1"));

    private static String req(String tool, String booking, String target) {
        return "{\"tool\":\"" + tool + "\",\"bookingId\":\"" + booking + "\",\"targetId\":\"" + target + "\"}";
    }
    private ToolRequest parsed(String raw) {
        return assertInstanceOf(ParseOutcome.Parsed.class, parser.parse(raw)).request();
    }
    private ProtocolError rejected(String raw) {
        return assertInstanceOf(ParseOutcome.Rejected.class, parser.parse(raw)).error();
    }

    // ---- accepted requests ------------------------------------------------------------------------------------

    @Test @DisplayName("All three non-proposal tools accepted with an empty target")
    void nonProposalToolsAccepted() {
        for (Tool t : new Tool[]{Tool.READ_BOOKING, Tool.LIST_ALTERNATIVES, Tool.FINISH}) {
            ToolRequest r = parsed(req(t.name(), "B1", ""));
            assertEquals(t, r.tool());
            assertEquals("B1", r.bookingId().getValue());
            assertEquals(Optional.empty(), r.targetId());
        }
    }

    @Test @DisplayName("Proposal accepted with a valid target; whitespace between tokens is allowed")
    void proposalAccepted() {
        ToolRequest r = parsed("{ \"tool\" : \"PROPOSE_REASSIGNMENT\", \"bookingId\":\"B1\", \"targetId\":\"C03\" }");
        assertEquals(Tool.PROPOSE_REASSIGNMENT, r.tool());
        assertEquals("C03", r.targetId().orElseThrow().getValue());
    }

    @Test @DisplayName("Field order does not matter")
    void fieldOrderIrrelevant() {
        assertEquals(Tool.READ_BOOKING, parsed("{\"targetId\":\"\",\"tool\":\"READ_BOOKING\",\"bookingId\":\"B1\"}").tool());
    }

    // ---- D2-H: malformed JSON or extra approved field ----------------------------------------------------------

    @ParameterizedTest(name = "malformed: {0}")
    @ValueSource(strings = {"", "   ", "not json", "{", "{\"tool\":\"READ_BOOKING\"",
            "[\"READ_BOOKING\"]", "\"READ_BOOKING\"", "null",
            "```json\n{\"tool\":\"READ_BOOKING\",\"bookingId\":\"B1\",\"targetId\":\"\"}\n```",
            "{\"tool\":\"READ_BOOKING\",\"bookingId\":\"B1\",\"targetId\":\"\"} {\"tool\":\"FINISH\",\"bookingId\":\"B1\",\"targetId\":\"\"}"})
    @DisplayName("D2-H malformed text, non-objects, code fences and two objects are rejected")
    void malformedRejected(String raw) {
        assertEquals(ProtocolError.MALFORMED_JSON, rejected(raw));
    }

    @Test @DisplayName("Null response is rejected, not thrown")
    void nullRejected() {
        assertEquals(ProtocolError.MALFORMED_JSON, rejected(null));
    }

    @Test @DisplayName("D2-H extra approved:true field is rejected and grants no authority")
    void approvedFieldRejected() {
        ParseOutcome out = parser.parse(
                "{\"tool\":\"PROPOSE_REASSIGNMENT\",\"bookingId\":\"B1\",\"targetId\":\"B12\",\"approved\":true}");
        assertEquals(ProtocolError.WRONG_FIELDS, assertInstanceOf(ParseOutcome.Rejected.class, out).error());
        // ToolRequest has no approval component at all, so a model cannot express authority even when valid.
        assertEquals(3, ToolRequest.class.getRecordComponents().length);
    }

    @ParameterizedTest(name = "fields: {0}")
    @ValueSource(strings = {"{}", "{\"tool\":\"READ_BOOKING\",\"bookingId\":\"B1\"}",
            "{\"tool\":\"READ_BOOKING\",\"targetId\":\"\"}",
            "{\"tool\":\"READ_BOOKING\",\"bookingId\":\"B1\",\"targetId\":\"\",\"note\":\"x\"}"})
    @DisplayName("D2-H missing or additional properties are rejected")
    void wrongFieldsRejected(String raw) {
        assertEquals(ProtocolError.WRONG_FIELDS, rejected(raw));
    }

    @Test @DisplayName("Duplicate keys are rejected as malformed")
    void duplicateKeyRejected() {
        assertEquals(ProtocolError.MALFORMED_JSON,
                rejected("{\"tool\":\"FINISH\",\"tool\":\"PROPOSE_REASSIGNMENT\",\"bookingId\":\"B1\",\"targetId\":\"B12\"}"));
    }

    @ParameterizedTest(name = "non-string: {0}")
    @ValueSource(strings = {"{\"tool\":1,\"bookingId\":\"B1\",\"targetId\":\"\"}",
            "{\"tool\":\"READ_BOOKING\",\"bookingId\":null,\"targetId\":\"\"}",
            "{\"tool\":\"READ_BOOKING\",\"bookingId\":\"B1\",\"targetId\":false}",
            "{\"tool\":[\"READ_BOOKING\"],\"bookingId\":\"B1\",\"targetId\":\"\"}"})
    @DisplayName("D2-H non-string property values are rejected")
    void nonStringRejected(String raw) {
        assertEquals(ProtocolError.NON_STRING_FIELD, rejected(raw));
    }

    // ---- D2-I: unknown tool or wrong goal booking --------------------------------------------------------------

    @ParameterizedTest(name = "tool: {0}")
    @ValueSource(strings = {"DELETE_BOOKING", "read_booking", "COMMIT", "EXECUTE", "APPROVE", "", "Runtime.exec", "READ_BOOKING "})
    @DisplayName("D2-I unknown, lowercase, padded and authority-sounding tool names are rejected")
    void unknownToolRejected(String tool) {
        assertEquals(ProtocolError.UNKNOWN_TOOL, rejected(req(tool, "B1", "")));
    }

    @ParameterizedTest(name = "booking: {0}")
    @ValueSource(strings = {"B2", "b1", "", "A17"})
    @DisplayName("D2-I a booking other than the goal booking is rejected")
    void wrongBookingRejected(String booking) {
        assertEquals(ProtocolError.WRONG_BOOKING, rejected(req("READ_BOOKING", booking, "")));
        assertEquals(ProtocolError.WRONG_BOOKING, rejected(req("PROPOSE_REASSIGNMENT", booking, "B12")));
    }

    // ---- target rules ------------------------------------------------------------------------------------------

    @Test @DisplayName("A non-empty target on a non-proposal tool is rejected")
    void targetNotAllowed() {
        assertEquals(ProtocolError.TARGET_NOT_ALLOWED, rejected(req("READ_BOOKING", "B1", "B12")));
        assertEquals(ProtocolError.TARGET_NOT_ALLOWED, rejected(req("FINISH", "B1", "B12")));
    }

    @ParameterizedTest(name = "target: \"{0}\"")
    @ValueSource(strings = {"", "b12", "12B", "ABCDEFGHIJKLMNOPQ", "B 12"})
    @DisplayName("A proposal with an empty or malformed target is rejected")
    void proposalTargetMalformed(String target) {
        assertEquals(ProtocolError.TARGET_MALFORMED, rejected(req("PROPOSE_REASSIGNMENT", "B1", target)));
    }

    @Test @DisplayName("A well-formed but unknown target passes the protocol; existence is a domain check at dispatch")
    void unknownTargetLeftToDomain() {
        assertEquals("Z99", parsed(req("PROPOSE_REASSIGNMENT", "B1", "Z99")).targetId().orElseThrow().getValue());
    }

    // ---- ToolRequest invariant ---------------------------------------------------------------------------------

    @Test @DisplayName("ToolRequest cannot be built with a target on the wrong tool, or without one on a proposal")
    void toolRequestInvariant() {
        Identifier b1 = new Identifier("B1"), b12 = new Identifier("B12");
        assertThrows(IllegalArgumentException.class, () -> new ToolRequest(Tool.READ_BOOKING, b1, Optional.of(b12)));
        assertThrows(IllegalArgumentException.class, () -> new ToolRequest(Tool.PROPOSE_REASSIGNMENT, b1, Optional.empty()));
        assertThrows(NullPointerException.class, () -> new ToolRequest(null, b1, Optional.empty()));
    }
}
