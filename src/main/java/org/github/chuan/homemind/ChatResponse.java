package org.github.chuan.homemind;

// ChatResponse.java
public class ChatResponse {
    private String response;
    private String conversationId;
    private long timestamp;

    public ChatResponse(String response, String conversationId) {
        this.response = response;
        this.conversationId = conversationId;
        this.timestamp = System.currentTimeMillis();
    }

    // Getter
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