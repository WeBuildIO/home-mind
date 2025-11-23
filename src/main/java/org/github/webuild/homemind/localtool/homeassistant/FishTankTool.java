package org.github.webuild.homemind.localtool.homeassistant;

import org.github.webuild.homemind.properties.HomeAssistantProperties;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;

@Slf4j
@Component
public class FishTankTool {

    private final RestTemplate restTemplate;
    private final HttpHeaders httpHeaders;
    private final ObjectMapper objectMapper;
    private final HomeAssistantProperties homeAssistantProperties;


    // 设备实体ID集中配置（新增亮度控制实体ID）
    private static final String LIGHT_SWITCH_ENTITY_ID = "switch.xiaomi_m200_2c39_switch_status"; // 正确：主灯开关实体ID
    private static final String LIGHT_ENTITY_ID = "light.xiaomi_m200_2c39_light";
    private static final String LIGHT_COLOR_ENTITY_ID = "number.xiaomi_m200_2c39_light_edit_color";
    private static final String LIGHT_BRIGHTNESS_ENTITY_ID = "number.xiaomi_m200_2c39_light_edit_bright"; // 新增：亮度控制实体ID
    private static final String LIGHT_EDIT_SWITCH_ID = "switch.xiaomi_m200_2c39_light_edit_on";
    private static final String PUMP_SWITCH_ENTITY_ID = "switch.xiaomi_m200_2c39_water_pump";
    private static final String PUMP_LEVEL_ENTITY_ID = "select.xiaomi_m200_2c39_pump_flux";
    private static final String FEED_ENTITY_ID = "select.xiaomi_m200_2c39_pet_food_out";
    private static final String FEED_COUNT_ENTITY_ID = "sensor.xiaomi_m200_2c39_today_feeded_num";
    private static final String TEMP_ENTITY_ID = "sensor.xiaomi_m200_2c39_temperature";

    // 颜色映射（名称→RGB数值）
    private static final HashMap<String, Integer> COLOR_MAP = new HashMap<>();
    static {
        COLOR_MAP.put("红色", 0xFF0000);
        COLOR_MAP.put("绿色", 0x00FF00);
        COLOR_MAP.put("蓝色", 0x0000FF);
        COLOR_MAP.put("白色", 0xFFFFFF);
        COLOR_MAP.put("黄色", 0xFFFF00);
        COLOR_MAP.put("粉色", 0xFFC0CB);
        COLOR_MAP.put("青色", 0x00FFFF);
        COLOR_MAP.put("紫色", 0x800080);
    }

    public FishTankTool(RestTemplate restTemplate, ObjectMapper objectMapper, HomeAssistantProperties homeAssistantProperties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.homeAssistantProperties = homeAssistantProperties;

        this.httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.setBearerAuth(homeAssistantProperties.getToken());
    }

    @Tool(description = "控制鱼缸灯光开关，参数：action（打开/关闭）")
    public String controlLightSwitch(String action) {
        if (!List.of("打开", "关闭").contains(action)) return "操作无效，仅支持'打开'/'关闭'";

        // 核心修复：使用switch服务（而非light服务），控制正确的开关实体ID
        String service = action.equals("打开") ? "turn_on" : "turn_off";
        String url = homeAssistantProperties.getUrl() + "/api/services/switch/" + service;
        String body = String.format("{\"entity_id\": \"%s\"}", LIGHT_SWITCH_ENTITY_ID);

        try {
            ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, httpHeaders), String.class);
            if (res.getStatusCode().is2xxSuccessful()) {
                String stateDesc = action.equals("打开") ? "开启" : "关闭";
                return "鱼缸灯光已" + stateDesc + "啦！" + (action.equals("关闭") ? "鱼儿们可以好好休息了～" : "光线柔和不刺眼～");
            }
            return "灯光控制失败，状态码：" + res.getStatusCode();
        } catch (HttpClientErrorException e) {
            log.error("灯光控制失败：{}，请求参数：{}", e.getStatusText(), body);
            return e.getStatusCode() == HttpStatus.UNAUTHORIZED ? "控制失败：令牌无效" : "设备异常";
        } catch (Exception e) {
            log.error("灯光控制异常：", e);
            return "操作失败，请检查鱼缸是否在线";
        }
    }

    @Tool(description = "调节鱼缸灯光颜色，参数：color（红色/绿色/蓝色/白色/黄色/粉色/青色/紫色）")
    public String controlLightColor(String color) {
        if (!COLOR_MAP.containsKey(color)) return "不支持该颜色，仅支持预设8种颜色";

        int rgbValue = COLOR_MAP.get(color);
        String setColorUrl = homeAssistantProperties.getUrl() + "/api/services/number/set_value";
        String setColorBody = String.format("{\"entity_id\": \"%s\", \"value\": %d}", LIGHT_COLOR_ENTITY_ID, rgbValue);

        try {
            ResponseEntity<String> res = restTemplate.exchange(setColorUrl, HttpMethod.POST, new HttpEntity<>(setColorBody, httpHeaders), String.class);
            if (res.getStatusCode().is2xxSuccessful()) {
                enableLightEdit(true);
                return "灯光颜色已切换为" + color + "💡";
            }
            return "颜色调节失败，状态码：" + res.getStatusCode();
        } catch (HttpClientErrorException e) {
            log.error("颜色调节失败：{}", e.getStatusText());
            return e.getStatusCode() == HttpStatus.UNAUTHORIZED ? "查询失败：令牌无效" : "设备不支持颜色调节";
        } catch (Exception e) {
            log.error("颜色调节异常：", e);
            return "操作失败，请检查鱼缸是否在线";
        }
    }

    @Tool(description = "调节鱼缸灯光亮度，参数：brightness（1-100整数）")
    public String controlLightBrightness(Integer brightness) {
        if (brightness == null || brightness < 1 || brightness > 100) return "亮度无效，需传入1-100整数";

        // 直接调用number.set_value服务控制亮度（适配设备实际逻辑）
        String url = homeAssistantProperties.getUrl() + "/api/services/number/set_value";
        String body = String.format("{\"entity_id\": \"%s\", \"value\": %d}", LIGHT_BRIGHTNESS_ENTITY_ID, brightness);

        try {
            ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, httpHeaders), String.class);
            if (res.getStatusCode().is2xxSuccessful()) {
                enableLightEdit(true); // 确保灯光编辑模式开启，亮度生效
                return "鱼缸灯光亮度已调到" + brightness + "%✨ 光线柔和不刺眼～";
            }
            return "亮度调节失败，状态码：" + res.getStatusCode();
        } catch (HttpClientErrorException e) {
            log.error("亮度调节失败：{}，请求参数：{}", e.getStatusText(), body);
            return e.getStatusCode() == HttpStatus.UNAUTHORIZED ? "调节失败：令牌无效" : "设备不支持亮度调节";
        } catch (Exception e) {
            log.error("亮度调节异常：", e);
            return "操作失败，请检查鱼缸是否在线";
        }
    }

    @Tool(description = "控制鱼缸水泵开关，参数：action（打开/关闭）")
    public String controlPumpSwitch(String action) {
        if (!List.of("打开", "关闭").contains(action)) return "操作无效，仅支持'打开'/'关闭'";

        String service = action.equals("打开") ? "turn_on" : "turn_off";
        String url = homeAssistantProperties.getUrl() + "/api/services/switch/" + service;
        String body = String.format("{\"entity_id\": \"%s\"}", PUMP_SWITCH_ENTITY_ID);

        try {
            ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, httpHeaders), String.class);
            if (res.getStatusCode().is2xxSuccessful()) {
                String state = action.equals("打开") ? "开启" : "关闭";
                return "鱼缸水泵" + state + "成功！当前状态：" + state;
            }
            return "水泵控制失败，状态码：" + res.getStatusCode();
        } catch (Exception e) {
            log.error("水泵控制异常：", e);
            return "操作失败，请检查鱼缸是否在线";
        }
    }

    @Tool(description = "调节鱼缸水泵档位，参数：level（Level1/Level2）")
    public String controlPumpLevel(String level) {
        if (!List.of("Level1", "Level2").contains(level)) return "档位无效，仅支持'Level1'/'Level2'";

        String url = homeAssistantProperties.getUrl() + "/api/services/select/select_option";
        String body = String.format("{\"entity_id\": \"%s\", \"option\": \"%s\"}", PUMP_LEVEL_ENTITY_ID, level);

        try {
            ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, httpHeaders), String.class);
            if (res.getStatusCode().is2xxSuccessful()) {
                HaEntity entity = getEntityById(PUMP_LEVEL_ENTITY_ID);
                return "水泵档位切换至" + entity.getState() + "成功！";
            }
            return "档位调节失败，状态码：" + res.getStatusCode();
        } catch (Exception e) {
            log.error("档位调节异常：", e);
            return "操作失败，请检查鱼缸是否在线";
        }
    }

    @Tool(description = "控制鱼缸喂食，参数：foodAmount（1/2/3）")
    public String controlFeeding(String foodAmount) {
        if (!List.of("1", "2", "3").contains(foodAmount)) return "喂食份数无效，仅支持1-3份";

        String url = homeAssistantProperties.getUrl() + "/api/services/select/select_option";
        String body = String.format("{\"entity_id\": \"%s\", \"option\": \"%s\"}", FEED_ENTITY_ID, foodAmount);

        try {
            ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, httpHeaders), String.class);
            if (res.getStatusCode().is2xxSuccessful()) {
                HaEntity countEntity = getEntityById(FEED_COUNT_ENTITY_ID);
                return "喂食" + foodAmount + "份成功！今日累计：" + countEntity.getState() + "份～";
            }
            return "喂食失败，状态码：" + res.getStatusCode();
        } catch (Exception e) {
            log.error("喂食异常：", e);
            return "操作失败，请检查鱼缸是否在线";
        }
    }

    @Tool(description = "查询鱼缸水温，无需参数")
    public String queryFishTankTemperature() {
        try {
            HaEntity tempEntity = getEntityById(TEMP_ENTITY_ID);
            double fahrenheit = Double.parseDouble(tempEntity.getState().trim());
            double celsius = (fahrenheit - 32) * 5 / 9;
            String tip = celsius < 24 ? "⚠️  水温偏低（适宜24-28℃）" : celsius > 28 ? "⚠️  水温偏高（适宜24-28℃）" : "✅  水温适宜";
            return String.format("当前鱼缸水温：%.1f℃\n%s", celsius, tip);
        } catch (Exception e) {
            log.error("水温查询异常：", e);
            return "查询失败，请检查传感器是否在线";
        }
    }

    @Tool(description = "查询鱼缸完整状态，无需参数")
    public String queryFishTankStatus() {
        try {
            // 水温
            HaEntity tempEntity = getEntityById(TEMP_ENTITY_ID);
            double temp = (Double.parseDouble(tempEntity.getState().trim()) - 32) * 5 / 9;
            // 灯光
            HaEntity lightEntity = getEntityById(LIGHT_ENTITY_ID);
            String lightState = lightEntity.getState().equals("on") ? "开启" : "关闭";
            // 水泵
            HaEntity pumpEntity = getEntityById(PUMP_SWITCH_ENTITY_ID);
            String pumpState = pumpEntity.getState().equals("on") ? "开启" : "关闭";
            HaEntity pumpLevelEntity = getEntityById(PUMP_LEVEL_ENTITY_ID);
            // 喂食
            HaEntity feedEntity = getEntityById(FEED_COUNT_ENTITY_ID);

            StringBuilder status = new StringBuilder("当前鱼缸状态：\n");
            status.append("1. 水温：").append(String.format("%.1f℃", temp)).append("\n");
            status.append("2. 灯光：").append(lightState).append("\n");
            status.append("3. 水泵：").append(pumpState).append("（档位：").append(pumpLevelEntity.getState()).append("）\n");
            status.append("4. 今日喂食：").append(feedEntity.getState()).append("份");
            return status.toString();
        } catch (Exception e) {
            log.error("状态查询异常：", e);
            return "查询失败，请检查鱼缸是否在线";
        }
    }

    private HaEntity getEntityById(String entityId) throws Exception {
        String url = homeAssistantProperties.getUrl() + "/api/states/" + entityId;
        ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(httpHeaders), String.class);
        return objectMapper.readValue(res.getBody(), HaEntity.class);
    }

    private void enableLightEdit(boolean enable) {
        try {
            String service = enable ? "turn_on" : "turn_off";
            String url = homeAssistantProperties.getUrl() + "/api/services/switch/" + service;
            String body = String.format("{\"entity_id\": \"%s\"}", LIGHT_EDIT_SWITCH_ID);
            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, httpHeaders), String.class);
        } catch (Exception e) {
            log.error("灯光编辑模式切换异常：", e);
        }
    }

    // 内部实体类
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
        @JsonProperty("color_mode")
        private String colorMode;
    }
}