package org.github.chuan.homemind;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 主要聊天接口
     */
    @PostMapping("/send")
    public ResponseEntity<ChatResponse> sendMessage(@RequestBody ChatRequest request) {
        ChatResponse response = chatService.processMessage(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 快速聊天（无需会话ID）
     */
    @PostMapping("/quick")
    public ResponseEntity<ChatResponse> quickChat(@RequestParam String message) {
        ChatRequest request = new ChatRequest(message, null);
        ChatResponse response = chatService.processMessage(request);
        return ResponseEntity.ok(response);
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

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "AI Chatbot",
                "timestamp", Instant.now().toString()
        ));
    }
}



