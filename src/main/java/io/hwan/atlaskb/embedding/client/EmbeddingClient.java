package io.hwan.atlaskb.embedding.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;

public class EmbeddingClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String modelId;
    private final int batchSize;
    private final int dimension;

    public EmbeddingClient(
            WebClient webClient,
            ObjectMapper objectMapper,
            String modelId,
            int batchSize,
            int dimension
    ) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.modelId = modelId;
        this.batchSize = batchSize;
        this.dimension = dimension;
    }

    public List<List<Float>> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<List<Float>> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(start + batchSize, texts.size());
            vectors.addAll(embedBatch(texts.subList(start, end)));
        }
        return vectors;
    }

    private List<List<Float>> embedBatch(List<String> batchTexts) {
        String response = webClient.post()
                .uri("/embeddings")
                .bodyValue(buildRequestBody(batchTexts))
                .retrieve()
                .bodyToMono(String.class)
                .block(REQUEST_TIMEOUT);

        return parseEmbeddings(response);
    }

    private Map<String, Object> buildRequestBody(List<String> batchTexts) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelId);
        requestBody.put("input", batchTexts);
        requestBody.put("dimension", dimension);
        requestBody.put("encoding_format", "float");
        return requestBody;
    }

    private List<List<Float>> parseEmbeddings(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                throw new IllegalStateException("Embedding API response is missing data array");
            }

            List<List<Float>> vectors = new ArrayList<>(data.size());
            for (JsonNode item : data) {
                JsonNode embedding = item.get("embedding");
                if (embedding == null || !embedding.isArray()) {
                    continue;
                }

                List<Float> vector = new ArrayList<>(embedding.size());
                for (JsonNode value : embedding) {
                    vector.add((float) value.asDouble());
                }
                vectors.add(vector);
            }
            return vectors;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse embedding response", exception);
        }
    }
}
