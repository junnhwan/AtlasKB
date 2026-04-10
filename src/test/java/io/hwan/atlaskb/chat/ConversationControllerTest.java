package io.hwan.atlaskb.chat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.hwan.atlaskb.auth.service.JwtService;
import io.hwan.atlaskb.chat.dto.ConversationMessage;
import io.hwan.atlaskb.chat.dto.ConversationSelectionResult;
import io.hwan.atlaskb.chat.service.ConversationQueryService;
import io.hwan.atlaskb.chat.service.ConversationSessionService;
import io.hwan.atlaskb.user.entity.User;
import io.hwan.atlaskb.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:conversationdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.kafka.listener.auto-startup=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private ConversationQueryService conversationQueryService;

    @MockBean
    private ConversationSessionService conversationSessionService;

    private String token;
    private Long userId;

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
        userId = savedUser.getId();
        token = jwtService.generateToken(savedUser);
    }

    @Test
    void getConversationHistoryReturnsCurrentConversationMessages() throws Exception {
        when(conversationQueryService.getConversationHistory(userId.toString(), null)).thenReturn(List.of(
                new ConversationMessage("user", "上一轮问题", "2026-04-10T12:00:00"),
                new ConversationMessage("assistant", "上一轮答案", "2026-04-10T12:00:01")
        ));

        mockMvc.perform(get("/api/v1/users/conversation")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data[0].role").value("user"))
                .andExpect(jsonPath("$.data[0].content").value("上一轮问题"))
                .andExpect(jsonPath("$.data[0].timestamp").value("2026-04-10T12:00:00"))
                .andExpect(jsonPath("$.data[1].role").value("assistant"))
                .andExpect(jsonPath("$.data[1].content").value("上一轮答案"))
                .andExpect(jsonPath("$.data[1].timestamp").value("2026-04-10T12:00:01"));

        verify(conversationQueryService).getConversationHistory(userId.toString(), null);
    }

    @Test
    void getConversationHistoryWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/conversation"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(4010))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    void getConversationSessionsReturnsConversationSummaries() throws Exception {
        when(conversationQueryService.getConversationSessions(userId.toString())).thenReturn(List.of(
                new io.hwan.atlaskb.chat.dto.ConversationSessionSummary("conv-1", "会话 1", "2026-04-10T12:00:00"),
                new io.hwan.atlaskb.chat.dto.ConversationSessionSummary("conv-2", "会话 2", "2026-04-10T12:10:00")
        ));

        mockMvc.perform(get("/api/v1/users/conversation/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data[0].conversationId").value("conv-1"))
                .andExpect(jsonPath("$.data[0].name").value("会话 1"))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-04-10T12:00:00"))
                .andExpect(jsonPath("$.data[1].conversationId").value("conv-2"))
                .andExpect(jsonPath("$.data[1].name").value("会话 2"))
                .andExpect(jsonPath("$.data[1].createdAt").value("2026-04-10T12:10:00"));

        verify(conversationQueryService).getConversationSessions(userId.toString());
    }

    @Test
    void selectConversationSessionReturnsSelectedConversationId() throws Exception {
        when(conversationSessionService.selectConversation(userId.toString(), "conv-2"))
                .thenReturn(new ConversationSelectionResult("conv-2"));

        mockMvc.perform(post("/api/v1/users/conversation/session/select")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "conv-2"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.conversationId").value("conv-2"));

        verify(conversationSessionService).selectConversation(userId.toString(), "conv-2");
    }
}
