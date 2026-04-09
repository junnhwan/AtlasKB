package io.hwan.atlaskb.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    public void handleMessage(
            String userId,
            String username,
            String role,
            String message,
            WebSocketSession session
    ) {
        log.info(
                "Receive websocket chat message: userId={}, username={}, role={}, sessionId={}, length={}",
                userId,
                username,
                role,
                session.getId(),
                message.length()
        );
    }
}
