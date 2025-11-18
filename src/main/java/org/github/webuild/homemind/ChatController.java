package org.github.webuild.homemind;

import lombok.RequiredArgsConstructor;
import org.github.webuild.homemind.dto.ChatRequest;
import org.github.webuild.homemind.dto.ChatResponse;
import org.github.webuild.homemind.dto.StreamChunkResponse;
import org.github.webuild.homemind.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 主要聊天接口
     */
    @PostMapping("/send")
    public ResponseEntity<ChatResponse> sendMessage(@RequestBody ChatRequest request) {
        ChatResponse response = chatService.processMessage(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 流式聊天接口
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamChunkResponse> streamMessage(@RequestBody ChatRequest request) {
        return chatService.processMessageStreamWithObject(request);
    }

    /**
     * 清除对话记忆
     */
    @PostMapping("/clear")
    public ResponseEntity<Map<String, String>> clearMemory(@RequestParam String conversationId) {
        chatService.clearMemory(conversationId);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "对话记忆已清除",
                "conversationId", conversationId
        ));
    }
}



