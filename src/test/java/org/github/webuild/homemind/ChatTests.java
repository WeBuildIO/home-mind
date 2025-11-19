package org.github.webuild.homemind;

import lombok.extern.slf4j.Slf4j;
import org.github.webuild.homemind.dto.ChatRequest;
import org.github.webuild.homemind.dto.ChatResponse;
import org.github.webuild.homemind.dto.StreamChunkResponse;
import org.github.webuild.homemind.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StringUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Slf4j
@SpringBootTest
public class ChatTests {

    @Autowired
    private ChatService chatService;

    @Test
    public void syncChatTest() {
        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setMessage("你好，我叫小明");
        ChatResponse chatResponse = chatService.processMessage(chatRequest);
        assert StringUtils.hasLength(chatResponse.getConversationId());
        chatRequest.setMessage("我叫什么？");
        chatRequest.setConversationId(chatResponse.getConversationId());
        chatResponse = chatService.processMessage(chatRequest);
        assert chatResponse.getChatReply().contains("小明");
    }

    @Test
    public void streamChatTest() throws InterruptedException {
        // 1. 构造流式请求
        ChatRequest request = new ChatRequest();
        request.setMessage("流式对话测试，介绍下自己");
        var stream = chatService.processMessageStreamWithObject(request);

        // 2. 用 CountDownLatch 等待流式结束，收集关键结果
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean hasValidChunk = new AtomicBoolean(false); // 是否收到有效回复片段
        AtomicReference<String> conversationId = new AtomicReference<>();  // 会话ID
        AtomicBoolean hasEndMark = new AtomicBoolean(false);    // 是否收到结束标记

        // 3. 订阅流式响应（阻塞等待结果）
        stream.subscribe(
                chunk -> {
                    System.out.printf("流式 chunk：type=%s, content=%s%n", chunk.getType(), chunk.getContent());
                    // 验证有有效 CHUNK（回复片段）和会话ID
                    if (StreamChunkResponse.Type.CHUNK.equals(chunk.getType())
                            && chunk.getContent() != null
                            && chunk.getConversationId() != null) {
                        hasValidChunk.set(true);
                        conversationId.set(chunk.getConversationId());
                    }
                    // 验证有 END 结束标记
                    if (StreamChunkResponse.Type.END.equals(chunk.getType())) {
                        hasEndMark.set(true);
                    }
                },
                error -> {
                    System.out.printf("流式错误 %s", error);
                    latch.countDown();
                },
                () -> {
                    System.out.println("流式结束");
                    latch.countDown(); // 流式完成后释放锁
                }
        );

        // 4. 等待流式响应完成（最多10秒，避免无限阻塞）
        boolean isComplete = latch.await(30, TimeUnit.SECONDS);

        // 5. 核心验证（正常流程必须满足以下条件）
        assertThat(isComplete).isTrue();        // 流式在10秒内正常结束
        assertThat(hasValidChunk.get()).isTrue();     // 收到有效回复片段
        assertThat(conversationId.get()).isNotEmpty();// 会话ID非空
        assertThat(hasEndMark.get()).isTrue();        // 收到 END 结束标记
    }
}
