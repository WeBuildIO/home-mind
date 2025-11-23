package org.github.webuild.homemind.localtool.homeassistant;

import org.github.webuild.homemind.properties.HomeAssistantProperties;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class TempHumiditySensorTool {

    private final RestTemplate restTemplate;
    private final HttpHeaders httpHeaders;
    private final ObjectMapper objectMapper;
    private final HomeAssistantProperties homeAssistantProperties;

    public TempHumiditySensorTool(RestTemplate restTemplate, ObjectMapper objectMapper, HomeAssistantProperties homeAssistantProperties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.homeAssistantProperties = homeAssistantProperties;

        this.httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.setBearerAuth(homeAssistantProperties.getToken());
    }

    @Tool(description = "查看室内温湿度数据，无需参数")
    public String queryIndoorTempHumidity() {
        String url = homeAssistantProperties.getUrl() + "/api/states";
        HttpEntity<Void> requestEntity = new HttpEntity<>(httpHeaders);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            List<HaEntity> entityList = objectMapper.readValue(response.getBody(), new TypeReference<>() {});

            List<SensorData> sensorDataList = new ArrayList<>();
            for (HaEntity entity : entityList) {
                String entityId = entity.getEntityId().toLowerCase();
                String friendlyName = Optional.ofNullable(entity.getAttributes().getFriendlyName()).orElse("");

                boolean isIndoorTempSensor = entityId.contains("temperature") && !entityId.contains("xiaomi_m200");
                boolean isIndoorHumiditySensor = entityId.contains("humidity") && !entityId.contains("xiaomi_m200");

                if ((isIndoorTempSensor || isIndoorHumiditySensor) && isSensorOnline(entity)) {
                    String deviceName = friendlyName.replace(" 温度", "").replace(" 湿度", "");
                    double value = Double.parseDouble(entity.getState().trim());
                    String unit = entityId.contains("temperature") ? "℃" : "%";
                    String type = entityId.contains("temperature") ? "温度" : "湿度";

                    // 温度单位转换：华氏度（°F）→ 摄氏度（℃）
                    if ("温度".equals(type)) {
                        value = (value - 32) * 5 / 9; // 华氏转摄氏公式
                    }

                    // 合并同一设备的温湿度数据
                    Optional<SensorData> existing = sensorDataList.stream()
                            .filter(data -> data.name.equals(deviceName))
                            .findFirst();

                    if (existing.isPresent()) {
                        SensorData data = existing.get();
                        if ("温度".equals(type)) {
                            data.temperature = String.format("%.1f%s", value, unit);
                        } else {
                            data.humidity = String.format("%.1f%s", value, unit);
                        }
                    } else {
                        SensorData newData = new SensorData();
                        newData.name = deviceName;
                        if ("温度".equals(type)) {
                            newData.temperature = String.format("%.1f%s", value, unit);
                            newData.humidity = "未检测";
                        } else {
                            newData.humidity = String.format("%.1f%s", value, unit);
                            newData.temperature = "未检测";
                        }
                        sensorDataList.add(newData);
                    }
                }
            }

            if (sensorDataList.isEmpty()) return "未查询到在线的室内温湿度设备～";

            // 格式化返回结果
            StringBuilder result = new StringBuilder("室内温湿度数据：\n\n");
            for (SensorData data : sensorDataList) {
                result.append(data.name).append("\n");
                result.append("  温度：").append(data.temperature).append("\n");
                result.append("  湿度：").append(data.humidity).append("\n\n");
            }
            return result.toString().trim();
        } catch (HttpClientErrorException e) {
            log.error("温湿度查询失败：{}", e.getStatusText());
            return e.getStatusCode() == HttpStatus.UNAUTHORIZED ? "查询失败：令牌无效" : "查询失败：服务异常";
        } catch (Exception e) {
            log.error("温湿度查询异常：", e);
            return "查询时发生错误，请检查设备是否在线";
        }
    }

    private boolean isSensorOnline(HaEntity entity) {
        try {
            return entity != null && entity.getState() != null && !entity.getState().trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @Data
    private static class SensorData {
        private String name;
        private String temperature;
        private String humidity;
    }

    @Data
    private static class HaEntity {
        @JsonProperty("entity_id")
        private String entityId;
        private String state;
        @JsonProperty("attributes")
        private HaAttributes attributes;
    }

    @Data
    private static class HaAttributes {
        @JsonProperty("friendly_name")
        private String friendlyName;
    }
}