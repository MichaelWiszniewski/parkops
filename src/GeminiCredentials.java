import java.io.Console;
import java.util.Arrays;

/** Reads a private key without writing it to a source file, command history or transcript. */
public final class GeminiCredentials {
    private GeminiCredentials() {}
    public static String readKey() {
        String configured = System.getenv("GEMINI_API_KEY");
        if (configured != null && !configured.isBlank()) return configured;
        Console terminal = System.console();
        if (terminal == null) throw new IllegalArgumentException(
            "Run directly in a terminal, without piping or redirecting input/output, so the key can be entered privately.");
        char[] entered = terminal.readPassword("Paste your Gemini API key, then press Enter (nothing is displayed): ");
        if (entered == null) throw new IllegalArgumentException("No key entered; no request made");
        try {
            String key = new String(entered).strip();
            if (key.isBlank()) throw new IllegalArgumentException("No key entered; no request made");
            return key;
        } finally { Arrays.fill(entered, '\0'); }
    }
}
