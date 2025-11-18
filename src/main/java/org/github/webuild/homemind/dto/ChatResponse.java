package org.github.webuild.homemind.dto;

import lombok.Data;

@Data
public class ChatResponse {
    private String recognizedText;
    private String chatReply;
    private String conversationId;
    private long timestamp;
    private String error;
}