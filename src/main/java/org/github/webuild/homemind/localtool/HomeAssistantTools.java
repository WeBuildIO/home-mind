package org.github.webuild.homemind.localtool;

import org.springframework.ai.tool.annotation.Tool;

/**
 * 终极简化版HA工具类：仅一个方法，查询所有设备状态，无需用户传任何参数
 */
public class HomeAssistantTools {
    @Tool(description = "查询所有Home Assistant智能家居设备的状态，无需任何参数，直接调用")
    public String getAllDevicesStatus() {
        return "小米鱼缸";
    }
}