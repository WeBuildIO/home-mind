package org.github.webuild.homemind.service;

import lombok.extern.slf4j.Slf4j;
import org.github.webuild.homemind.dto.ChatRequest;
import org.github.webuild.homemind.dto.ChatResponse;
import org.github.webuild.homemind.dto.StreamChunkResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public ChatService(ChatClient.Builder chatClientBuilder) {
        // 创建内存记忆 - 保存最近10轮对话
        this.chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(100)
                .build();

        // 创建 ChatClient 并配置记忆功能
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                你是一个友好的AI聊天伙伴，名字叫"小派"。
                语气亲切自然，像朋友一样聊天，会记住对话上下文。
                回答简洁但富有情感，适当使用表情符号。
                """)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
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
        chatResponse.setChatReply(response);
        chatResponse.setConversationId(conversationId);
        chatResponse.setTimestamp(System.currentTimeMillis());
        return chatResponse;
    }

    /**
     * 流式处理用户消息 - 使用具体对象版本
     */
    public Flux<StreamChunkResponse> processMessageStreamWithObject(ChatRequest request) {
        String conversationId = getOrCreateConversationId(request);

        Flux<StreamChunkResponse> contentStream = chatClient.prompt()
                .user(request.getMessage())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .map(chunk -> StreamChunkResponse.chunk(chunk, conversationId));

        return contentStream.concatWith(
                Mono.just(StreamChunkResponse.end(conversationId))
        );
    }

    /**
     * 获取或创建会话ID
     */
    private String getOrCreateConversationId(ChatRequest request) {
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
