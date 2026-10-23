import java.util.List;
import java.util.Map;
/** Synthetic input data, not the assessed domain implementation. Values are immutable. */
public final class Fixture {
    private Fixture() {}
    public static Map<String,Object> booking() {
        return Map.of("id","B1","spaceId","A17","durationHours",3,"version",0,"accessibilityRequired",true);
    }
    public static List<Map<String,Object>> spaces() {
        return List.of(space("A17",false,true,false),space("B12",true,false,false),
                       space("C03",true,false,true),space("D09",true,true,true));
    }
    private static Map<String,Object> space(String id,boolean open,boolean occupied,boolean accessible) {
        return Map.of("id",id,"open",open,"occupied",occupied,"accessible",accessible);
    }
    public static String proposalRequest(String target) {
        return MiniJson.stringify(Map.of("tool","PROPOSE_REASSIGNMENT","bookingId","B1","targetId",target));
    }
    public static ReplayProvider replay() {
        return new ReplayProvider(List.of(
            "{\"tool\":\"READ_BOOKING\",\"bookingId\":\"B1\",\"targetId\":\"\"}",
            "{\"tool\":\"LIST_ALTERNATIVES\",\"bookingId\":\"B1\",\"targetId\":\"\"}",
            proposalRequest("B12")));
    }
}
