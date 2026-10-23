public final class Ineligible implements PolicyVerdict {
    private final RefusalReason reason;
    public Ineligible(RefusalReason reason) {
        if (reason == null) throw new IllegalArgumentException("reason required");
        this.reason = reason;
    }
    public RefusalReason getReason() { return reason; }
}
