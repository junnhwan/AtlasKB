package io.hwan.atlaskb.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.hwan.atlaskb.chat.dto.ConversationSelectionResult;
import io.hwan.atlaskb.chat.service.ConversationSessionService;
import io.hwan.atlaskb.common.exception.BusinessException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class ConversationSessionServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Test
    void createConversationCreatesRedisConversationKeys() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);

        ConversationSessionService conversationSessionService = new ConversationSessionService(stringRedisTemplate);

        ConversationSelectionResult result = conversationSessionService.createConversation("1");

        ArgumentCaptor<String> conversationSetKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> conversationIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(setOperations).add(conversationSetKeyCaptor.capture(), conversationIdCaptor.capture());
        assertEquals("user:1:conversations", conversationSetKeyCaptor.getValue());
        String conversationId = conversationIdCaptor.getValue();
        assertEquals(conversationId, result.conversationId());
        verify(valueOperations).set("user:1:current_conversation", conversationId, Duration.ofDays(7));
        verify(stringRedisTemplate).expire("user:1:conversations", Duration.ofDays(30));

        ArgumentCaptor<String> metaKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> metaCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(metaKeyCaptor.capture(), metaCaptor.capture());
        assertEquals("conversation:" + conversationId + ":meta", metaKeyCaptor.getValue());
        assertEquals("会话: " + conversationId, metaCaptor.getValue().get("name"));
        assertTrue(String.valueOf(metaCaptor.getValue().get("createdAt")).length() > 10);
    }

    @Test
    void selectConversationUpdatesCurrentConversationWhenConversationBelongsToUser() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember("user:1:conversations", "conv-2")).thenReturn(true);

        ConversationSessionService conversationSessionService = new ConversationSessionService(stringRedisTemplate);

        ConversationSelectionResult result = conversationSessionService.selectConversation("1", "conv-2");

        assertEquals("conv-2", result.conversationId());
        verify(valueOperations).set("user:1:current_conversation", "conv-2", Duration.ofDays(7));
    }

    @Test
    void selectConversationRejectsConversationOutsideUserScope() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember("user:1:conversations", "conv-9")).thenReturn(false);

        ConversationSessionService conversationSessionService = new ConversationSessionService(stringRedisTemplate);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> conversationSessionService.selectConversation("1", "conv-9")
        );

        assertEquals(4043, exception.getCode());
        assertEquals("会话不存在", exception.getMessage());
    }
}
