/** Immutable booking snapshot. */
public final class Booking {
    private final Identifier id;
    private final Identifier spaceId;
    private final Duration duration;
    private final int version;
    private final boolean accessibilityRequired;

    public Booking(Identifier id, Identifier spaceId, Duration duration, int version, boolean accessibilityRequired) {
        if (id == null || spaceId == null || duration == null) throw new IllegalArgumentException("booking fields required");
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        this.id = id; this.spaceId = spaceId; this.duration = duration;
        this.version = version; this.accessibilityRequired = accessibilityRequired;
    }

    public Identifier getId() { return id; }
    public Identifier getSpaceId() { return spaceId; }
    public Duration getDuration() { return duration; }
    public int getVersion() { return version; }
    public boolean isAccessibilityRequired() { return accessibilityRequired; }
}
