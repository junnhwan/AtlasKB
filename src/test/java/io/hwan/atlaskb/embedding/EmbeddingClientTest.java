package io.hwan.atlaskb.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.hwan.atlaskb.embedding.client.EmbeddingClient;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

class EmbeddingClientTest {

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
    void embedPostsCompatibleRequestAndParsesVectors() throws Exception {
        List<String> requestBodies = new ArrayList<>();

        server.createContext("/embeddings", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals("Bearer test-key", exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            assertEquals(MediaType.APPLICATION_JSON_VALUE, exchange.getRequestHeaders().getFirst(HttpHeaders.CONTENT_TYPE));

            byte[] responseBody = """
                    {
                      "data": [
                        {"embedding": [0.1, 0.2]},
                        {"embedding": [0.3, 0.4]}
                      ]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, responseBody.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBody);
            }
        });

        EmbeddingClient embeddingClient = new EmbeddingClient(
                WebClient.builder()
                        .baseUrl(serverBaseUrl())
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-key")
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                objectMapper,
                "text-embedding-v4",
                10,
                2048
        );

        List<List<Float>> vectors = embeddingClient.embed(List.of("hello", "atlas"));

        assertEquals(2, vectors.size());
        assertEquals(List.of(0.1F, 0.2F), vectors.get(0));
        assertEquals(List.of(0.3F, 0.4F), vectors.get(1));
        assertEquals(1, requestBodies.size());

        JsonNode requestJson = objectMapper.readTree(requestBodies.get(0));
        assertEquals("text-embedding-v4", requestJson.get("model").asText());
        assertEquals(2048, requestJson.get("dimension").asInt());
        assertEquals("float", requestJson.get("encoding_format").asText());
        assertEquals(List.of("hello", "atlas"), objectMapper.convertValue(requestJson.get("input"), List.class));
    }

    @Test
    void embedSplitsTextsIntoBatches() {
        List<String> requestBodies = new ArrayList<>();
        AtomicInteger responseOffset = new AtomicInteger();

        server.createContext("/embeddings", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int offset = responseOffset.getAndIncrement();
            byte[] responseBody = """
                    {
                      "data": [
                        {"embedding": [%d.0]},
                        {"embedding": [%d.0]}
                      ]
                    }
                    """.formatted(offset + 1, offset + 2).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, responseBody.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBody);
            }
        });

        EmbeddingClient embeddingClient = new EmbeddingClient(
                WebClient.builder().baseUrl(serverBaseUrl()).build(),
                objectMapper,
                "text-embedding-v4",
                2,
                1024
        );

        List<List<Float>> vectors = embeddingClient.embed(List.of("a", "b", "c", "d"));

        assertEquals(4, vectors.size());
        assertEquals(List.of(1.0F), vectors.get(0));
        assertEquals(List.of(2.0F), vectors.get(1));
        assertEquals(List.of(2.0F), vectors.get(2));
        assertEquals(List.of(3.0F), vectors.get(3));
        assertEquals(2, requestBodies.size());
    }

    private String serverBaseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }
}
