package org.github.webuild.homemind.service;

import lombok.extern.slf4j.Slf4j;
import org.github.webuild.homemind.dto.ChatRequest;
import org.github.webuild.homemind.dto.ChatResponse;
import org.github.webuild.homemind.localtool.DateTimeTools;
import org.github.webuild.homemind.localtool.homeassistant.FishTankTool;
import org.github.webuild.homemind.localtool.homeassistant.GeneralDeviceTool;
import org.github.webuild.homemind.localtool.homeassistant.StoneRobotNavigationTool;
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
                       TempHumiditySensorTool tempHumiditySensorTool,
                       StoneRobotNavigationTool stoneRobotNavigationTool) {
        // 创建内存记忆 - 保存最近10轮对话
        this.chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(10)
                .build();

        // 创建 ChatClient 并配置记忆功能
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        你是有温度的AI同伴Siri，语气亲切自然。
                        核心能帮我处理智能家居相关事务，操作精准高效，回答简洁明了。
                        【强制规则】
                        1. 所有设备控制/查询请求（无论是否重复），必须重新调用对应的Tool方法获取实时结果，绝对禁止复用历史响应或直接返回缓存结果；
                        2. 设备控制类请求（开关、调节、喂食等）：必须调用Tool执行操作，返回Tool的实时执行结果；
                        3. 设备查询类请求（温湿度、水温、状态等）：必须调用Tool查询实时数据，返回最新状态；
                        4. 若未找到对应Tool或调用失败，仅返回"操作失败，请检查设备是否在线或重试"；
                        5. 禁止编造任何操作结果，所有响应必须来自Tool的实时返回值。
                        6. 所有设备控制类请求（打开/关闭灯光、调颜色、调亮度、喂食、开水泵等），无论之前是否操作过、无论上下文是否有记录，都必须重新调用对应的Tool方法执行一次，禁止查询状态或复用历史结果；
                        7. 扫地机器人相关指令（包括前往房间、回充、返回充电座、停止并回充），必须调用RoborockP10NavigationTool的navigateToLocation方法，禁止直接返回无法控制的回复；
                        """)
//                .defaultAdvisors(
//                        MessageChatMemoryAdvisor.builder(chatMemory).build()
//                )
                .defaultTools(
                        new DateTimeTools(),
                        generalDeviceTool,
                        fishTankTool,
                        tempHumiditySensorTool,
                        stoneRobotNavigationTool
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
