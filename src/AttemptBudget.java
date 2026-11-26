/**
 * Bounded number of model calls for one investigation (D3.4 contracted operation 1, case D3-J).
 * Extracted from AgentRunner.run(), which used to count calls in a local int. Every call is consumed
 * before it is made, including calls whose reply turns out to be invalid.
 *
 * Contracts are JML in comments, so they compile as ordinary Java. No OpenJML environment was supplied by
 * the course, so they are checked by the manual argument in CONTRACTS.md and by AttemptBudgetTests, not
 * by a verifier. They describe this class only, not the model or the whole workflow.
 */
public final class AttemptBudget {
    /** Same bounds as AgentRunner.MIN_CALLS and AgentRunner.MAX_CALLS. */
    public static final int MIN_LIMIT = 1, MAX_LIMIT = 6;

    private /*@ spec_public @*/ final int limit;
    private /*@ spec_public @*/ int used;

    //@ public invariant MIN_LIMIT <= limit && limit <= MAX_LIMIT;
    //@ public invariant 0 <= used && used <= limit;

    /*@ public normal_behavior
      @   requires MIN_LIMIT <= limit && limit <= MAX_LIMIT;
      @   assignable \nothing;          // fields of the new object are not part of the frame
      @   ensures this.limit == limit && this.used == 0;
      @ also public exceptional_behavior
      @   requires limit < MIN_LIMIT || limit > MAX_LIMIT;
      @   assignable \nothing;
      @   signals_only IllegalArgumentException;
      @*/
    public AttemptBudget(int limit) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT)
            throw new IllegalArgumentException("call limit must be " + MIN_LIMIT + ".." + MAX_LIMIT + ", got " + limit);
        this.limit = limit;
        this.used = 0;
    }

    /*@ public normal_behavior
      @   requires used < limit;
      @   assignable used;
      @   ensures used == \old(used) + 1;
      @   ensures remaining() == \old(remaining()) - 1;
      @ also public exceptional_behavior
      @   requires used == limit;
      @   assignable \nothing;
      @   signals_only IllegalStateException;
      @   signals (IllegalStateException e) used == \old(used);
      @*/
    public void consume() {
        if (used == limit) throw new IllegalStateException("attempt budget exhausted (" + limit + " calls)");
        used++;
    }

    /*@ public normal_behavior
      @   ensures \result == limit - used;
      @   ensures \result >= 0;
      @*/
    public /*@ pure @*/ int remaining() { return limit - used; }

    /*@ public normal_behavior
      @   ensures \result == used;
      @*/
    public /*@ pure @*/ int used() { return used; }

    /*@ public normal_behavior
      @   ensures \result == limit;
      @*/
    public /*@ pure @*/ int limit() { return limit; }
}
