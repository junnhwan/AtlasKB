package io.hwan.atlaskb.chat.support;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class ChatEventFactory {

    public Map<String, Object> chunk(String chunkText) {
        Map<String, Object> payload = basePayload("chunk");
        payload.put("chunk", chunkText);
        payload.put("message", chunkText);
        return payload;
    }

    public Map<String, Object> completion() {
        Map<String, Object> payload = basePayload("completion");
        payload.put("status", "finished");
        payload.put("message", "响应已完成");
        return payload;
    }

    public Map<String, Object> error(String errorMessage) {
        Map<String, Object> payload = basePayload("error");
        payload.put("status", "failed");
        payload.put("message", errorMessage);
        payload.put("error", errorMessage);
        return payload;
    }

    public Map<String, Object> stop() {
        Map<String, Object> payload = basePayload("stop");
        payload.put("status", "stopped");
        payload.put("message", "响应已停止");
        return payload;
    }

    private Map<String, Object> basePayload(String type) {
        long timestamp = System.currentTimeMillis();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("timestamp", timestamp);
        payload.put("date", Instant.ofEpochMilli(timestamp).toString());
        return payload;
    }
}
