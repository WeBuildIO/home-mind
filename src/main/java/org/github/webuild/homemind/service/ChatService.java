package org.github.webuild.homemind.service;

import lombok.extern.slf4j.Slf4j;
import org.github.webuild.homemind.dto.ChatRequest;
import org.github.webuild.homemind.dto.ChatResponse;
import org.github.webuild.homemind.localtool.DateTimeTools;
import org.github.webuild.homemind.localtool.homeassistant.FishTankTool;
import org.github.webuild.homemind.localtool.homeassistant.GeneralDeviceTool;
import org.github.webuild.homemind.localtool.homeassistant.TempHumiditySensorTool;
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

    public ChatService(ChatClient.Builder chatClientBuilder,
                       GeneralDeviceTool generalDeviceTool,
                       FishTankTool fishTankTool,
                       TempHumiditySensorTool tempHumiditySensorTool) {
        // 创建内存记忆 - 保存最近100轮对话
        this.chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(100)
                .build();

        // 创建 ChatClient 并配置记忆功能
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        你是有温度的AI同伴Siri，语气亲切自然。
                        核心能帮我处理智能家居相关事务，包括设备控制、状态查询等，
                        操作精准高效，回答简洁明了
                        【强制规则】
                                1. 所有设备控制类请求（如开关、调节、喂食、调节颜色、获取温湿度等），必须调用对应的Tool方法，绝对禁止直接返回"已关闭、已调整"等结果；
                                2. 若未找到对应Tool或调用失败，只能返回"操作失败，请检查设备是否在线或重试"；
                                3. 禁止编造操作结果，所有状态必须来自Tool的返回值。
                        """)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .defaultTools(
                        new DateTimeTools(),
                        generalDeviceTool,
                        fishTankTool,
                        tempHumiditySensorTool
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
