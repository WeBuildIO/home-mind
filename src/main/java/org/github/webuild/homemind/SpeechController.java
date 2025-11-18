package org.github.webuild.homemind;

import lombok.RequiredArgsConstructor;
import org.github.webuild.homemind.dto.ChatRequest;
import org.github.webuild.homemind.dto.ChatResponse;
import org.github.webuild.homemind.service.BaiduSpeechService;
import org.github.webuild.homemind.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/speech")
@RequiredArgsConstructor
public class SpeechController {

    private final BaiduSpeechService speechService;

    private final ChatService chatService;

    /**
     * 语音识别 + 连续对话（复用 ChatResponse，返回统一 Map 结果）
     * @param pcmData 客户端上传的PCM音频
     * @param conversationId 可选：会话ID（首次请求可不传）
     */
    @PostMapping("/recognize-chat")
    public ResponseEntity<ChatResponse> recognizeAndChat(
            @RequestBody byte[] pcmData,
            @RequestParam(required = false) String conversationId
    ) {
        try {
            // 1. 语音识别：PCM → 文本
            String recognizedText = speechService.recognize(pcmData);
            if (recognizedText == null || recognizedText.trim().isEmpty()) {
                ChatResponse chatResponse = new ChatResponse();
                chatResponse.setError("未识别到语音内容");
                return ResponseEntity.badRequest().body(chatResponse);
            }

            // 2. 构造对话请求，调用 ChatService
            ChatRequest chatRequest = new ChatRequest();
            chatRequest.setMessage(recognizedText);
            chatRequest.setConversationId(conversationId);
            ChatResponse chatResponse = chatService.processMessage(chatRequest);
            return ResponseEntity.ok(chatResponse);
        } catch (Exception e) {
            ChatResponse chatResponse = new ChatResponse();
            chatResponse.setError(e.getMessage());
            chatResponse.setTimestamp(System.currentTimeMillis());
            return ResponseEntity.internalServerError().body(chatResponse);
        }
    }

    /**
     * 清除对话记忆（复用原有 ChatService 方法）
     */
    @PostMapping("/clear-memory")
    public ResponseEntity<ChatResponse> clearChatMemory(@RequestParam String conversationId) {
        try {
            chatService.clearMemory(conversationId);
            ChatResponse response = new ChatResponse();
            response.setChatReply("会话记忆已清除：" + conversationId);
            response.setConversationId(conversationId);
            response.setTimestamp(System.currentTimeMillis());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setChatReply("清除失败：" + e.getMessage());
            errorResponse.setConversationId(conversationId);
            errorResponse.setTimestamp(System.currentTimeMillis());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}