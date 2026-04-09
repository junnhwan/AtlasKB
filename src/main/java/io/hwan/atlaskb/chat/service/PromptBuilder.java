package io.hwan.atlaskb.chat.service;

import io.hwan.atlaskb.chat.config.AiProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PromptBuilder {

    private final AiProperties aiProperties;

    public PromptBuilder(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public List<Map<String, String>> buildMessages(
            String userMessage,
            String context,
            List<Map<String, String>> history
    ) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", buildSystemPrompt(context)
        ));

        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }

        messages.add(Map.of(
                "role", "user",
                "content", userMessage
        ));
        return messages;
    }

    public String buildSystemPrompt(String context) {
        AiProperties.Prompt prompt = aiProperties.getPrompt();
        StringBuilder builder = new StringBuilder();

        if (StringUtils.hasText(prompt.getRules())) {
            builder.append(prompt.getRules().trim()).append("\n\n");
        }

        builder.append(prompt.getRefStart()).append("\n");
        if (StringUtils.hasText(context)) {
            builder.append(context.trim()).append("\n");
        } else {
            builder.append(prompt.getNoResultText()).append("\n");
        }
        builder.append(prompt.getRefEnd());
        return builder.toString();
    }
}
