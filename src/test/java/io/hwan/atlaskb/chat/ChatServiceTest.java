package io.hwan.atlaskb.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hwan.atlaskb.chat.client.DeepSeekClient;
import io.hwan.atlaskb.chat.service.ChatService;
import io.hwan.atlaskb.search.dto.SearchRequest;
import io.hwan.atlaskb.search.dto.SearchResult;
import io.hwan.atlaskb.search.service.HybridSearchService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

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

    @Mock
    private HybridSearchService hybridSearchService;

    @Mock
    private DeepSeekClient deepSeekClient;

    @Mock
    private WebSocketSession webSocketSession;

    @Test
    void handleMessageCreatesConversationStreamsResponseAndPersistsHistory() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(valueOperations.get("user:1:current_conversation")).thenReturn(null);
        when(webSocketSession.getId()).thenReturn("session-1");

        SearchResult searchResult = new SearchResult();
        searchResult.setFileName("manual.pdf");
        searchResult.setTextContent("AtlasKB 主链路说明");
        when(hybridSearchService.search(any(SearchRequest.class), eq("1"))).thenReturn(List.of(searchResult));

        doAnswer(invocation -> {
            ((java.util.function.Consumer<String>) invocation.getArgument(3)).accept("先给结论");
            ((java.util.function.Consumer<String>) invocation.getArgument(3)).accept("，再给论据");
            ((Runnable) invocation.getArgument(4)).run();
            return null;
        }).when(deepSeekClient).streamResponse(eq("帮我总结一下"), any(), any(), any(), any(), any());

        ChatService chatService = new ChatService(
                stringRedisTemplate,
                hybridSearchService,
                deepSeekClient,
                objectMapper
        );

        chatService.handleMessage("1", "admin", "ADMIN", "帮我总结一下", webSocketSession);

        ArgumentCaptor<SearchRequest> searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(hybridSearchService).search(searchRequestCaptor.capture(), eq("1"));
        assertEquals("帮我总结一下", searchRequestCaptor.getValue().getQuery());
        assertEquals(5, searchRequestCaptor.getValue().getTopK());

        ArgumentCaptor<String> conversationIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(setOperations).add(eq("user:1:conversations"), conversationIdCaptor.capture());
        String conversationId = conversationIdCaptor.getValue();
        verify(valueOperations).set("user:1:current_conversation", conversationId, Duration.ofDays(7));
        verify(stringRedisTemplate).expire("user:1:conversations", Duration.ofDays(30));
        verify(hashOperations).putAll(eq("conversation:" + conversationId + ":meta"), any(Map.class));

        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(deepSeekClient).streamResponse(eq("帮我总结一下"), contextCaptor.capture(), eq(List.of()), any(), any(), any());
        assertTrue(contextCaptor.getValue().contains("manual.pdf"));
        assertTrue(contextCaptor.getValue().contains("AtlasKB 主链路说明"));

        ArgumentCaptor<TextMessage> outgoingCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession, org.mockito.Mockito.times(3)).sendMessage(outgoingCaptor.capture());
        List<TextMessage> sentMessages = outgoingCaptor.getAllValues();
        assertTrue(sentMessages.get(0).getPayload().contains("\"type\":\"chunk\""));
        assertTrue(sentMessages.get(0).getPayload().contains("\"chunk\":\"先给结论\""));
        assertTrue(sentMessages.get(0).getPayload().contains("\"message\":\"先给结论\""));
        assertTrue(sentMessages.get(0).getPayload().contains("\"timestamp\":"));
        assertTrue(sentMessages.get(1).getPayload().contains("\"type\":\"chunk\""));
        assertTrue(sentMessages.get(1).getPayload().contains("\"chunk\":\"，再给论据\""));
        assertTrue(sentMessages.get(2).getPayload().contains("\"type\":\"completion\""));
        assertTrue(sentMessages.get(2).getPayload().contains("\"status\":\"finished\""));
        assertTrue(sentMessages.get(2).getPayload().contains("响应已完成"));
        assertTrue(sentMessages.get(2).getPayload().contains("\"timestamp\":"));

        ArgumentCaptor<String> persistedKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userJsonCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> assistantJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(listOperations).rightPushAll(
                persistedKeyCaptor.capture(),
                userJsonCaptor.capture(),
                assistantJsonCaptor.capture()
        );
        assertEquals("conversation:" + conversationId + ":messages", persistedKeyCaptor.getValue());

        Map<String, String> persistedUser = objectMapper.readValue(userJsonCaptor.getValue(), new TypeReference<>() {
        });
        Map<String, String> persistedAssistant = objectMapper.readValue(assistantJsonCaptor.getValue(), new TypeReference<>() {
        });
        assertEquals("user", persistedUser.get("role"));
        assertEquals("帮我总结一下", persistedUser.get("content"));
        assertEquals("assistant", persistedAssistant.get("role"));
        assertEquals("先给结论，再给论据", persistedAssistant.get("content"));
        verify(listOperations).trim("conversation:" + conversationId + ":messages", -20, -1);
        verify(stringRedisTemplate).expire("conversation:" + conversationId + ":messages", Duration.ofDays(30));
    }

    @Test
    void handleMessageLoadsExistingHistoryAndSendsErrorWhenClientFails() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(valueOperations.get("user:2:current_conversation")).thenReturn("conv-2");
        when(setOperations.isMember("user:2:conversations", "conv-2")).thenReturn(true);
        when(listOperations.range("conversation:conv-2:messages", 0, -1)).thenReturn(List.of(
                objectMapper.writeValueAsString(Map.of("role", "user", "content", "上一轮问题")),
                objectMapper.writeValueAsString(Map.of("role", "assistant", "content", "上一轮答案"))
        ));
        when(webSocketSession.getId()).thenReturn("session-2");
        when(hybridSearchService.search(any(SearchRequest.class), eq("2"))).thenReturn(List.of());

        doAnswer(invocation -> {
            ((java.util.function.Consumer<Throwable>) invocation.getArgument(5))
                    .accept(new IllegalStateException("boom"));
            return null;
        }).when(deepSeekClient).streamResponse(eq("继续追问"), any(), any(), any(), any(), any());

        ChatService chatService = new ChatService(
                stringRedisTemplate,
                hybridSearchService,
                deepSeekClient,
                objectMapper
        );

        chatService.handleMessage("2", "guest", "USER", "继续追问", webSocketSession);

        ArgumentCaptor<List<Map<String, String>>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(deepSeekClient).streamResponse(eq("继续追问"), eq(""), historyCaptor.capture(), any(), any(), any());
        assertEquals(2, historyCaptor.getValue().size());
        assertEquals("上一轮问题", historyCaptor.getValue().get(0).get("content"));
        assertEquals("上一轮答案", historyCaptor.getValue().get(1).get("content"));

        ArgumentCaptor<TextMessage> outgoingCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(outgoingCaptor.capture());
        assertTrue(outgoingCaptor.getValue().getPayload().contains("\"type\":\"error\""));
        assertTrue(outgoingCaptor.getValue().getPayload().contains("AI服务暂时不可用"));
        assertTrue(outgoingCaptor.getValue().getPayload().contains("\"timestamp\":"));
        verify(listOperations, never()).rightPushAll(eq("conversation:conv-2:messages"), any(), any());
    }

    @Test
    void stopResponseSkipsRemainingChunksCompletionAndHistoryPersistence() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(valueOperations.get("user:3:current_conversation")).thenReturn(null);
        when(webSocketSession.getId()).thenReturn("session-3");
        when(hybridSearchService.search(any(SearchRequest.class), eq("3"))).thenReturn(List.of());

        ChatService chatService = new ChatService(
                stringRedisTemplate,
                hybridSearchService,
                deepSeekClient,
                objectMapper
        );

        doAnswer(invocation -> {
            ((java.util.function.Consumer<String>) invocation.getArgument(3)).accept("第一段");
            chatService.stopResponse("3", webSocketSession);
            ((java.util.function.Consumer<String>) invocation.getArgument(3)).accept("第二段");
            ((Runnable) invocation.getArgument(4)).run();
            return null;
        }).when(deepSeekClient).streamResponse(eq("停止测试"), any(), any(), any(), any(), any());

        chatService.handleMessage("3", "tester", "USER", "停止测试", webSocketSession);

        ArgumentCaptor<TextMessage> outgoingCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession, org.mockito.Mockito.times(2)).sendMessage(outgoingCaptor.capture());
        List<TextMessage> sentMessages = outgoingCaptor.getAllValues();
        assertTrue(sentMessages.get(0).getPayload().contains("\"type\":\"chunk\""));
        assertTrue(sentMessages.get(0).getPayload().contains("\"chunk\":\"第一段\""));
        assertTrue(sentMessages.get(1).getPayload().contains("\"type\":\"stop\""));
        assertTrue(sentMessages.get(1).getPayload().contains("\"status\":\"stopped\""));
        assertTrue(sentMessages.get(1).getPayload().contains("响应已停止"));
        assertTrue(sentMessages.get(1).getPayload().contains("\"timestamp\":"));
        verify(listOperations, never()).rightPushAll(any(), any(), any());
        verify(listOperations, never()).trim(anyString(), anyLong(), anyLong());
    }
}
