import java.util.ArrayList;
import java.util.List;

/**
 * Append-only record of commits. At most one commit per proposal; callers get an unmodifiable copy.
 * contains() is D3.4 contracted operation 2 (loop invariant and variant); it is the search behind the
 * DUPLICATE outcome (D3-F). Contracts are JML in comments; see CONTRACTS.md for the manual argument.
 * Assumptions: Identifier.equals is pure, deterministic and null-safe (it is: a final String comparison),
 * and CommitRecord is an immutable record whose proposalId() is never null (its constructor checks this).
 */
public final class CommitLog {
    private final /*@ spec_public @*/ List<CommitRecord> commits = new ArrayList<>();

    //@ public invariant commits != null;
    //@ public invariant (\forall int j; 0 <= j && j < commits.size(); commits.get(j) != null);
    /*@ public invariant (\forall int a, b; 0 <= a && a < b && b < commits.size();
      @                       !commits.get(a).proposalId().equals(commits.get(b).proposalId()));
      @*/

    public void add(CommitRecord c) {
        if (c == null) throw new IllegalArgumentException("commit required");
        if (contains(c.proposalId())) throw new IllegalStateException("proposal already committed");
        commits.add(c);
    }
    /*@ public normal_behavior
      @   requires proposalId != null;
      @   assignable \nothing;
      @   ensures \result <==> (\exists int j; 0 <= j && j < commits.size();
      @                             commits.get(j).proposalId().equals(proposalId));
      @*/
    public /*@ pure @*/ boolean contains(Identifier proposalId) {
        int i = 0;
        /*@ loop_invariant 0 <= i && i <= commits.size();
          @ loop_invariant (\forall int j; 0 <= j && j < i; !commits.get(j).proposalId().equals(proposalId));
          @ loop_assigns i;
          @ decreases commits.size() - i;
          @*/
        while (i < commits.size()) {
            if (commits.get(i).proposalId().equals(proposalId)) return true;
            i++;
        }
        return false;
    }
    public int count() { return commits.size(); }
    public List<CommitRecord> all() { return List.copyOf(commits); }
}
