package org.github.webuild.homemind.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BaiduSpeechService {

    @Value("${baidu.speech.api-key}")
    private String apiKey;

    @Value("${baidu.speech.secret-key}")
    private String secretKey;

    @Value("${baidu.speech.recognize-url}")
    private String recognizeUrl;

    private String accessToken;  // 百度Token（缓存30天）
    private final RestTemplate restTemplate = new RestTemplate();

    // 1. 获取百度API的AccessToken（缓存复用）
    private String getAccessToken() {
        if (accessToken != null) {
            return accessToken;
        }
        // 调用百度Token接口
        String tokenUrl = String.format(
                "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id=%s&client_secret=%s",
                apiKey, secretKey
        );
        Map<String, Object> tokenResp = restTemplate.getForObject(tokenUrl, Map.class);
        accessToken = (String) tokenResp.get("access_token");
        return accessToken;
    }

    // 2. 识别PCM音频（核心方法）
    public String recognize(byte[] pcmData) {
        // PCM数据转Base64（百度API要求）
        String audioBase64 = Base64.getEncoder().encodeToString(pcmData);

        // 构建识别请求参数
        Map<String, Object> params = new HashMap<>();
        params.put("format", "pcm");       // 音频格式
        params.put("rate", 16000);         // 采样率（和客户端一致）
        params.put("channel", 1);          // 单声道
        params.put("cuid", "springboot-demo");  // 设备标识（随便填）
        params.put("token", getAccessToken());
        params.put("speech", audioBase64); // Base64编码的音频
        params.put("len", pcmData.length); // 音频字节数

        // 发送请求到百度API
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(params, headers);
        Map<String, Object> resp = restTemplate.postForObject(recognizeUrl, request, Map.class);

        // 解析识别结果
        int errNo = (Integer) resp.get("err_no");
        if (errNo != 0) {
            throw new RuntimeException("识别失败：" + resp.get("err_msg"));
        }
        List<String> result = (List<String>) resp.get("result");
        return result.get(0).trim(); // 返回识别文本
    }
}