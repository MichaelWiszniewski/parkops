import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Deterministic contract tests for the real-model route. The supplied GeminiProvider is exercised
 * unchanged against a local fake endpoint on 127.0.0.1 (its documented loopback seam). This proves the
 * connection code and the application's handling of its replies without network access or a key.
 * It is NOT a live model run.
 */
class GeminiProviderContractTests {
    private HttpServer server;
    private final List<String> requestBodies = Collections.synchronizedList(new ArrayList<>());
    private final List<String> authHeaders = Collections.synchronizedList(new ArrayList<>());

    /** Each reply is {status, assistantContent}; served in order. */
    private URI start(List<Object[]> replies, long delayMillis) throws IOException {
        AtomicInteger n = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/openai/chat/completions", ex -> {
            requestBodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authHeaders.add(ex.getRequestHeaders().getFirst("Authorization"));
            try { Thread.sleep(delayMillis); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            Object[] reply = replies.get(Math.min(n.getAndIncrement(), replies.size() - 1));
            byte[] body = envelope((String) reply[1]).getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders((Integer) reply[0], body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta/openai/chat/completions");
    }
    @AfterEach void stop() { if (server != null) server.stop(0); }

    private static String envelope(String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");
        return MiniJson.stringify(Map.of("choices", List.of(choice)));
    }
    private static Object[] ok(String content) { return new Object[]{200, content}; }
    private static String req(String tool, String target) {
        return "{\"tool\":\"" + tool + "\",\"bookingId\":\"B1\",\"targetId\":\"" + target + "\"}";
    }
    private GeminiProvider provider(URI endpoint, Duration timeout) {
        return new GeminiProvider(endpoint, "test-key-not-real", "gemini-test-model", timeout);
    }

    @Test @DisplayName("Through the real GeminiProvider: read, list, propose C03 -> one PENDING proposal, no move")
    void fullInvestigationOverHttp() throws IOException {
        URI uri = start(List.of(ok(req("READ_BOOKING", "")), ok(req("LIST_ALTERNATIVES", "")),
                ok(req("PROPOSE_REASSIGNMENT", "C03"))), 0);
        StudentApplication app = new StudentApplication();
        RunResult r = app.runAgent(provider(uri, Duration.ofSeconds(5)), 6);

        assertEquals(RunStatus.AWAITING_APPROVAL, r.status());
        assertEquals(3, requestBodies.size(), "exactly one HTTP request per model call");
        assertEquals(ProposalStatus.PENDING, app.proposalStatus("PROP_1").orElseThrow());
        assertEquals("A17", app.bookingSnapshot().get("spaceId"));
        assertEquals(0, app.commitCount());
        // Successive observations really travel to the model: request 2 carries the READ observation.
        assertTrue(requestBodies.get(1).contains("\\\"version\\\":0"), requestBodies.get(1));
        assertTrue(requestBodies.get(2).contains("candidates"), requestBodies.get(2));
        assertTrue(requestBodies.get(0).contains("\"model\":\"gemini-test-model\""));
        assertEquals("Bearer test-key-not-real", authHeaders.get(0));
    }

    @Test @DisplayName("HTTP 429 (quota) becomes PROVIDER_ERROR with no retry and no proposal")
    void quotaIsProviderError() throws IOException {
        URI uri = start(List.<Object[]>of(new Object[]{429, "quota"}), 0);
        StudentApplication app = new StudentApplication();
        RunResult r = app.runAgent(provider(uri, Duration.ofSeconds(5)), 6);
        assertEquals(RunStatus.PROVIDER_ERROR, r.status());
        assertEquals(1, requestBodies.size(), "no automatic retry");
        assertTrue(app.proposalStatus("PROP_1").isEmpty());
    }

    @Test @DisplayName("A timeout becomes PROVIDER_ERROR and commits nothing")
    void timeoutIsProviderError() throws IOException {
        URI uri = start(List.<Object[]>of(ok(req("READ_BOOKING", ""))), 1500);
        StudentApplication app = new StudentApplication();
        RunResult r = app.runAgent(provider(uri, Duration.ofMillis(300)), 6);
        assertEquals(RunStatus.PROVIDER_ERROR, r.status());
        assertEquals(0, app.commitCount());
    }

    @Test @DisplayName("A model reply wrapped in a code fence is an invalid response: counted, then recovered")
    void fencedReplyCounted() throws IOException {
        URI uri = start(List.of(ok("```json\n" + req("READ_BOOKING", "") + "\n```"), ok(req("READ_BOOKING", "")),
                ok(req("LIST_ALTERNATIVES", "")), ok(req("PROPOSE_REASSIGNMENT", "B12"))), 0);
        RunResult r = new StudentApplication().runAgent(provider(uri, Duration.ofSeconds(5)), 6);
        assertEquals(RunStatus.AWAITING_APPROVAL, r.status());
        assertEquals(1, r.invalidResponses());
        assertEquals(4, r.calls());
    }

    @Test @DisplayName("A model claiming approval gets nothing: rejected, counted, no proposal approved")
    void modelCannotApprove() throws IOException {
        String sneaky = "{\"tool\":\"PROPOSE_REASSIGNMENT\",\"bookingId\":\"B1\",\"targetId\":\"B12\",\"approved\":true}";
        URI uri = start(List.of(ok(req("READ_BOOKING", "")), ok(req("LIST_ALTERNATIVES", "")), ok(sneaky)), 0);
        StudentApplication app = new StudentApplication();
        RunResult r = app.runAgent(provider(uri, Duration.ofSeconds(5)), 3);
        assertEquals(RunStatus.STEP_LIMIT, r.status());
        assertEquals(1, r.invalidResponses());
        assertTrue(app.proposalStatus("PROP_1").isEmpty());
    }

    @Test @DisplayName("The provider refuses non-loopback, non-Google endpoints and blank keys")
    void providerGuards() {
        assertThrows(IllegalArgumentException.class,
                () -> new GeminiProvider(URI.create("https://example.com/x"), "k", "gemini-x", Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class, () -> new GeminiProvider(" ", "gemini-x", Duration.ofSeconds(5)));
    }
}
