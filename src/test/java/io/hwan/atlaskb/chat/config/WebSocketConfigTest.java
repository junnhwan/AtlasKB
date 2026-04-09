package io.hwan.atlaskb.chat.config;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.hwan.atlaskb.chat.handler.ChatWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    @Mock
    private ChatWebSocketHandler chatWebSocketHandler;

    @Mock
    private WebSocketHandlerRegistry registry;

    @Mock
    private WebSocketHandlerRegistration registration;

    @Test
    void registerWebSocketHandlersUsesExpectedPath() {
        when(registry.addHandler(chatWebSocketHandler, "/api/v1/chat/{token}"))
                .thenReturn(registration);

        WebSocketConfig webSocketConfig = new WebSocketConfig(chatWebSocketHandler);

        webSocketConfig.registerWebSocketHandlers(registry);

        verify(registry).addHandler(chatWebSocketHandler, "/api/v1/chat/{token}");
        verify(registration).setAllowedOriginPatterns("*");
    }
}
