package io.hwan.atlaskb.chat.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hwan.atlaskb.auth.service.JwtService;
import io.hwan.atlaskb.chat.service.ChatService;
import io.jsonwebtoken.Claims;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_USERNAME = "username";
    public static final String ATTR_ROLE = "role";

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final ChatService chatService;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler(ChatService chatService, JwtService jwtService) {
        this.chatService = chatService;
        this.jwtService = jwtService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        SessionUser sessionUser = resolveSessionUser(session);
        log.info(
                "WebSocket chat connected: userId={}, username={}, sessionId={}",
                sessionUser.userId(),
                sessionUser.username(),
                session.getId()
        );
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (!StringUtils.hasText(message.getPayload())) {
            return;
        }

        SessionUser sessionUser = resolveSessionUser(session);
        String payload = message.getPayload();
        if (payload.trim().startsWith("{")) {
            try {
                Map<String, Object> jsonMessage = objectMapper.readValue(payload, Map.class);
                String type = jsonMessage.get("type") instanceof String value ? value : null;
                if ("stop".equals(type)) {
                    chatService.stopResponse(sessionUser.userId(), session);
                    return;
                }
                if ("message".equals(type)) {
                    Object content = jsonMessage.get("content");
                    if (content instanceof String contentText && StringUtils.hasText(contentText)) {
                        payload = contentText;
                    }
                }
            } catch (Exception exception) {
                log.debug("Ignore invalid chat json payload: {}", payload, exception);
            }
        }

        chatService.handleMessage(
                sessionUser.userId(),
                sessionUser.username(),
                sessionUser.role(),
                payload,
                session
        );
    }

    private SessionUser resolveSessionUser(WebSocketSession session) throws Exception {
        String userId = attributeValue(session, ATTR_USER_ID);
        String username = attributeValue(session, ATTR_USERNAME);
        String role = attributeValue(session, ATTR_ROLE);
        if (StringUtils.hasText(userId) && StringUtils.hasText(username) && StringUtils.hasText(role)) {
            return new SessionUser(userId, username, role);
        }

        String token = extractToken(session);
        if (!StringUtils.hasText(token)) {
            closeSilently(session, CloseStatus.BAD_DATA, "Missing websocket token");
            throw new IllegalStateException("Missing websocket token");
        }

        try {
            Claims claims = jwtService.parseToken(token);
            String resolvedUserId = claims.getSubject();
            String resolvedUsername = claims.get("username", String.class);
            String resolvedRole = claims.get("role", String.class);

            if (!StringUtils.hasText(resolvedUserId) || !StringUtils.hasText(resolvedUsername) || !StringUtils.hasText(resolvedRole)) {
                closeSilently(session, CloseStatus.NOT_ACCEPTABLE, "Invalid websocket token claims");
                throw new IllegalStateException("Invalid websocket token claims");
            }

            session.getAttributes().put(ATTR_USER_ID, resolvedUserId);
            session.getAttributes().put(ATTR_USERNAME, resolvedUsername);
            session.getAttributes().put(ATTR_ROLE, resolvedRole);
            return new SessionUser(resolvedUserId, resolvedUsername, resolvedRole);
        } catch (Exception exception) {
            closeSilently(session, CloseStatus.NOT_ACCEPTABLE, "Invalid websocket token");
            throw exception;
        }
    }

    private String extractToken(WebSocketSession session) {
        if (session.getUri() == null || !StringUtils.hasText(session.getUri().getPath())) {
            return null;
        }

        String path = session.getUri().getPath();
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == path.length() - 1) {
            return null;
        }
        return path.substring(lastSlash + 1);
    }

    private String attributeValue(WebSocketSession session, String key) {
        Object value = session.getAttributes().get(key);
        return value instanceof String stringValue ? stringValue : null;
    }

    private void closeSilently(WebSocketSession session, CloseStatus closeStatus, String reason) {
        try {
            log.warn("Close websocket session {}: {}", session.getId(), reason);
            session.close(closeStatus);
        } catch (Exception closeException) {
            log.debug("Close websocket session failed: {}", closeException.getMessage());
        }
    }

    private record SessionUser(String userId, String username, String role) {
    }
}
