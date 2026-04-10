package io.hwan.atlaskb.chat.dto;

public record ConversationSessionSummary(
        String conversationId,
        String name,
        String createdAt
) {
}
