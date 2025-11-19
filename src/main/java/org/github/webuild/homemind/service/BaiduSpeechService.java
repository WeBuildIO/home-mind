package org.github.webuild.homemind.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class BaiduSpeechService {

    @Value("${baidu.speech.api-key}")
    private String apiKey;

    @Value("${baidu.speech.secret-key}")
    private String secretKey;

    private static final String ASR_URL  = "https://vop.baidu.com/server_api";
    private static final String TTS_URL = "https://tsn.baidu.com/text2audio";
    private String accessToken;
    private long tokenExpireTime;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 初始化：添加表单消息转换器（支持application/x-www-form-urlencoded）
    public BaiduSpeechService() {
        this.restTemplate = new RestTemplate();
        this.restTemplate.getMessageConverters().add(new FormHttpMessageConverter());
    }

    // 原有语音识别方法（不变）
    public String recognize(byte[] pcmData) {
        // PCM数据转Base64（百度API要求）
        String audioBase64 = Base64.getEncoder().encodeToString(pcmData);

        // 构建识别请求参数
        Map<String, Object> params = new HashMap<>();
        params.put("format", "pcm");       // 音频格式
        params.put("rate", 16000);         // 采样率（和客户端一致）
        params.put("channel", 1);          // 单声道
        params.put("cuid", "home-mind");  // 设备标识
        params.put("token", getAccessToken());
        params.put("speech", audioBase64); // Base64编码的音频
        params.put("len", pcmData.length); // 音频字节数

        // 发送请求到百度API
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(params, headers);
        Map<String, Object> resp = restTemplate.postForObject(ASR_URL, request, Map.class);

        // 解析识别结果
        int errNo = (Integer) resp.get("err_no");
        if (errNo != 0) {
            throw new RuntimeException("百度语音识别失败：err_no=" + errNo + ", err_msg=" + resp.get("err_msg"));
        }
        List<String> result = (List<String>) resp.get("result");
        return result.get(0).trim();
    }

    /**
     * 整段文本转语音（最终修正版，适配百度TTS接口）
     * @param text 合成文本（≤1024 GBK字节，约500汉字）
     * @return PCM_16k格式音频字节数组（客户端直接播放）
     */
    public byte[] textToSpeech(String text) {
        try {
            // 截断超长文本（百度限制≤1024 GBK字节）
            byte[] gbkBytes = text.getBytes("GBK");
            if (gbkBytes.length > 1024) {
                text = new String(gbkBytes, 0, 1024, "GBK") + "…";
            }

            // tex字段1次URL编码（百度支持1-2次，1次兼容性更好）
            String tex = URLEncoder.encode(text, StandardCharsets.UTF_8.name());

            // 构造请求参数
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("tex", tex);          // 编码后的文本
            params.add("tok", getAccessToken()); // 有效Token
            params.add("cuid", "home-mind"); // 设备标识（≤60字符）
            params.add("ctp", "1");          // 客户端类型（web端固定1）
            params.add("lan", "zh");         // 语言（固定中文）
            params.add("spd", "6");          // 语速（0-15，4=自然）
            params.add("pit", "6");          // 音调（0-15，5=默认）
            params.add("vol", "9");          // 音量（基础音库0-9，9=最大）
            params.add("per", "4");          // 发音人
            params.add("aue", "4");          // 音频格式（4=PCM_16k，客户端直接播放）

            // 4. 设置请求头（表单格式）
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            // 5. 调用百度TTS接口
            byte[] responseData = restTemplate.postForObject(TTS_URL, request, byte[].class);
            if (responseData == null || responseData.length < 1000) {
                // 解析错误信息（若返回JSON错误）
                String responseStr = new String(responseData, StandardCharsets.UTF_8);
                if (responseStr.contains("err_no")) {
                    Map<String, Object> errMap = objectMapper.readValue(responseStr, new TypeReference<>() {});
                    throw new RuntimeException("百度TTS合成失败：err_no=" + errMap.get("err_no") + ", err_msg=" + errMap.get("err_msg"));
                }
                throw new RuntimeException("百度TTS返回无效音频，长度：" + (responseData == null ? 0 : responseData.length) + " 字节");
            }

            return responseData;
        } catch (Exception e) {
            String errMsg = "百度TTS合成异常：" + e.getMessage();
            System.err.println(errMsg);
            throw new RuntimeException(errMsg, e);
        }
    }

    /**
     * 获取百度AccessToken（自动过期刷新）
     */
    private String getAccessToken() {
        // 1. Token未过期，直接返回
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return accessToken;
        }

        // 2. 重新请求Token
        String tokenUrl = String.format(
                "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id=%s&client_secret=%s",
                apiKey, secretKey
        );
        Map<String, Object> tokenResp = restTemplate.getForObject(tokenUrl, Map.class);

        // 3. 缓存Token和过期时间（提前1天刷新，避免临界过期）
        accessToken = (String) tokenResp.get("access_token");
        long expireIn = Long.parseLong(tokenResp.get("expires_in").toString());
        tokenExpireTime = System.currentTimeMillis() + expireIn * 1000 - TimeUnit.DAYS.toMillis(1);

        return accessToken;
    }
}