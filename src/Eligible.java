public final class Eligible implements PolicyVerdict {
    private final String message;
    public Eligible(String message) { this.message = message; }
    public String getMessage() { return message; }
}
