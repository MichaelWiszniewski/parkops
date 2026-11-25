import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The d3-scripted printout tells the truth about the outcomes it shows. The behaviour itself is asserted in D3ChangeCaseTests. */
class D3DemoTests {
    private final List<String> lines = new ArrayList<>();

    private String all() {
        if (lines.isEmpty()) D3Demo.run(lines::add);
        return String.join("\n", lines);
    }

    private void assertLine(String expected) {
        assertTrue(all().contains(expected), "missing line: " + expected + "\n" + all());
    }

    @Test @DisplayName("part 1 shows the v1 candidates B12 and C03")
    void baseline() {
        assertLine("\"candidates\":[\"B12\",\"C03\"]");
        assertLine("\"policyVersion\":1");
    }

    @Test @DisplayName("part 2 shows STALE, the v2 work item, the rejected B12 and exactly one commit to C03")
    void change() {
        assertLine("[D3-C] execute(PROP_1) captured policy v1 -> STALE");
        assertLine("tick(60) -> SCANNED, work items: 1 [B1 on closed A17, booking v0, policy v2]");
        assertLine("tick(120) -> SCANNED, work items: 0");
        assertLine("\"candidates\":[\"C03\"]");
        assertLine("\"error\":\"NOT_ACCESSIBLE\"");
        assertLine("[D3-E] execute(PROP_2) -> COMMITTED");
        assertLine("[D3-F] execute(PROP_2) again -> DUPLICATE");
        assertLine("STATE booking B1 at C03 version 1; commits 1; simulated notifications 1");
    }

    @Test @DisplayName("part 3 shows the non-requiring booking committing to B12")
    void nonRequiring() {
        assertLine("\"accessibilityRequired\":false");
        assertLine("STATE booking B1 at B12 version 1; commits 1; simulated notifications 1");
    }

    @Test @DisplayName("the demo is deterministic: two runs print identical output")
    void deterministic() {
        List<String> second = new ArrayList<>();
        D3Demo.run(second::add);
        assertEquals(all(), String.join("\n", second));
    }

    @Test @DisplayName("no line claims a live model run")
    void neverClaimsLive() {
        for (String l : lines.isEmpty() ? List.of(all().split("\n")) : lines)
            assertFalse(l.toUpperCase().contains("LIVE"), l);
    }
}
