package io.hwan.atlaskb.chat.service;

import io.hwan.atlaskb.chat.dto.ConversationSelectionResult;
import io.hwan.atlaskb.common.exception.BusinessException;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConversationSessionService {

    private static final Duration CURRENT_CONVERSATION_TTL = Duration.ofDays(7);

    private final StringRedisTemplate stringRedisTemplate;

    public ConversationSessionService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public ConversationSelectionResult selectConversation(String userId, String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            throw new BusinessException(4004, "conversationId 不能为空");
        }

        String conversationSetKey = buildConversationSetKey(userId);
        if (!Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(conversationSetKey, conversationId))) {
            throw new BusinessException(4043, "会话不存在");
        }

        stringRedisTemplate.opsForValue().set(buildCurrentConversationKey(userId), conversationId, CURRENT_CONVERSATION_TTL);
        return new ConversationSelectionResult(conversationId);
    }

    private String buildCurrentConversationKey(String userId) {
        return "user:" + userId + ":current_conversation";
    }

    private String buildConversationSetKey(String userId) {
        return "user:" + userId + ":conversations";
    }
}
