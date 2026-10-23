/** Immutable parking space snapshot. "Available" means open and not occupied. */
public final class Space {
    private final Identifier id;
    private final boolean occupied;
    private final boolean open;
    private final boolean accessible;

    /** Parameter order follows the project's space table: open, occupied, accessible. */
    public Space(Identifier id, boolean open, boolean occupied, boolean accessible) {
        if (id == null) throw new IllegalArgumentException("space id required");
        this.id = id; this.open = open; this.occupied = occupied; this.accessible = accessible;
    }

    public Identifier getId() { return id; }
    public boolean isOccupied() { return occupied; }
    public boolean isOpen() { return open; }
    public boolean isAccessible() { return accessible; }
}
