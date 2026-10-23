/** One immutable message in a provider conversation. No credentials belong here. */
public record ChatMessage(String role,String content) {
    public ChatMessage {
        if(role==null || content==null) throw new IllegalArgumentException("message fields required");
        if(!role.equals("system") && !role.equals("user") && !role.equals("assistant"))
            throw new IllegalArgumentException("supported roles: system, user, assistant");
    }
}
