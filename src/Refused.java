/** The request was refused; no proposal exists. This type has no proposal field by design. */
public final class Refused implements Result {
    private final RefusalReason reason;
    private final String message;
    public Refused(RefusalReason reason, String message) {
        if (reason == null || message == null) throw new IllegalArgumentException("reason and message required");
        this.reason = reason; this.message = message;
    }
    public RefusalReason getReason() { return reason; }
    public String getMessage() { return message; }
}
