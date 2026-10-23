import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
/** Deterministic test double. It is not an AI model. One instance is one conversation. */
public final class ReplayProvider implements ModelProvider {
    private final List<String> responses;
    private final List<List<ChatMessage>> histories=new ArrayList<>();
    private int calls;
    public ReplayProvider(List<String> responses) { this.responses=List.copyOf(responses); }
    public String next(List<ChatMessage> history) throws IOException {
        histories.add(List.copyOf(history));
        int index=calls++;
        if(index>=responses.size()) throw new IOException("Replay exhausted after "+responses.size()+" responses");
        return responses.get(index);
    }
    public int calls() { return calls; }
    public List<List<ChatMessage>> histories() { return List.copyOf(histories); }
}
