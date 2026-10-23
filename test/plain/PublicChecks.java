import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Public examples of required behaviour. Add independent tests of your own. */
public final class PublicChecks {
    @FunctionalInterface public interface Action { void run() throws Exception; }
    public record Case(String name, Action action) {}
    public static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + "; got " + actual);
    }
    public static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void rejects(Class<? extends Throwable> type, Action action) throws Exception {
        try { action.run(); } catch (Throwable ex) {
            if (type.isInstance(ex)) return;
            throw new AssertionError("Expected " + type.getSimpleName() + "; got " + ex, ex);
        }
        throw new AssertionError("Expected " + type.getSimpleName() + "; operation returned normally");
    }
    public static List<Case> cases() {
        List<Case> cases = new ArrayList<>();

cases.add(new Case("duration endpoints", () -> { DomainRules.requireDuration(1);DomainRules.requireDuration(24); }));
for(int bad:new int[]{0,25,-1}) cases.add(new Case("invalid duration "+bad, () -> rejects(IllegalArgumentException.class, () -> DomainRules.requireDuration(bad))));
for(String id:new String[]{null,"","b1","1B","ABCDEFGHIJKLMNOPQ"}) cases.add(new Case("invalid identifier "+id, () -> rejects(IllegalArgumentException.class, () -> DomainRules.requireIdentifier(id))));
cases.add(new Case("valid identifiers", () -> {DomainRules.requireIdentifier("B1");DomainRules.requireIdentifier("A_1-2");DomainRules.requireIdentifier("ABCDEFGHIJKLMNOP");}));
cases.add(new Case("D1-C pending B12, no booking change", () -> {StudentApplication a=new StudentApplication();var before=a.bookingSnapshot();ProposalView p=a.propose("B12");equal("PENDING",p.status());equal("B1",p.bookingId());equal("B12",p.targetId());equal(0,p.bookingVersion());equal(1,p.policyVersion());require(p.proposalId().matches("[A-Z][A-Z0-9_-]{0,15}"),"application-generated valid proposal ID required");equal(before,a.bookingSnapshot());equal("A17",a.bookingSnapshot().get("spaceId"));equal(0,a.bookingSnapshot().get("version"));}));
cases.add(new Case("D1-D occupied D09 rejected unchanged", () -> {StudentApplication a=new StudentApplication();var before=a.bookingSnapshot();rejects(IllegalArgumentException.class, () -> a.propose("D09"));equal(before,a.bookingSnapshot());}));
cases.add(new Case("D1-E unknown target rejected unchanged", () -> {StudentApplication a=new StudentApplication();var before=a.bookingSnapshot();rejects(IllegalArgumentException.class, () -> a.propose("Z99"));equal(before,a.bookingSnapshot());}));
cases.add(new Case("D1-F protected snapshot", () -> {StudentApplication a=new StudentApplication();var snapshot=a.bookingSnapshot();try {snapshot.put("spaceId","D09");}catch(UnsupportedOperationException expected){}equal("A17",a.bookingSnapshot().get("spaceId"));}));

        return cases;
    }
    public static void main(String[] args) {
        int failed = 0;
        for (Case c : cases()) {
            try { c.action().run(); System.out.println("PASS " + c.name()); }
            catch (Throwable ex) { failed++; System.out.println("FAIL " + c.name() + ": " + ex); }
        }
        System.out.println("CHECKS " + cases().size() + "; FAILED " + failed);
        if (failed != 0) System.exit(1);
    }
}
