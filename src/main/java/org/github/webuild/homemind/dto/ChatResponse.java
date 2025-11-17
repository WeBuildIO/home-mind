package org.github.webuild.homemind.dto;

public class ChatResponse {
    private String response;
    private String conversationId;
    private long timestamp;

    public ChatResponse(String response, String conversationId) {
        this.response = response;
        this.conversationId = conversationId;
        this.timestamp = System.currentTimeMillis();
    }

    public String getResponse() {
        return response;
    }

    public String getConversationId() {
        return conversationId;
    }

    public long getTimestamp() {
        return timestamp;
    }
}