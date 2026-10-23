/** Whole hours from 1 through 24 inclusive. Immutable; an existing instance is always valid. */
public final class Duration {
    private final int hours;

    public static boolean isValid(int hours) { return hours >= 1 && hours <= 24; }

    public Duration(int hours) {
        if (!isValid(hours)) throw new IllegalArgumentException("Duration must be 1..24 hours");
        this.hours = hours;
    }

    public int getHours() { return hours; }

    @Override public boolean equals(Object o) { return o instanceof Duration other && hours == other.hours; }
    @Override public int hashCode() { return Integer.hashCode(hours); }
    @Override public String toString() { return hours + "h"; }
}
