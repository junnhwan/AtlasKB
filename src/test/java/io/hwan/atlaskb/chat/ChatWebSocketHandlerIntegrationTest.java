package io.hwan.atlaskb.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.hwan.atlaskb.auth.service.JwtService;
import io.hwan.atlaskb.chat.service.ChatService;
import io.hwan.atlaskb.user.entity.User;
import io.hwan.atlaskb.user.repository.UserRepository;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:chatwsdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.kafka.listener.auto-startup=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class ChatWebSocketHandlerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private ChatService chatService;

    private String token;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        User user = new User();
        user.setUsername("admin");
        user.setPassword(new BCryptPasswordEncoder().encode("admin123"));
        user.setRole("ADMIN");
        user.setOrgTags("default,admin");
        user.setPrimaryOrg("default");
        User savedUser = userRepository.save(user);
        token = jwtService.generateToken(savedUser);
    }

    @Test
    void websocketMessageDelegatesToChatServiceWithParsedUserInfo() {
        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();

        client.execute(
                        URI.create("ws://localhost:" + port + "/api/v1/chat/" + token),
                        session -> session.send(Mono.just(session.textMessage("你好，帮我总结一下"))).then()
                )
                .block(Duration.ofSeconds(5));

        verify(chatService, timeout(2000)).handleMessage(
                eq("1"),
                eq("admin"),
                eq("ADMIN"),
                eq((String) null),
                eq("你好，帮我总结一下"),
                any()
        );
    }
}
