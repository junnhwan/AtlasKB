package io.hwan.atlaskb.chat.dto;

public record ConversationMessage(
        String role,
        String content,
        String timestamp
) {
}
