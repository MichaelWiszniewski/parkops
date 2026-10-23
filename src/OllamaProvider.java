import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Non-streaming Ollama /api/chat adapter. It never executes a proposed tool. */
public final class OllamaProvider implements ModelProvider {
    private final HttpClient client;
    private final URI endpoint;
    private final String model;
    private final Duration timeout;
    public OllamaProvider(URI endpoint,String model,Duration timeout) {
        if(endpoint==null || !("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme()))
           || endpoint.getHost()==null || endpoint.getUserInfo()!=null || endpoint.getFragment()!=null
           || endpoint.getQuery()!=null || !endpoint.getPath().endsWith("/api/chat"))
            throw new IllegalArgumentException("Use a credential-free course HTTP(S) /api/chat endpoint");
        if(model==null || model.isBlank())throw new IllegalArgumentException("model required");
        if(timeout==null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(120))>0)
            throw new IllegalArgumentException("timeout must be >0 and <=120 seconds");
        this.endpoint=endpoint;this.model=model;this.timeout=timeout;
        this.client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public String next(List<ChatMessage> history) throws IOException,InterruptedException {
        if(history==null || history.isEmpty())throw new IllegalArgumentException("nonempty conversation required");
        List<Map<String,Object>> messages=new ArrayList<>();
        for(ChatMessage m:List.copyOf(history))messages.add(Map.of("role",m.role(),"content",m.content()));
        Map<String,Object> body=new LinkedHashMap<>();body.put("model",model);body.put("messages",messages);body.put("stream",false);body.put("format","json");
        String payload=MiniJson.stringify(body);
        if(payload.length()>1_048_576)throw new IllegalArgumentException("conversation too large");
        HttpRequest request=HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload,StandardCharsets.UTF_8)).build();
        HttpResponse<String> response;
        try {response=client.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));}
        catch(InterruptedException ex){Thread.currentThread().interrupt();throw ex;}
        if(response.statusCode()!=200)throw new IOException("Model endpoint returned HTTP "+response.statusCode());
        try {
            Map<String,Object> envelope=MiniJson.object(MiniJson.parse(response.body()));
            Object content=MiniJson.object(envelope.get("message")).get("content");
            if(!(content instanceof String text))throw new IllegalArgumentException("message.content must be a string");
            return text;
        } catch(IllegalArgumentException ex) {throw new IOException("Invalid model response envelope",ex);}
    }
}
