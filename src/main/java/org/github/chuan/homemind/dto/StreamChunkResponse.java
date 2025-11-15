package org.github.chuan.homemind.dto;

import java.io.Serializable;

public class StreamChunkResponse implements Serializable {
    private final String content;
    private final String conversationId;
    private final long timestamp;
    private final String type;

    // 构造方法
    public StreamChunkResponse(String content, String conversationId, String type) {
        this.content = content;
        this.conversationId = conversationId;
        this.timestamp = System.currentTimeMillis();
        this.type = type;
    }

    // 静态工厂方法
    public static StreamChunkResponse chunk(String content, String conversationId) {
        return new StreamChunkResponse(content, conversationId, "chunk");
    }

    public static StreamChunkResponse end(String conversationId) {
        return new StreamChunkResponse("", conversationId, "end");
    }

    // Getter 方法
    public String getContent() { return content; }
    public String getConversationId() { return conversationId; }
    public long getTimestamp() { return timestamp; }
    public String getType() { return type; }
}
