/**
 * Starter boundary kept for the public checks. The rules themselves live in one place:
 * Duration.isValid and Identifier.isValid. This class only reports violations as IllegalArgumentException.
 */
public final class DomainRules {
    private DomainRules() {}
    public static void requireDuration(int hours) {
        if (!Duration.isValid(hours)) throw new IllegalArgumentException("Duration must be 1..24 hours");
    }
    public static void requireIdentifier(String id) {
        if (!Identifier.isValid(id)) throw new IllegalArgumentException("Invalid identifier: must match [A-Z][A-Z0-9_-]{0,15}");
    }
}
