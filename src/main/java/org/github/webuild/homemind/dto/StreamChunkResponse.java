package org.github.webuild.homemind.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 流式响应chunk（承载AI回复片段和会话信息）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreamChunkResponse {
    public enum Type { CHUNK, END, RECOGNIZE } // CHUNK=回复片段，END=结束标记，RECOGNIZE=语音识别结果

    private Type type;          // chunk类型
    private String content;     // 内容（AI回复片段/语音识别文本）
    private String conversationId; // 会话ID
    private long timestamp;     // 时间戳
    private String error;       // 错误信息

    // 工厂方法：语音识别结果（仅返回1次）
    public static StreamChunkResponse recognize(String recognizedText, String conversationId) {
        return new StreamChunkResponse(
                Type.RECOGNIZE,
                recognizedText,
                conversationId,
                System.currentTimeMillis(),
                null
        );
    }

    // 工厂方法：AI回复片段（多次返回）
    public static StreamChunkResponse chunk(String content, String conversationId) {
        return new StreamChunkResponse(
                Type.CHUNK,
                content,
                conversationId,
                System.currentTimeMillis(),
                null
        );
    }

    // 工厂方法：流式结束（仅返回1次）
    public static StreamChunkResponse end(String conversationId) {
        return new StreamChunkResponse(
                Type.END,
                null,
                conversationId,
                System.currentTimeMillis(),
                null
        );
    }

    // 工厂方法：错误信息
    public static StreamChunkResponse error(String errorMsg, String conversationId) {
        return new StreamChunkResponse(
                Type.END,
                null,
                conversationId,
                System.currentTimeMillis(),
                errorMsg
        );
    }
}