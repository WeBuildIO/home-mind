package org.github.webuild.homemind.localtool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 终极简化版HA工具类：仅一个方法，查询所有设备状态，无需用户传任何参数
 */
public class HomeAssistantTools {

    @Value("${homeassistant.url}")
    private String haUrl;

    @Value("${homeassistant.token}")
    private String haToken;

    private final RestTemplate restTemplate = new RestTemplate();

    @Tool(description = "查询所有Home Assistant智能家居设备的状态，无需任何参数，直接调用")
    public String getAllDevicesStatus() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + haToken);

            ResponseEntity<Map[]> response = restTemplate.exchange(
                    haUrl + "/api/states",
                    org.springframework.http.HttpMethod.GET,
                    new org.springframework.http.HttpEntity<>(headers),
                    Map[].class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                StringBuilder result = new StringBuilder("所有设备状态：\n");
                for (Map device : response.getBody()) {
                    String friendlyName = (String) ((Map) device.get("attributes")).getOrDefault("friendly_name", "未命名设备");
                    String state = (String) device.get("state");
                    result.append("- ").append(friendlyName).append("：").append(state).append("\n");
                }
                return result.toString();
            } else {
                return "查询失败，HA返回异常";
            }
        } catch (Exception e) {
            return "查询失败：" + e.getMessage();
        }
    }
}