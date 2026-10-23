/** Predictable application-generated proposal ids: PROP_1, PROP_2, ... */
public final class SequentialIdGenerator implements IdGenerator {
    private int counter = 0;
    @Override public Identifier generate() { counter++; return new Identifier("PROP_" + counter); }
}
