import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;

/** Tests supplied infrastructure using a local fake HTTP server; no real model is called. */
public final class InfrastructureChecks {
    private static int checks;
    private static void yes(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void bad(String input){checks++;try{MiniJson.parse(input);}catch(IllegalArgumentException expected){return;}throw new AssertionError("accepted malformed JSON: "+input);}
    public static void main(String[] args) throws Exception {
        String tricky="quote\" slash\\ newline\n tab\t Unicode \u03bb";
        yes(MiniJson.parse(MiniJson.stringify(tricky)).equals(tricky),"string round-trip");
        var parsed=MiniJson.object(MiniJson.parse("{\"a\":[1,true,null,\"x\"],\"b\":-1.25e2}"));
        yes(((List<?>)parsed.get("a")).size()==4,"nested array");
        for(String s:new String[]{"", "{\"x\":1,\"x\":2}","{\"x\":null,\"x\":2}","[1,]","{\"a\":1,}","01","+1",".5","1.","NaN","true false","\"bad\n\"","\"\\x\"","\"\\uZZZZ\"","[","[".repeat(66)+"]".repeat(66)})bad(s);
        ReplayProvider replay=Fixture.replay();
        var history=new java.util.ArrayList<ChatMessage>();history.add(new ChatMessage("user","synthetic B1"));
        yes(replay.next(history).contains("READ_BOOKING"),"first replay");history.clear();
        yes(replay.histories().get(0).size()==1,"history detached");
        replay.next(List.of(new ChatMessage("user","listed")));replay.next(List.of(new ChatMessage("user","propose")));
        try{replay.next(List.of(new ChatMessage("user","extra")));throw new AssertionError("replay exhaustion");}catch(java.io.IOException expected){checks++;}
        yes(replay.calls()==4,"all invocations counted");
        AtomicReference<String> received=new AtomicReference<>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/chat",exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            String reply=MiniJson.stringify(Map.of("message",Map.of("content",Fixture.proposalRequest("C03"))));
            byte[] bytes=reply.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}
        });
        server.createContext("/error/api/chat",exchange -> {exchange.sendResponseHeaders(503,-1);exchange.close();});
        server.createContext("/malformed/api/chat",exchange -> {byte[] bytes="{\"message\":{\"content\":12}}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}});
        server.start();
        try {
            String host="http://127.0.0.1:"+server.getAddress().getPort();
            OllamaProvider p=new OllamaProvider(URI.create(host+"/api/chat"),"fixture-model",Duration.ofSeconds(2));
            String response=p.next(List.of(new ChatMessage("system","synthetic protocol"),new ChatMessage("user","tool observation")));
            yes(MiniJson.object(MiniJson.parse(response)).get("targetId").equals("C03"),"adapter response");
            var request=MiniJson.object(MiniJson.parse(received.get()));
            yes(Boolean.FALSE.equals(request.get("stream")),"nonstreaming request");yes(request.get("model").equals("fixture-model"),"model ID");
            yes(((List<?>)request.get("messages")).size()==2,"conversation forwarded");yes(request.get("format").equals("json"),"JSON format");
            for(String path:List.of("/error/api/chat","/malformed/api/chat")) {
                try{new OllamaProvider(URI.create(host+path),"fixture",Duration.ofSeconds(2)).next(List.of(new ChatMessage("user","synthetic")));throw new AssertionError("bad endpoint accepted");}
                catch(java.io.IOException expected){checks++;}
            }
        } finally {server.stop(0);}
        yes(Fixture.booking().get("spaceId").equals("A17"),"fixture location");
        yes(Fixture.spaces().size()==4,"four fixture spaces");
        System.out.println("INFRASTRUCTURE_CHECKS_PASS "+checks+"; localhost fake HTTP only; no live model or project solution verified.");
    }
}
