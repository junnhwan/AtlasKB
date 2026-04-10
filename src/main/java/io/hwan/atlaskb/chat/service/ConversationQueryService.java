package io.hwan.atlaskb.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hwan.atlaskb.chat.dto.ConversationMessage;
import io.hwan.atlaskb.chat.dto.ConversationSessionSummary;
import io.hwan.atlaskb.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConversationQueryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationQueryService.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public ConversationQueryService(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public List<ConversationMessage> getConversationHistory(String userId, String requestedConversationId) {
        String conversationId = resolveConversationId(userId, requestedConversationId);
        if (conversationId == null) {
            return List.of();
        }

        List<String> historyJsonList = stringRedisTemplate.opsForList().range(buildConversationMessagesKey(conversationId), 0, -1);
        if (historyJsonList == null || historyJsonList.isEmpty()) {
            return List.of();
        }

        List<ConversationMessage> history = new ArrayList<>(historyJsonList.size());
        for (String historyJson : historyJsonList) {
            try {
                history.add(objectMapper.readValue(historyJson, ConversationMessage.class));
            } catch (Exception exception) {
                log.warn("Skip invalid conversation history item: conversationId={}", conversationId, exception);
            }
        }
        return history;
    }

    public List<ConversationSessionSummary> getConversationSessions(String userId) {
        Set<String> sessionIds = stringRedisTemplate.opsForSet().members(buildConversationSetKey(userId));
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }

        List<ConversationSessionSummary> sessions = new ArrayList<>(sessionIds.size());
        for (String conversationId : sessionIds) {
            Map<Object, Object> meta = stringRedisTemplate.opsForHash().entries(buildConversationMetaKey(conversationId));
            sessions.add(new ConversationSessionSummary(
                    conversationId,
                    String.valueOf(meta.getOrDefault("name", conversationId)),
                    String.valueOf(meta.getOrDefault("createdAt", "未知"))
            ));
        }
        sessions.sort(Comparator.comparing(ConversationSessionSummary::conversationId));
        return sessions;
    }

    private String resolveConversationId(String userId, String requestedConversationId) {
        String conversationSetKey = buildConversationSetKey(userId);
        if (StringUtils.hasText(requestedConversationId)) {
            if (!Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(conversationSetKey, requestedConversationId))) {
                throw new BusinessException(4043, "会话不存在");
            }
            return requestedConversationId;
        }

        String currentConversationId = stringRedisTemplate.opsForValue().get(buildCurrentConversationKey(userId));
        if (!StringUtils.hasText(currentConversationId)) {
            return null;
        }

        if (!Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(conversationSetKey, currentConversationId))) {
            return null;
        }
        return currentConversationId;
    }

    private String buildCurrentConversationKey(String userId) {
        return "user:" + userId + ":current_conversation";
    }

    private String buildConversationSetKey(String userId) {
        return "user:" + userId + ":conversations";
    }

    private String buildConversationMessagesKey(String conversationId) {
        return "conversation:" + conversationId + ":messages";
    }

    private String buildConversationMetaKey(String conversationId) {
        return "conversation:" + conversationId + ":meta";
    }
}
