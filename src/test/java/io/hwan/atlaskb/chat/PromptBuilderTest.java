package io.hwan.atlaskb.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.hwan.atlaskb.chat.config.AiProperties;
import io.hwan.atlaskb.chat.service.PromptBuilder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptBuilderTest {

    @Test
    void buildMessagesIncludesSystemPromptHistoryAndCurrentUserMessage() {
        AiProperties aiProperties = createAiProperties();
        PromptBuilder promptBuilder = new PromptBuilder(aiProperties);

        List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "上一轮提问"),
                Map.of("role", "assistant", "content", "上一轮回答")
        );

        List<Map<String, String>> messages = promptBuilder.buildMessages(
                "这一轮提问",
                "[1] (atlas.pdf) AtlasKB 主链路说明",
                history
        );

        assertEquals(4, messages.size());
        assertEquals("system", messages.get(0).get("role"));
        assertTrue(messages.get(0).get("content").contains("1. 仅用简体中文作答。"));
        assertTrue(messages.get(0).get("content").contains("<<REF>>"));
        assertTrue(messages.get(0).get("content").contains("[1] (atlas.pdf) AtlasKB 主链路说明"));
        assertTrue(messages.get(0).get("content").contains("<<END>>"));
        assertEquals(history.get(0), messages.get(1));
        assertEquals(history.get(1), messages.get(2));
        assertEquals(Map.of("role", "user", "content", "这一轮提问"), messages.get(3));
    }

    @Test
    void buildMessagesUsesNoResultPlaceholderWhenContextIsBlank() {
        AiProperties aiProperties = createAiProperties();
        PromptBuilder promptBuilder = new PromptBuilder(aiProperties);

        List<Map<String, String>> messages = promptBuilder.buildMessages(
                "你是谁",
                "   ",
                List.of()
        );

        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).get("role"));
        assertTrue(messages.get(0).get("content").contains("（本轮无检索结果）"));
        assertTrue(messages.get(0).get("content").contains("<<REF>>"));
        assertTrue(messages.get(0).get("content").contains("<<END>>"));
        assertEquals(Map.of("role", "user", "content", "你是谁"), messages.get(1));
    }

    private AiProperties createAiProperties() {
        AiProperties aiProperties = new AiProperties();
        aiProperties.getPrompt().setRules("""
                1. 仅用简体中文作答。
                2. 回答需先给结论，再给论据。
                3. 如引用参考信息，请在句末补充文件名。
                """);
        aiProperties.getPrompt().setRefStart("<<REF>>");
        aiProperties.getPrompt().setRefEnd("<<END>>");
        aiProperties.getPrompt().setNoResultText("（本轮无检索结果）");
        return aiProperties;
    }
}
