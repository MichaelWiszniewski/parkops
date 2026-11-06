import java.util.List;

/** Outcome of one tick. Only SCANNED can carry work items. */
public record TickResult(Status status, long time, List<WorkItem> workItems) {
    public enum Status { SCANNED, NOT_DUE, CLOCK_REJECTED }

    public TickResult {
        if (status == null || workItems == null) throw new IllegalArgumentException("fields required");
        if (status != Status.SCANNED && !workItems.isEmpty()) throw new IllegalArgumentException("only a scan emits work");
        workItems = List.copyOf(workItems);
    }
}
