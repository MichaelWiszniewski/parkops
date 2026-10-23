public interface ReassignmentPolicy {
    PolicyVerdict evaluate(Booking booking, Space target);
    int getVersion();
}
