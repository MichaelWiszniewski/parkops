import java.net.URI;
import java.time.Duration;
import java.util.List;
public final class Main {
    public static void main(String[] args) throws Exception {
        String mode=args.length==0?"fixture":args[0];
        if(mode.equals("fixture")) {
            System.out.println("SYNTHETIC FIXTURE; no model invocation or booking mutation.");
            System.out.println(MiniJson.stringify(Fixture.booking()));
            System.out.println(MiniJson.stringify(Fixture.spaces()));
            return;
        }
        StudentApplication app=new StudentApplication();
        try {
            if(mode.equals("scripted")) {
                System.out.println("REPLAY D1 input: not a live model run; approval/execution are not invoked.");
                ReplayProvider replay=new ReplayProvider(List.of(Fixture.proposalRequest("B12")));
                var input=MiniJson.object(MiniJson.parse(replay.next(List.of(new ChatMessage("user","Propose a replacement for B1")))));
                ProposalView p=app.propose((String)input.get("targetId"));
                System.out.println(p);
                System.out.println("Booking: "+MiniJson.stringify(app.bookingSnapshot()));
            } else if(mode.equals("agent-scripted")) {
                System.out.println("REPLAY D2 workflow; not a live model run.");
                System.out.println(app.run(Fixture.replay(),6));
            } else if(mode.equals("live")) {
                String host=System.getenv("OLLAMA_CHAT_URL"),model=System.getenv("OLLAMA_MODEL");
                if(host==null || model==null || host.isBlank() || model.isBlank()) {
                    System.out.println("LIVE_RUN_PENDING_COURSE_ACCESS: configure the course-provided OLLAMA_CHAT_URL and OLLAMA_MODEL.");
                    System.exit(2);
                }
                System.out.println("LIVE provider selected; outcome must be recorded from actual execution.");
                System.out.println(app.run(new OllamaProvider(URI.create(host),model,Duration.ofSeconds(30)),6));
            } else {
                System.err.println("Modes: fixture | scripted (D1) | agent-scripted (D2) | live (D2)");System.exit(2);
            }
        } catch(UnsupportedOperationException ex) {
            System.err.println("STARTER_NOT_IMPLEMENTED: "+ex.getMessage());System.exit(1);
        }
    }
}
