/** A simulated notification. Nothing is sent: no email, SMS or external service is involved. */
public record Notification(Identifier proposalId, Identifier bookingId, String message) {
    public Notification {
        if (proposalId == null || bookingId == null || message == null) throw new IllegalArgumentException("fields required");
    }
}
