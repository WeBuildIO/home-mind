package org.github.webuild.homemind.service;

import lombok.extern.slf4j.Slf4j;
import org.github.webuild.homemind.dto.ChatRequest;
import org.github.webuild.homemind.dto.ChatResponse;
import org.github.webuild.homemind.localtool.DateTimeTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public ChatService(ChatClient.Builder chatClientBuilder) {
        // 创建内存记忆 - 保存最近100轮对话
        this.chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(100)
                .build();

        // 创建 ChatClient 并配置记忆功能
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        你是一个友好的AI聊天伙伴，名字叫"小派"。
                        语气亲切自然，像朋友一样聊天，会记住对话上下文。
                        回答简洁但富有情感。
                        """)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .defaultTools(new DateTimeTools())
                .build();
    }

    /**
     * 处理用户消息 - 核心方法
     */
    public ChatResponse processMessage(ChatRequest request) {
        String conversationId = getOrCreateConversationId(request);

        String response = chatClient.prompt()
                .user(request.getMessage())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
        log.info("Chat response: {}", response);
        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setRecognizedText(request.getMessage());
        chatResponse.setChatReply(response);
        chatResponse.setConversationId(conversationId);
        chatResponse.setTimestamp(System.currentTimeMillis());
        return chatResponse;
    }

    /**
     * 获取或创建会话ID
     */
    public String getOrCreateConversationId(ChatRequest request) {
        if (request.getConversationId() != null && !request.getConversationId().trim().isEmpty()) {
            return request.getConversationId();
        }
        return "conv_" + System.currentTimeMillis();
    }

    /**
     * 清除对话记忆
     */
    public void clearMemory(String conversationId) {
        chatMemory.clear(conversationId);
    }
}
