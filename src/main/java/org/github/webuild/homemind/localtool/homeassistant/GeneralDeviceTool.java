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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class GeneralDeviceTool {

    private final RestTemplate restTemplate;
    private final HttpHeaders httpHeaders;
    private final ObjectMapper objectMapper;
    private final HomeAssistantProperties homeAssistantProperties;

    // 通用设备类型映射（可根据HA支持的设备类型扩展）
    private static final HashMap<String, String> DEVICE_TYPE_MAP = new HashMap<>();
    static {
        DEVICE_TYPE_MAP.put("switch.", "开关");
        DEVICE_TYPE_MAP.put("light.", "灯光");
        DEVICE_TYPE_MAP.put("climate.", "空调");
        DEVICE_TYPE_MAP.put("fan.", "风扇");
        DEVICE_TYPE_MAP.put("cover.", "窗帘");
        DEVICE_TYPE_MAP.put("lock.", "门锁");
        DEVICE_TYPE_MAP.put("media_player.", "媒体播放器");
    }

    public GeneralDeviceTool(RestTemplate restTemplate, ObjectMapper objectMapper, HomeAssistantProperties homeAssistantProperties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.homeAssistantProperties = homeAssistantProperties;

        this.httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.setBearerAuth(homeAssistantProperties.getToken());
    }

    @Tool(description = "查询所有在线的智能家居设备状态（通用设备），无需传入参数")
    public String queryAllOnlineDevices() {
        String url = homeAssistantProperties.getUrl() + "/api/states";
        HttpEntity<Void> requestEntity = new HttpEntity<>(httpHeaders);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            List<HaEntity> entityList = objectMapper.readValue(response.getBody(), new TypeReference<>() {});

            List<String> deviceList = new ArrayList<>();
            for (HaEntity entity : entityList) {
                if (isDeviceOnline(entity)) {
                    String deviceName = Optional.ofNullable(entity.getAttributes().getFriendlyName()).orElse(entity.getEntityId());
                    String deviceType = getDeviceType(entity.getEntityId());
                    String deviceState = formatDeviceState(entity.getEntityId(), entity.getState());
                    deviceList.add(String.format("%s（%s）：%s", deviceName, deviceType, deviceState));
                }
            }

            return deviceList.isEmpty()
                    ? "未查询到在线的智能家居设备"
                    : "当前在线设备状态：\n" + String.join("\n", deviceList);
        } catch (HttpClientErrorException e) {
            log.error("查询通用设备失败：{}", e.getStatusText());
            return "查询失败：" + (e.getStatusCode() == HttpStatus.UNAUTHORIZED ? "令牌无效" : "服务异常");
        } catch (Exception e) {
            log.error("查询通用设备异常：", e);
            return "查询时发生未知错误，请检查Home Assistant状态";
        }
    }

    @Tool(description = "查询单个通用设备的详细信息，参数：设备名称或实体ID（如'客厅灯光'、'switch.living_room'）")
    public String querySingleDevice(String deviceIdentifier) {
        if (deviceIdentifier == null || deviceIdentifier.trim().isEmpty()) {
            return "请传入设备名称或实体ID";
        }

        String identifier = deviceIdentifier.trim();
        try {
            String url = homeAssistantProperties.getUrl() + "/api/states/" + identifier;
            HttpEntity<Void> requestEntity = new HttpEntity<>(httpHeaders);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            HaEntity entity = objectMapper.readValue(response.getBody(), HaEntity.class);

            if (isDeviceOnline(entity)) {
                String deviceName = Optional.ofNullable(entity.getAttributes().getFriendlyName()).orElse(identifier);
                String deviceType = getDeviceType(entity.getEntityId());
                String deviceState = formatDeviceState(entity.getEntityId(), entity.getState());
                String updateTime = ZonedDateTime.parse(entity.getLastUpdated())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                return String.format("设备详细信息：\n名称：%s\n类型：%s\n状态：%s\n最后更新时间：%s",
                        deviceName, deviceType, deviceState, updateTime);
            } else {
                return "该设备未在线";
            }
        } catch (HttpClientErrorException.NotFound e) {
            return searchDeviceByName(identifier);
        } catch (HttpClientErrorException e) {
            log.error("查询设备{}失败：{}", identifier, e.getStatusText());
            return "查询失败：" + (e.getStatusCode() == HttpStatus.UNAUTHORIZED ? "令牌无效" : "服务异常");
        } catch (Exception e) {
            log.error("查询设备{}异常：", identifier, e);
            return "查询时发生未知错误，请检查设备是否在线";
        }
    }

    @Tool(description = "控制通用开关/灯光类设备的开关状态，参数：1.设备名称/实体ID；2.操作（'打开'或'关闭'）")
    public String controlSwitchDevice(String deviceIdentifier, String action) {
        if (deviceIdentifier == null || deviceIdentifier.trim().isEmpty()) {
            return "请传入设备名称或实体ID";
        }
        if (action == null || !("打开".equalsIgnoreCase(action) || "关闭".equalsIgnoreCase(action))) {
            return "操作无效，请传入'打开'或'关闭'";
        }

        String identifier = deviceIdentifier.trim();
        try {
            String entityId = getDeviceEntityId(identifier);
            if (entityId == null) {
                return "未找到该设备";
            }

            if (!entityId.startsWith("switch.") && !entityId.startsWith("light.")) {
                return "该设备不是开关/灯光类设备，不支持开关控制";
            }

            String service = "打开".equalsIgnoreCase(action) ? "turn_on" : "turn_off";
            String domain = entityId.startsWith("light.") ? "light" : "switch";
            String url = homeAssistantProperties.getUrl() + "/api/services/" + domain + "/" + service;
            String requestBody = String.format("{\"entity_id\": \"%s\"}", entityId);
            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, httpHeaders);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                HaEntity updatedEntity = getEntityById(entityId);
                String deviceState = formatDeviceState(entityId, updatedEntity.getState());
                return String.format("操作成功！%s当前状态：%s", identifier, deviceState);
            } else {
                return "操作失败，状态码：" + response.getStatusCode();
            }
        } catch (HttpClientErrorException e) {
            log.error("控制设备{}失败：{}", identifier, e.getStatusText());
            return "控制失败：" + (e.getStatusCode() == HttpStatus.UNAUTHORIZED ? "令牌无效" : "设备不支持该操作");
        } catch (Exception e) {
            log.error("控制设备{}异常：", identifier, e);
            return "控制时发生未知错误，请检查设备是否在线";
        }
    }

    @Tool(description = "模糊查询设备（根据名称关键词匹配），参数：设备名称关键词（如'灯光'、'开关'）")
    public String searchDeviceByName(String deviceName) {
        if (deviceName == null || deviceName.trim().isEmpty()) {
            return "请传入设备名称关键词";
        }

        String keyword = deviceName.trim().toLowerCase();
        String url = homeAssistantProperties.getUrl() + "/api/states";
        HttpEntity<Void> requestEntity = new HttpEntity<>(httpHeaders);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            List<HaEntity> entityList = objectMapper.readValue(response.getBody(), new TypeReference<List<HaEntity>>() {});

            List<String> matchedDevices = new ArrayList<>();
            for (HaEntity entity : entityList) {
                if (isDeviceOnline(entity)) {
                    String friendlyName = Optional.ofNullable(entity.getAttributes().getFriendlyName()).orElse("").toLowerCase();
                    String entityId = entity.getEntityId().toLowerCase();
                    if (friendlyName.contains(keyword) || entityId.contains(keyword)) {
                        String deviceType = getDeviceType(entity.getEntityId());
                        String deviceState = formatDeviceState(entity.getEntityId(), entity.getState());
                        matchedDevices.add(String.format("%s（%s）：%s",
                                Optional.ofNullable(entity.getAttributes().getFriendlyName()).orElse(entityId),
                                deviceType,
                                deviceState));
                    }
                }
            }

            if (matchedDevices.isEmpty()) {
                return "未找到名称包含'" + deviceName + "'的在线设备";
            } else if (matchedDevices.size() == 1) {
                return "找到匹配设备：\n" + matchedDevices.get(0);
            } else {
                return "找到多个匹配设备：\n" + String.join("\n----------------\n", matchedDevices);
            }
        } catch (Exception e) {
            log.error("模糊查询设备失败：", e);
            return "查询时发生错误，请重试";
        }
    }

    // ------------------------------ 私有辅助方法 ------------------------------
    /**
     * 根据实体ID查询设备状态
     */
    private HaEntity getEntityById(String entityId) throws Exception {
        String url = homeAssistantProperties.getUrl() + "/api/states/" + entityId;
        HttpEntity<Void> requestEntity = new HttpEntity<>(httpHeaders);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
        return objectMapper.readValue(response.getBody(), HaEntity.class);
    }

    /**
     * 判断设备是否在线（排除unavailable/unknown状态）
     */
    private boolean isDeviceOnline(HaEntity entity) {
        if (entity == null || entity.getState() == null) {
            return false;
        }
        String state = entity.getState().toLowerCase();
        return !state.equals("unavailable") && !state.equals("unknown");
    }

    /**
     * 获取设备类型（根据entity_id前缀匹配）
     */
    private String getDeviceType(String entityId) {
        for (String prefix : DEVICE_TYPE_MAP.keySet()) {
            if (entityId.startsWith(prefix)) {
                return DEVICE_TYPE_MAP.get(prefix);
            }
        }
        return "智能设备";
    }

    /**
     * 格式化设备状态（将on/off等统一转换为中文）
     */
    private String formatDeviceState(String entityId, String state) {
        if (state == null) {
            return "未知";
        }
        String lowerState = state.toLowerCase();
        return switch (lowerState) {
            case "on" -> "开启";
            case "off" -> "关闭";
            case "idle" -> "待机";
            case "heat" -> "制热";
            case "cool" -> "制冷";
            case "auto" -> "自动";
            case "opened" -> "开启";
            case "closed" -> "关闭";
            default -> state;
        };
    }

    /**
     * 根据名称或关键词获取设备实体ID
     */
    private String getDeviceEntityId(String identifier) {
        if (identifier.contains(".")) {
            return identifier;
        }

        String url = homeAssistantProperties.getUrl() + "/api/states";
        HttpEntity<Void> requestEntity = new HttpEntity<>(httpHeaders);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            List<HaEntity> entityList = objectMapper.readValue(response.getBody(), new TypeReference<List<HaEntity>>() {});

            for (HaEntity entity : entityList) {
                String friendlyName = Optional.ofNullable(entity.getAttributes().getFriendlyName()).orElse("");
                if (friendlyName.equalsIgnoreCase(identifier) && isDeviceOnline(entity)) {
                    return entity.getEntityId();
                }
            }
            return null;
        } catch (Exception e) {
            log.error("获取设备实体ID失败：", e);
            return null;
        }
    }

    // ------------------------------ 内部实体类 ------------------------------
    @Data
    private static class HaEntity {
        @JsonProperty("entity_id")
        private String entityId;
        private String state;
        @JsonProperty("attributes")
        private HaAttributes attributes;
        @JsonProperty("last_updated")
        private String lastUpdated;
    }

    @Data
    private static class HaAttributes {
        @JsonProperty("friendly_name")
        private String friendlyName;
    }
}