import java.util.List;
/** Returns untrusted response text. Java application code must validate it before dispatch. */
@FunctionalInterface
public interface ModelProvider {
    String next(List<ChatMessage> history) throws Exception;
}
