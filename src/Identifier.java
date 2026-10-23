import java.util.regex.Pattern;

/** Validated identifier value object: [A-Z][A-Z0-9_-]{0,15}. Immutable; an existing instance is always valid. */
public final class Identifier {
    private static final Pattern FORMAT = Pattern.compile("[A-Z][A-Z0-9_-]{0,15}");
    private final String value;

    /** Non-throwing well-formedness check, used at the request boundary before construction. */
    public static boolean isValid(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }

    public Identifier(String value) {
        if (!isValid(value))
            throw new IllegalArgumentException("Invalid identifier: must match [A-Z][A-Z0-9_-]{0,15}");
        this.value = value;
    }

    public String getValue() { return value; }

    @Override public boolean equals(Object o) { return o instanceof Identifier other && value.equals(other.value); }
    @Override public int hashCode() { return value.hashCode(); }
    @Override public String toString() { return value; }
}
