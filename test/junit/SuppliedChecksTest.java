import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
public class SuppliedChecksTest {
    @TestFactory Stream<DynamicTest> requirements() {
        return PublicChecks.cases().stream().map(c -> DynamicTest.dynamicTest(c.name(), () -> c.action().run()));
    }
}
