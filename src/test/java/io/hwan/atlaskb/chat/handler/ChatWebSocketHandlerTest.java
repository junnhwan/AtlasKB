package io.hwan.atlaskb.chat.handler;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.hwan.atlaskb.auth.service.JwtService;
import io.hwan.atlaskb.chat.service.ChatService;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketHandlerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private JwtService jwtService;

    @Mock
    private WebSocketSession webSocketSession;

    @Test
    void handleTextMessageDispatchesStopInstruction() throws Exception {
        when(webSocketSession.getAttributes()).thenReturn(new HashMap<>() {
            {
                put(ChatWebSocketHandler.ATTR_USER_ID, "1");
                put(ChatWebSocketHandler.ATTR_USERNAME, "admin");
                put(ChatWebSocketHandler.ATTR_ROLE, "ADMIN");
            }
        });

        ChatWebSocketHandler handler = new ChatWebSocketHandler(chatService, jwtService);

        handler.handleTextMessage(webSocketSession, new TextMessage("""
                {"type":"stop"}
                """));

        verify(chatService).stopResponse("1", webSocketSession);
        verify(chatService, never()).handleMessage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
