import java.util.ArrayList;
import java.util.List;

/** Append-only record of commits. At most one commit per proposal; callers get an unmodifiable copy. */
public final class CommitLog {
    private final List<CommitRecord> commits = new ArrayList<>();

    public void add(CommitRecord c) {
        if (c == null) throw new IllegalArgumentException("commit required");
        if (contains(c.proposalId())) throw new IllegalStateException("proposal already committed");
        commits.add(c);
    }
    public boolean contains(Identifier proposalId) {
        for (CommitRecord c : commits) if (c.proposalId().equals(proposalId)) return true;
        return false;
    }
    public int count() { return commits.size(); }
    public List<CommitRecord> all() { return List.copyOf(commits); }
}
