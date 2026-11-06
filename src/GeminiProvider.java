import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Gemini transport only. Domain validation and tool execution remain in the application. */
public final class GeminiProvider implements ModelProvider {
    public static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";

    /** Creates a connection using a private key prompt or the current process environment. */
    public static GeminiProvider fromEnvironment() {
        String selected = System.getenv("GEMINI_MODEL");
        if (selected == null || selected.isBlank()) selected = DEFAULT_MODEL;
        return new GeminiProvider(GeminiCredentials.readKey(), selected, Duration.ofSeconds(30));
    }

    private static final URI GOOGLE = URI.create(
        "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions");
    private static final int MAX_BYTES = 1_048_576;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

    public GeminiProvider(String apiKey, String model, Duration timeout) {
        this(GOOGLE, apiKey, model, timeout);
    }

    // Package-private transport fixture seam. Non-Google targets must be literal loopback.
    GeminiProvider(URI endpoint, String apiKey, String model, Duration timeout) {
        boolean loopback = endpoint != null && "http".equals(endpoint.getScheme())
            && "127.0.0.1".equals(endpoint.getHost());
        if (endpoint == null || (!GOOGLE.equals(endpoint) && !loopback)
            || endpoint.getUserInfo() != null || endpoint.getQuery() != null
            || endpoint.getFragment() != null) throw new IllegalArgumentException("Unsupported model endpoint");
        if (apiKey == null || apiKey.isBlank() || apiKey.chars().anyMatch(c -> c <= 32 || c > 126))
            throw new IllegalArgumentException("A private Gemini API key is required");
        if (model == null || !model.matches("gemini-[A-Za-z0-9._-]{1,100}"))
            throw new IllegalArgumentException("A Gemini model identifier is required");
        if (timeout == null || timeout.isNegative() || timeout.isZero()
            || timeout.compareTo(Duration.ofSeconds(120)) > 0)
            throw new IllegalArgumentException("Timeout must be positive and at most 120 seconds");
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
    }

    @Override public String next(List<ChatMessage> history) throws IOException, InterruptedException {
        if (history == null || history.isEmpty()) throw new IllegalArgumentException("Conversation required");
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage message : List.copyOf(history))
            messages.add(Map.of("role", message.role(), "content", message.content()));
        byte[] payload = MiniJson.stringify(Map.of("model", model, "messages", messages,
            "stream", false)).getBytes(StandardCharsets.UTF_8);
        if (payload.length > MAX_BYTES) throw new IllegalArgumentException("Conversation too large");
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofByteArray(payload)).build();
        HttpResponse<byte[]> response;
        try {
            response = client.send(request, info -> new LimitedBody());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        }
        // Never log the service error body, authentication headers or key.
        if (response.statusCode() != 200)
            throw new IOException("Gemini HTTP " + response.statusCode() + "; no automatic retry performed");
        try {
            Map<String, Object> body = MiniJson.object(MiniJson.parse(
                new String(response.body(), StandardCharsets.UTF_8)));
            if (!(body.get("choices") instanceof List<?> choices) || choices.size() != 1)
                throw new IllegalArgumentException("Exactly one candidate required");
            Map<String, Object> choice = MiniJson.object(choices.get(0));
            if (!"stop".equals(choice.get("finish_reason")))
                throw new IllegalArgumentException("Incomplete or refused model response");
            Map<String, Object> message = MiniJson.object(choice.get("message"));
            if (!(message.get("content") instanceof String text) || text.isBlank())
                throw new IllegalArgumentException("Text response required");
            return text;
        } catch (IllegalArgumentException ex) {
            throw new IOException("Gemini returned an unusable response envelope");
        }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> completed = new CompletableFuture<>();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return completed; }
        @Override public void onSubscribe(Flow.Subscription incoming) {
            subscription=incoming;
            incoming.request(Long.MAX_VALUE);
        }
        @Override public void onNext(List<ByteBuffer> chunks) {
            if(completed.isDone()) return;
            for(ByteBuffer chunk:chunks) {
                if(chunk.remaining()>MAX_BYTES-bytes.size()) {
                    subscription.cancel();
                    completed.completeExceptionally(new IOException("Gemini response exceeds size limit"));
                    return;
                }
                byte[] part=new byte[chunk.remaining()];
                chunk.get(part);
                bytes.writeBytes(part);
            }
        }
        @Override public void onError(Throwable failure) { completed.completeExceptionally(failure); }
        @Override public void onComplete() { completed.complete(bytes.toByteArray()); }
    }
}
