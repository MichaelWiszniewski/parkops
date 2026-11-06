/** Result of checking raw model text: either a validated request or a controlled rejection. */
public sealed interface ParseOutcome permits ParseOutcome.Parsed, ParseOutcome.Rejected {
    record Parsed(ToolRequest request) implements ParseOutcome {
        public Parsed { if (request == null) throw new IllegalArgumentException("request required"); }
    }
    record Rejected(ProtocolError error, String message) implements ParseOutcome {
        public Rejected { if (error == null || message == null) throw new IllegalArgumentException("error and message required"); }
    }
}
