package io.hwan.atlaskb.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.hwan.atlaskb.chat.dto.ConversationSelectionResult;
import io.hwan.atlaskb.chat.service.ConversationSessionService;
import io.hwan.atlaskb.common.exception.BusinessException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
