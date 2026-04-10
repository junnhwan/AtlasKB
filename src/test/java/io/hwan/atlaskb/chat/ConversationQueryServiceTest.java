package io.hwan.atlaskb.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hwan.atlaskb.chat.dto.ConversationMessage;
import io.hwan.atlaskb.chat.dto.ConversationSessionSummary;
import io.hwan.atlaskb.chat.service.ConversationQueryService;
import io.hwan.atlaskb.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class ConversationQueryServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Test
    void getConversationHistoryReturnsMessagesOfCurrentConversation() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(valueOperations.get("user:1:current_conversation")).thenReturn("conv-1");
        when(setOperations.isMember("user:1:conversations", "conv-1")).thenReturn(true);
        when(listOperations.range("conversation:conv-1:messages", 0, -1)).thenReturn(List.of(
                objectMapper.writeValueAsString(new ConversationMessage("user", "上一轮问题", "2026-04-10T12:00:00")),
                objectMapper.writeValueAsString(new ConversationMessage("assistant", "上一轮答案", "2026-04-10T12:00:01"))
        ));

        ConversationQueryService conversationQueryService = new ConversationQueryService(stringRedisTemplate, objectMapper);

        List<ConversationMessage> history = conversationQueryService.getConversationHistory("1", null);

        assertEquals(2, history.size());
        assertEquals("user", history.get(0).role());
        assertEquals("上一轮问题", history.get(0).content());
        assertEquals("assistant", history.get(1).role());
        assertEquals("上一轮答案", history.get(1).content());
    }

    @Test
    void getConversationHistoryRejectsConversationOutsideUserScope() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember("user:1:conversations", "conv-9")).thenReturn(false);

        ConversationQueryService conversationQueryService = new ConversationQueryService(stringRedisTemplate, objectMapper);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> conversationQueryService.getConversationHistory("1", "conv-9")
        );

        assertEquals(4043, exception.getCode());
        assertEquals("会话不存在", exception.getMessage());
    }

    @Test
    void getConversationSessionsReturnsConversationSummaries() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(setOperations.members("user:1:conversations")).thenReturn(Set.of("conv-1", "conv-2"));
        when(hashOperations.entries("conversation:conv-1:meta")).thenReturn(Map.of(
                "name", "会话 1",
                "createdAt", "2026-04-10T12:00:00"
        ));
        when(hashOperations.entries("conversation:conv-2:meta")).thenReturn(Map.of(
                "name", "会话 2",
                "createdAt", "2026-04-10T12:10:00"
        ));

        ConversationQueryService conversationQueryService = new ConversationQueryService(stringRedisTemplate, objectMapper);

        List<ConversationSessionSummary> sessions = conversationQueryService.getConversationSessions("1");

        assertEquals(2, sessions.size());
        assertEquals("conv-1", sessions.get(0).conversationId());
        assertEquals("会话 1", sessions.get(0).name());
        assertEquals("2026-04-10T12:00:00", sessions.get(0).createdAt());
        assertEquals("conv-2", sessions.get(1).conversationId());
        assertEquals("会话 2", sessions.get(1).name());
        assertEquals("2026-04-10T12:10:00", sessions.get(1).createdAt());
    }
}
