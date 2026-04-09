package io.hwan.atlaskb.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.hwan.atlaskb.chat.client.DeepSeekClient;
import io.hwan.atlaskb.chat.config.AiProperties;
import io.hwan.atlaskb.chat.service.PromptBuilder;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

class DeepSeekClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void streamResponsePostsChatRequestAndEmitsContentChunks() throws Exception {
        List<String> requestBodies = new CopyOnWriteArrayList<>();

        server.createContext("/chat/completions", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals("Bearer test-key", exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            assertEquals(MediaType.APPLICATION_JSON_VALUE, exchange.getRequestHeaders().getFirst(HttpHeaders.CONTENT_TYPE));

            byte[] responseBody = """
                    data: {"choices":[{"delta":{"content":"你好"}}]}

                    data: {"choices":[{"delta":{"content":"，AtlasKB"}}]}

                    data: [DONE]

                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE);
            exchange.sendResponseHeaders(200, responseBody.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBody);
            }
        });

        AiProperties aiProperties = createAiProperties();
        DeepSeekClient deepSeekClient = createClient(aiProperties);
        List<String> chunks = new CopyOnWriteArrayList<>();
        AtomicInteger completionCount = new AtomicInteger();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        deepSeekClient.streamResponse(
                "这一轮提问",
                "[1] (atlas.pdf) AtlasKB 主链路说明",
                List.of(Map.of("role", "user", "content", "上一轮提问")),
                chunks::add,
                () -> {
                    completionCount.incrementAndGet();
                    latch.countDown();
                },
                error -> {
                    errorRef.set(error);
                    latch.countDown();
                }
        );

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(errorRef.get());
        assertEquals(List.of("你好", "，AtlasKB"), chunks);
        assertEquals(1, completionCount.get());
        assertEquals(1, requestBodies.size());

        JsonNode requestJson = objectMapper.readTree(requestBodies.get(0));
        assertEquals("deepseek-chat", requestJson.get("model").asText());
        assertEquals(true, requestJson.get("stream").asBoolean());
        assertEquals(0.3D, requestJson.get("temperature").asDouble());
        assertEquals(2000, requestJson.get("max_tokens").asInt());
        assertEquals(0.9D, requestJson.get("top_p").asDouble());
        assertEquals(3, requestJson.get("messages").size());
        assertEquals("system", requestJson.get("messages").get(0).get("role").asText());
        assertTrue(requestJson.get("messages").get(0).get("content").asText().contains("<<REF>>"));
        assertTrue(requestJson.get("messages").get(0).get("content").asText().contains("atlas.pdf"));
        assertEquals("上一轮提问", requestJson.get("messages").get(1).get("content").asText());
        assertEquals("这一轮提问", requestJson.get("messages").get(2).get("content").asText());
    }

    @Test
    void streamResponseInvokesErrorCallbackWhenApiReturnsFailure() throws Exception {
        server.createContext("/chat/completions", exchange -> {
            byte[] responseBody = "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(500, responseBody.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBody);
            }
        });

        AiProperties aiProperties = createAiProperties();
        DeepSeekClient deepSeekClient = createClient(aiProperties);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicInteger completionCount = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);

        deepSeekClient.streamResponse(
                "这一轮提问",
                "",
                List.of(),
                chunk -> {
                },
                () -> {
                    completionCount.incrementAndGet();
                    latch.countDown();
                },
                error -> {
                    errorRef.set(error);
                    latch.countDown();
                }
        );

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(errorRef.get());
        assertEquals(0, completionCount.get());
    }

    private DeepSeekClient createClient(AiProperties aiProperties) {
        return DeepSeekClient.createForTest(
                WebClient.builder()
                        .baseUrl(serverBaseUrl())
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-key")
                        .build(),
                objectMapper,
                new PromptBuilder(aiProperties),
                "deepseek-chat",
                aiProperties
        );
    }

    private AiProperties createAiProperties() {
        AiProperties aiProperties = new AiProperties();
        aiProperties.getPrompt().setRules("""
                你是 AtlasKB 知识助手，须遵守：
                1. 仅用简体中文作答。
                2. 回答需先给结论，再给论据。
                3. 如引用参考信息，请在句末补充文件名。
                """);
        aiProperties.getPrompt().setRefStart("<<REF>>");
        aiProperties.getPrompt().setRefEnd("<<END>>");
        aiProperties.getPrompt().setNoResultText("（本轮无检索结果）");
        aiProperties.getGeneration().setTemperature(0.3);
        aiProperties.getGeneration().setMaxTokens(2000);
        aiProperties.getGeneration().setTopP(0.9);
        return aiProperties;
    }

    private String serverBaseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }
}
