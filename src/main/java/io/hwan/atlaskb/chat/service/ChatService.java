package io.hwan.atlaskb.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hwan.atlaskb.chat.client.DeepSeekClient;
import io.hwan.atlaskb.search.dto.SearchRequest;
import io.hwan.atlaskb.search.dto.SearchResult;
import io.hwan.atlaskb.search.service.HybridSearchService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final int DEFAULT_TOP_K = 5;
    private static final int HISTORY_LIMIT = 20;
    private static final Duration CURRENT_CONVERSATION_TTL = Duration.ofDays(7);
    private static final Duration CONVERSATION_SET_TTL = Duration.ofDays(30);
    private static final Duration MESSAGE_HISTORY_TTL = Duration.ofDays(30);
    private static final int MAX_SNIPPET_LENGTH = 300;

    private final StringRedisTemplate stringRedisTemplate;
    private final HybridSearchService hybridSearchService;
    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;
    private final Map<String, Boolean> stopFlags = new ConcurrentHashMap<>();

    public ChatService(
            StringRedisTemplate stringRedisTemplate,
            HybridSearchService hybridSearchService,
            DeepSeekClient deepSeekClient,
            ObjectMapper objectMapper
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.hybridSearchService = hybridSearchService;
        this.deepSeekClient = deepSeekClient;
        this.objectMapper = objectMapper;
    }

    public void handleMessage(
            String userId,
            String username,
            String role,
            String message,
            WebSocketSession session
    ) {
        try {
            stopFlags.remove(session.getId());
            String conversationId = getOrCreateConversationId(userId);
            List<Map<String, String>> history = getConversationHistory(conversationId);
            List<SearchResult> searchResults = hybridSearchService.search(new SearchRequest(message, DEFAULT_TOP_K), userId);
            String context = buildContext(searchResults);
            StringBuilder responseBuilder = new StringBuilder();

            log.info(
                    "Process chat message: userId={}, username={}, role={}, conversationId={}, sessionId={}",
                    userId,
                    username,
                    role,
                    conversationId,
                    session.getId()
            );

            deepSeekClient.streamResponse(
                    message,
                    context,
                    history,
                    chunk -> {
                        if (isStopped(session.getId())) {
                            return;
                        }
                        responseBuilder.append(chunk);
                        sendChunk(session, chunk);
                    },
                    () -> {
                        if (isStopped(session.getId())) {
                            stopFlags.remove(session.getId());
                            return;
                        }
                        updateConversationHistory(conversationId, message, responseBuilder.toString());
                        sendCompletion(session);
                    },
                    error -> {
                        if (isStopped(session.getId())) {
                            stopFlags.remove(session.getId());
                            return;
                        }
                        sendError(session);
                    }
            );
        } catch (Exception exception) {
            log.error("Handle chat message failed", exception);
            sendError(session);
        }
    }

    public void stopResponse(String userId, WebSocketSession session) {
        stopFlags.put(session.getId(), true);
        log.info("Stop chat response: userId={}, sessionId={}", userId, session.getId());
        sendStop(session);
    }

    private String getOrCreateConversationId(String userId) {
        String currentConversationKey = buildCurrentConversationKey(userId);
        String conversationSetKey = buildConversationSetKey(userId);

        String currentConversationId = stringRedisTemplate.opsForValue().get(currentConversationKey);
        if (currentConversationId != null
                && Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(conversationSetKey, currentConversationId))) {
            return currentConversationId;
        }

        String conversationId = UUID.randomUUID().toString();
        stringRedisTemplate.opsForSet().add(conversationSetKey, conversationId);
        stringRedisTemplate.opsForValue().set(currentConversationKey, conversationId, CURRENT_CONVERSATION_TTL);
        stringRedisTemplate.expire(conversationSetKey, CONVERSATION_SET_TTL);
        stringRedisTemplate.opsForHash().putAll(buildConversationMetaKey(conversationId), Map.of(
                "createdAt", LocalDateTime.now().toString(),
                "name", "会话: " + conversationId
        ));
        return conversationId;
    }

    private List<Map<String, String>> getConversationHistory(String conversationId) {
        List<String> historyJsonList = stringRedisTemplate.opsForList().range(buildConversationMessagesKey(conversationId), 0, -1);
        if (historyJsonList == null || historyJsonList.isEmpty()) {
            return List.of();
        }

        List<Map<String, String>> history = new ArrayList<>(historyJsonList.size());
        for (String historyJson : historyJsonList) {
            try {
                history.add(objectMapper.readValue(historyJson, new TypeReference<>() {
                }));
            } catch (Exception exception) {
                log.warn("Skip invalid conversation history item: conversationId={}", conversationId, exception);
            }
        }
        return history;
    }

    private void updateConversationHistory(String conversationId, String userMessage, String assistantMessage) {
        String messageHistoryKey = buildConversationMessagesKey(conversationId);

        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            String userJson = objectMapper.writeValueAsString(buildMessage("user", userMessage, timestamp));
            String assistantJson = objectMapper.writeValueAsString(buildMessage("assistant", assistantMessage, timestamp));

            stringRedisTemplate.opsForList().rightPushAll(messageHistoryKey, userJson, assistantJson);
            stringRedisTemplate.opsForList().trim(messageHistoryKey, -HISTORY_LIMIT, -1);
            stringRedisTemplate.expire(messageHistoryKey, MESSAGE_HISTORY_TTL);
        } catch (Exception exception) {
            log.error("Persist conversation history failed: conversationId={}", conversationId, exception);
        }
    }

    private Map<String, String> buildMessage(String role, String content, String timestamp) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        message.put("timestamp", timestamp);
        return message;
    }

    private String buildContext(List<SearchResult> searchResults) {
        if (searchResults == null || searchResults.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < searchResults.size(); index++) {
            SearchResult result = searchResults.get(index);
            String snippet = result.getTextContent() == null ? "" : result.getTextContent();
            if (snippet.length() > MAX_SNIPPET_LENGTH) {
                snippet = snippet.substring(0, MAX_SNIPPET_LENGTH) + "…";
            }
            String fileName = result.getFileName() == null ? "unknown" : result.getFileName();
            builder.append("[").append(index + 1).append("] (")
                    .append(fileName)
                    .append(") ")
                    .append(snippet)
                    .append("\n");
        }
        return builder.toString();
    }

    private void sendChunk(WebSocketSession session, String chunk) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("chunk", chunk))));
        } catch (Exception exception) {
            log.error("Send chunk failed: sessionId={}", session.getId(), exception);
        }
    }

    private void sendCompletion(WebSocketSession session) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "completion",
                    "status", "finished"
            ))));
        } catch (Exception exception) {
            log.error("Send completion failed: sessionId={}", session.getId(), exception);
        }
    }

    private void sendStop(WebSocketSession session) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "stop",
                    "status", "stopped",
                    "message", "响应已停止"
            ))));
        } catch (Exception exception) {
            log.error("Send stop failed: sessionId={}", session.getId(), exception);
        }
    }

    private void sendError(WebSocketSession session) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "error", "AI服务暂时不可用，请稍后重试"
            ))));
        } catch (Exception exception) {
            log.error("Send error failed: sessionId={}", session.getId(), exception);
        }
    }

    private String buildCurrentConversationKey(String userId) {
        return "user:" + userId + ":current_conversation";
    }

    private String buildConversationSetKey(String userId) {
        return "user:" + userId + ":conversations";
    }

    private String buildConversationMetaKey(String conversationId) {
        return "conversation:" + conversationId + ":meta";
    }

    private String buildConversationMessagesKey(String conversationId) {
        return "conversation:" + conversationId + ":messages";
    }

    private boolean isStopped(String sessionId) {
        return Boolean.TRUE.equals(stopFlags.get(sessionId));
    }
}
