import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D3.3 dependency review, kept as regression tests. The compiler now stops RegistryView holders from calling
 * mutators; these tests stop the narrowing from being undone, and cover the part the compiler cannot check
 * here (everything is in the default package, so ParkingRegistry's mutators must stay public).
 * Run from the project root, as bash run.sh test does.
 */
class RegistryBoundaryTests {
    private static final Set<String> MUTATORS = Set.of("moveBooking", "closeSpace", "occupySpace");

    @Test @DisplayName("RegistryView exposes exactly the four read methods and no mutator")
    void viewIsReadOnly() {
        Set<String> names = new TreeSet<>();
        for (Method m : RegistryView.class.getDeclaredMethods()) names.add(m.getName());
        assertEquals(new TreeSet<>(Set.of("findSpace", "findBooking", "allBookings", "allSpaces")), names);
        assertTrue(RegistryView.class.isAssignableFrom(ParkingRegistry.class));
    }

    @Test @DisplayName("InvestigationSession and PeriodicDetector hold and accept only RegistryView, never ParkingRegistry")
    void readOnlyCollaborators() {
        for (Class<?> c : List.of(InvestigationSession.class, PeriodicDetector.class)) {
            for (Field f : c.getDeclaredFields())
                assertNotEquals(ParkingRegistry.class, f.getType(), c.getSimpleName() + "." + f.getName());
            for (Constructor<?> k : c.getConstructors())
                assertTrue(List.of(k.getParameterTypes()).contains(RegistryView.class)
                        && !List.of(k.getParameterTypes()).contains(ParkingRegistry.class), c.getSimpleName());
        }
    }

    @Test @DisplayName("no src/ class except ProposalManager calls moveBooking; only ParkingRegistry defines the mutators")
    void onlyProposalManagerCommits() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("src"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String name = p.getFileName().toString();
                if (name.equals("ParkingRegistry.java")) continue;
                String code = withoutComments(Files.readString(p, StandardCharsets.UTF_8));
                for (String m : MUTATORS) {
                    int calls = count(code, m + "(");
                    int allowed = name.equals("ProposalManager.java") && m.equals("moveBooking") ? 1 : 0;
                    assertEquals(allowed, calls, name + " calls " + m);
                }
            }
        }
    }

    private static String withoutComments(String code) {
        return code.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    private static int count(String s, String token) {
        int n = 0;
        for (int i = s.indexOf(token); i >= 0; i = s.indexOf(token, i + 1)) n++;
        return n;
    }
}
