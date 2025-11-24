package org.github.webuild.homemind.localtool.homeassistant;

import org.github.webuild.homemind.properties.HomeAssistantProperties;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * 石头P10扫地机器人控制工具类（终极自由调度版）
 * 核心特性：随叫随到，强制中断所有当前任务，立即执行新指令（前往房间/回充）
 */
@Slf4j
@Component
public class StoneRobotNavigationTool {

    // 依赖注入组件
    private final RestTemplate restTemplate;
    private final HttpHeaders headers;
    private final ObjectMapper objectMapper;
    private final String haUrl;

    // 机器人实体ID（与HA完全一致）
    private static final String ROBOT_VACUUM_ID = "vacuum.roborock_a74_c190_robot_cleaner";
    private static final String NAVIGATION_STATUS_SENSOR_ID = "sensor.roborock_a74_c190_status";
    private static final String BATTERY_SENSOR_ID = "sensor.roborock_a74_c190_battery_level";

    // 位置→HA房间ID映射（需根据实际room_mapping调整）
    private static final Map<String, Integer> LOCATION_TO_ROOM_ID = new HashMap<>();
    static {
        LOCATION_TO_ROOM_ID.put("客厅", 16);   // 对应HA房间ID 16
        LOCATION_TO_ROOM_ID.put("卧室", 17);   // 对应HA房间ID 17
        LOCATION_TO_ROOM_ID.put("书房", 19);   // 对应HA房间ID 19
        LOCATION_TO_ROOM_ID.put("充电座", -1); // 回充特殊标识
    }

    // 同义词映射（覆盖所有可能的调度指令）
    private static final Map<String, String> SYNONYM_MAP = new HashMap<>();
    static {
        // 回充指令
        SYNONYM_MAP.put("回充", "充电座");
        SYNONYM_MAP.put("返回充电座", "充电座");
        SYNONYM_MAP.put("回家", "充电座");
        SYNONYM_MAP.put("停止并回充", "充电座");
        // 房间调度指令
        SYNONYM_MAP.put("去客厅", "客厅");
        SYNONYM_MAP.put("去卧室", "卧室");
        SYNONYM_MAP.put("去书房", "书房");
        SYNONYM_MAP.put("打扫客厅", "客厅");
        SYNONYM_MAP.put("清扫卧室", "卧室");
        SYNONYM_MAP.put("前往书房", "书房");
        SYNONYM_MAP.put("中断当前任务去客厅", "客厅");
        SYNONYM_MAP.put("别扫了去卧室", "卧室");
    }

    // 构造函数（依赖注入）
    public StoneRobotNavigationTool(RestTemplate restTemplate, ObjectMapper objectMapper, HomeAssistantProperties haProps) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.haUrl = haProps.getUrl();

        // 初始化HTTP请求头
        this.headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(haProps.getToken());
    }

    /**
     * 核心工具方法：随叫随到，强制中断所有任务，执行新指令
     * @param targetLocation 目标位置（支持：客厅/卧室/书房/充电座/各类同义词）
     * @return 执行结果（自然语言提示）
     */
    @Tool(description = "随叫随到，强制中断扫地机器人当前所有任务，立即前往指定房间或返回充电座；参数targetLocation支持：客厅、卧室、书房、充电座、回充、返回充电座、去客厅、打扫卧室等")
    public String navigateToLocation(String targetLocation) {
        try {
            // 1. 预处理指令：同义词转换+格式清理
            String processedLocation = processTargetLocation(targetLocation);

            // 2. 校验指令有效性
            if (!LOCATION_TO_ROOM_ID.containsKey(processedLocation)) {
                return buildUnsupportedMsg();
            }

            // 3. 校验电量（仅低于10%禁止执行，避免彻底没电）
            String batteryCheckResult = checkBatteryLevel();
            if (batteryCheckResult != null) {
                return batteryCheckResult;
            }

            // 4. 强制中断当前任务（无论是否在工作）
            interruptCurrentTask();

            // 5. 执行新指令（前往房间/回充）
            String executeResult = executeNewCommand(processedLocation);

            // 6. 异步监控任务完成状态
            startAsyncTaskMonitor(processedLocation);

            return executeResult;
        } catch (NumberFormatException e) {
            log.error("电池电量格式异常", e);
            return "操作失败：设备电量信息异常";
        } catch (Exception e) {
            log.error("机器人调度异常", e);
            return "操作失败，请检查设备是否在线或重试";
        }
    }

    /**
     * 预处理指令：同义词转换+去空格
     */
    private String processTargetLocation(String targetLocation) {
        if (targetLocation == null || targetLocation.trim().isEmpty()) {
            return "";
        }
        String trimmed = targetLocation.trim();
        return SYNONYM_MAP.getOrDefault(trimmed, trimmed);
    }

    /**
     * 构建不支持指令提示
     */
    private String buildUnsupportedMsg() {
        return "不支持该指令哦～ 目前可调度机器人：\n1. 前往房间：客厅、卧室、书房\n2. 返回充电座：回充、返回充电座、回家";
    }

    /**
     * 校验电池电量（仅低于10%禁止，确保能回充）
     */
    private String checkBatteryLevel() {
        String batteryLevelStr = getEntityState(BATTERY_SENSOR_ID);
        if (batteryLevelStr == null) {
            return "操作失败：无法获取机器人电量";
        }

        double batteryDouble = Double.parseDouble(batteryLevelStr.trim());
        int battery = (int) Math.round(batteryDouble);
        if (battery < 10) {
            return "机器人电量不足10%，已无法执行任务，请手动回充";
        }
        return null;
    }

    /**
     * 强制中断当前任务（发送stop指令）
     */
    private void interruptCurrentTask() {
        Map<String, Object> params = new HashMap<>();
        params.put("entity_id", ROBOT_VACUUM_ID);
        String stopUrl = haUrl + "/api/services/vacuum/stop";

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(params, headers);
            restTemplate.exchange(stopUrl, HttpMethod.POST, request, String.class);
            log.info("已强制中断机器人当前任务");
            // 延迟300ms，确保停止指令生效
            Thread.sleep(300);
        } catch (Exception e) {
            log.error("中断当前任务失败（可能机器人已空闲）", e);
            // 中断失败不影响后续指令执行（可能机器人本就空闲）
        }
    }

    /**
     * 执行新指令（前往房间/回充）
     */
    private String executeNewCommand(String processedLocation) {
        Map<String, Object> params = new HashMap<>();
        Integer roomId = LOCATION_TO_ROOM_ID.get(processedLocation);
        String url = "";

        try {
            params.put("entity_id", ROBOT_VACUUM_ID);

            // 回充指令：调用return_to_base
            if (roomId == -1) {
                url = haUrl + "/api/services/vacuum/return_to_base";
            }
            // 前往房间指令：调用app_segment_clean
            else {
                url = haUrl + "/api/services/vacuum/send_command";
                params.put("command", "app_segment_clean");
                params.put("params", List.of(roomId));
            }

            // 发送新指令
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(params, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String successMsg = buildSuccessMsg(processedLocation);
                log.info("新指令执行成功：{}，参数：{}", successMsg, params);
                return successMsg;
            } else {
                String errorMsg = "新指令发送失败，状态码：" + response.getStatusCode();
                log.error(errorMsg + "，参数：{}", params);
                return errorMsg;
            }
        } catch (HttpClientErrorException e) {
            String errorDetail = "状态码=" + e.getStatusCode() + "，响应体=" + e.getResponseBodyAsString();
            log.error("新指令调用失败：{}，参数：{}", errorDetail, params, e);
            return "新指令执行失败：" + errorDetail;
        } catch (Exception e) {
            log.error("新指令执行异常：参数={}", params, e);
            return "操作失败，请重试";
        }
    }

    /**
     * 构建成功提示信息
     */
    private String buildSuccessMsg(String processedLocation) {
        if ("充电座".equals(processedLocation)) {
            return "已强制中断当前任务，机器人正在返回充电座～";
        } else {
            return "已强制中断当前任务，机器人正在前往" + processedLocation + "清扫～";
        }
    }

    /**
     * 异步监控任务完成状态
     */
    private void startAsyncTaskMonitor(String targetLocation) {
        new Thread(() -> {
            try {
                int timeout = 300; // 5分钟超时
                boolean completed = waitForTaskCompletion(targetLocation, timeout);
                log.info("{}任务监控结果：{}", targetLocation, completed ? "完成" : "超时");
            } catch (InterruptedException e) {
                log.error("监控线程被中断", e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("任务监控异常", e);
            }
        }, "robot-task-monitor").start();
    }

    /**
     * 等待任务完成（轮询状态）
     */
    private boolean waitForTaskCompletion(String targetLocation, int timeout) throws InterruptedException {
        long start = System.currentTimeMillis();
        Integer roomId = LOCATION_TO_ROOM_ID.get(targetLocation);

        while (System.currentTimeMillis() - start < timeout * 1000) {
            String currentState = getRobotState();

            // 回充任务：停靠/充电即完成
            if (roomId == -1) {
                if ("docked".equals(currentState) || "charging".equals(currentState)) {
                    return true;
                }
            }
            // 清扫任务：空闲/停靠即完成
            else {
                if ("idle".equals(currentState) || "docked".equals(currentState)) {
                    return true;
                }
            }

            Thread.sleep(3000);
        }
        return false;
    }

    /**
     * 获取机器人当前状态
     */
    private String getRobotState() {
        try {
            String state = getEntityState(NAVIGATION_STATUS_SENSOR_ID);
            return state == null ? "" : state.toLowerCase();
        } catch (Exception e) {
            log.error("查询机器人状态失败", e);
            return "";
        }
    }

    /**
     * 通用方法：获取HA实体状态
     */
    private String getEntityState(String entityId) {
        try {
            String url = haUrl + "/api/states/" + entityId;
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return objectMapper.readTree(response.getBody()).get("state").asText();
        } catch (Exception e) {
            log.error("查询HA实体状态失败：entityId={}", entityId, e);
            return null;
        }
    }
}