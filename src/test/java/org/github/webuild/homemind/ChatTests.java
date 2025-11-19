package org.github.webuild.homemind;

import lombok.extern.slf4j.Slf4j;
import org.github.webuild.homemind.dto.ChatRequest;
import org.github.webuild.homemind.dto.ChatResponse;
import org.github.webuild.homemind.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StringUtils;

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
}
