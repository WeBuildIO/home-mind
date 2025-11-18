package org.github.webuild.homemind.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String conversationId;
}
