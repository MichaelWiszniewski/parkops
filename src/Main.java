import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Command-line entry point. Modes:
 *   scripted        D2 replay workflow with a scripted operator (labelled; not a live model run)
 *   live            D2 workflow with Gemini and an interactive operator; also saved under results/
 *   periodic        periodic detection trace with controlled time
 *   agent-scripted  replay agent loop only, stopping at the pending proposal
 *   d1-scripted     the original D1 single-proposal replay
 *   fixture         prints the synthetic fixture
 */
public final class Main {
    private static final int CALL_LIMIT = 6;

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "fixture" : args[0];
        if (mode.equals("fixture")) {
            System.out.println("SYNTHETIC FIXTURE; no model invocation or booking mutation.");
            System.out.println(MiniJson.stringify(Fixture.booking()));
            System.out.println(MiniJson.stringify(Fixture.spaces()));
            return;
        }
        StudentApplication app = new StudentApplication();
        switch (mode) {
            case "scripted" -> {
                System.out.println("REPLAY D2 workflow: ReplayProvider is a test double, not a live model.");
                System.out.println("The operator decision below is scripted (APPROVE) for repeatability.");
                WorkflowDemo.run(app, Fixture.replay(), OperatorConsole.scripted(OperatorDecision.APPROVE),
                        CALL_LIMIT, System.out::println);
            }
            case "live" -> live(app);
            case "periodic" -> {
                System.out.println("PERIODIC detection with controlled time; no waiting, no model call.");
                System.out.println(app.periodicTrace());
            }
            case "agent-scripted" -> {
                System.out.println("REPLAY D2 agent loop only; not a live model run; no operator action.");
                System.out.println(app.run(Fixture.replay(), CALL_LIMIT));
            }
            case "d1-scripted" -> {
                System.out.println("REPLAY D1 input: not a live model run; approval/execution are not invoked.");
                ReplayProvider replay = new ReplayProvider(List.of(Fixture.proposalRequest("B12")));
                var input = MiniJson.object(MiniJson.parse(
                        replay.next(List.of(new ChatMessage("user", "Propose a replacement for B1")))));
                System.out.println(app.propose((String) input.get("targetId")));
                System.out.println("Booking: " + MiniJson.stringify(app.bookingSnapshot()));
            }
            default -> {
                System.err.println("Modes: scripted | live | periodic | agent-scripted | d1-scripted | fixture");
                System.exit(2);
            }
        }
    }

    /** Live Gemini run. The key is read privately by the supplied helper and is never printed or saved. */
    private static void live(StudentApplication app) throws IOException {
        String model = System.getenv("GEMINI_MODEL");
        if (model == null || model.isBlank()) model = GeminiProvider.DEFAULT_MODEL;
        ModelProvider provider;
        try {
            provider = GeminiProvider.fromEnvironment();
        } catch (IllegalArgumentException ex) {
            System.err.println("LIVE_SETUP_ERROR: " + ex.getMessage());
            System.exit(2);
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Toronto"));
        StringBuilder record = new StringBuilder();
        java.util.function.Consumer<String> out = line -> { System.out.println(line); record.append(line).append('\n'); };

        out.accept("LIVE Gemini run (genuine model calls; not replay)");
        out.accept("Date: " + now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        out.accept("Command: bash run.sh run live");
        out.accept("Model: " + model);
        out.accept("Environment: Java " + System.getProperty("java.version") + ", " + System.getProperty("os.name")
                + " " + System.getProperty("os.arch"));
        out.accept("Operator: interactive terminal prompt (separate human action)");
        WorkflowDemo.run(app, provider, new TerminalOperator(), CALL_LIMIT, out);

        Path dir = Path.of("results");
        Files.createDirectories(dir);
        Path file = dir.resolve("live-run-" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
        Files.writeString(file, record, StandardCharsets.UTF_8);
        System.out.println("Saved transcript (no key included): " + file);
    }
}
