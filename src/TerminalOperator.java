import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Asks the person at the terminal. End of input or an unreadable line means DEFER: nothing is committed. */
public final class TerminalOperator implements OperatorConsole {
    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    @Override public OperatorDecision decide(Proposal pending) {
        for (int attempt = 0; attempt < 3; attempt++) {
            System.out.print("OPERATOR: approve, reject or defer " + pending.getId().getValue()
                    + " (move " + pending.getBookingId().getValue() + " to " + pending.getTargetId().getValue()
                    + ")? [a/r/d]: ");
            System.out.flush();
            String line;
            try { line = in.readLine(); } catch (IOException ex) { return OperatorDecision.DEFER; }
            if (line == null) return OperatorDecision.DEFER;
            switch (line.strip().toLowerCase()) {
                case "a", "approve" -> { return OperatorDecision.APPROVE; }
                case "r", "reject" -> { return OperatorDecision.REJECT; }
                case "d", "defer" -> { return OperatorDecision.DEFER; }
                default -> System.out.println("Please type a, r or d.");
            }
        }
        return OperatorDecision.DEFER;
    }
}
