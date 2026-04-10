package io.hwan.atlaskb.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.hwan.atlaskb.chat.support.ChatEventFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatEventFactoryTest {

    @Test
    void chunkEventContainsCommonMetadataAndCompatibilityFields() {
        ChatEventFactory chatEventFactory = new ChatEventFactory();

        Map<String, Object> payload = chatEventFactory.chunk("AtlasKB");

        assertEquals("chunk", payload.get("type"));
        assertEquals("AtlasKB", payload.get("chunk"));
        assertEquals("AtlasKB", payload.get("message"));
        assertTrue(payload.get("timestamp") instanceof Long);
        assertTrue(((Long) payload.get("timestamp")) > 0L);
        assertTrue(payload.get("date") instanceof String);
    }

    @Test
    void completionErrorAndStopEventsShareCommonEnvelope() {
        ChatEventFactory chatEventFactory = new ChatEventFactory();

        Map<String, Object> completion = chatEventFactory.completion();
        Map<String, Object> error = chatEventFactory.error("失败了");
        Map<String, Object> stop = chatEventFactory.stop();

        assertEquals("completion", completion.get("type"));
        assertEquals("finished", completion.get("status"));
        assertEquals("响应已完成", completion.get("message"));

        assertEquals("error", error.get("type"));
        assertEquals("failed", error.get("status"));
        assertEquals("失败了", error.get("message"));
        assertEquals("失败了", error.get("error"));

        assertEquals("stop", stop.get("type"));
        assertEquals("stopped", stop.get("status"));
        assertEquals("响应已停止", stop.get("message"));
    }
}
