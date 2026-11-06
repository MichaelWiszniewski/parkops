import java.util.ArrayList;
import java.util.List;

/** Append-only record of simulated notifications; callers get an unmodifiable copy. */
public final class NotificationLog {
    private final List<Notification> sent = new ArrayList<>();

    public void add(Notification n) {
        if (n == null) throw new IllegalArgumentException("notification required");
        sent.add(n);
    }
    public int count() { return sent.size(); }
    public List<Notification> all() { return List.copyOf(sent); }
}
