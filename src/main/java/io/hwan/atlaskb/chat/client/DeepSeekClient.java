package io.hwan.atlaskb.chat.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hwan.atlaskb.chat.config.AiProperties;
import io.hwan.atlaskb.chat.service.PromptBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class DeepSeekClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final PromptBuilder promptBuilder;
    private final String model;
    private final AiProperties aiProperties;

    @Autowired
    public DeepSeekClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            PromptBuilder promptBuilder,
            AiProperties aiProperties,
            @Value("${deepseek.api.url}") String apiUrl,
            @Value("${deepseek.api.key:}") String apiKey,
            @Value("${deepseek.api.model}") String model
    ) {
        this(
                createWebClient(webClientBuilder, apiUrl, apiKey),
                objectMapper,
                promptBuilder,
                model,
                aiProperties
        );
    }

    private DeepSeekClient(
            WebClient webClient,
            ObjectMapper objectMapper,
            PromptBuilder promptBuilder,
            String model,
            AiProperties aiProperties
    ) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.promptBuilder = promptBuilder;
        this.model = model;
        this.aiProperties = aiProperties;
    }

    public static DeepSeekClient createForTest(
            WebClient webClient,
            ObjectMapper objectMapper,
            PromptBuilder promptBuilder,
            String model,
            AiProperties aiProperties
    ) {
        return new DeepSeekClient(webClient, objectMapper, promptBuilder, model, aiProperties);
    }

    public void streamResponse(
            String userMessage,
            String context,
            List<Map<String, String>> history,
            Consumer<String> onChunk,
            Runnable onComplete,
            Consumer<Throwable> onError
    ) {
        AtomicBoolean completed = new AtomicBoolean(false);

        webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(buildRequest(userMessage, context, history))
                .retrieve()
                .bodyToFlux(String.class)
                .doOnComplete(() -> invokeOnComplete(onComplete, completed))
                .subscribe(
                        payload -> processPayload(payload, onChunk, onComplete, completed),
                        error -> {
                            log.error("DeepSeek chat request failed", error);
                            if (onError != null) {
                                onError.accept(error);
                            }
                        }
                );
    }

    private Map<String, Object> buildRequest(
            String userMessage,
            String context,
            List<Map<String, String>> history
    ) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("messages", promptBuilder.buildMessages(userMessage, context, history));
        request.put("stream", true);

        AiProperties.Generation generation = aiProperties.getGeneration();
        if (generation.getTemperature() != null) {
            request.put("temperature", generation.getTemperature());
        }
        if (generation.getMaxTokens() != null) {
            request.put("max_tokens", generation.getMaxTokens());
        }
        if (generation.getTopP() != null) {
            request.put("top_p", generation.getTopP());
        }
        return request;
    }

    private void processPayload(
            String payload,
            Consumer<String> onChunk,
            Runnable onComplete,
            AtomicBoolean completed
    ) {
        if (payload == null || payload.isBlank()) {
            return;
        }

        String[] lines = payload.split("\\r?\\n");
        for (String line : lines) {
            String normalized = normalizeDataLine(line);
            if (normalized == null) {
                continue;
            }

            if ("[DONE]".equals(normalized)) {
                invokeOnComplete(onComplete, completed);
                continue;
            }

            try {
                JsonNode root = objectMapper.readTree(normalized);
                String content = root.path("choices")
                        .path(0)
                        .path("delta")
                        .path("content")
                        .asText("");
                if (!content.isEmpty() && onChunk != null) {
                    onChunk.accept(content);
                }
            } catch (Exception exception) {
                log.debug("Ignore non-json stream payload: {}", normalized, exception);
            }
        }
    }

    private String normalizeDataLine(String line) {
        if (line == null) {
            return null;
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("data:")) {
            return trimmed.substring(5).trim();
        }
        return trimmed;
    }

    private void invokeOnComplete(Runnable onComplete, AtomicBoolean completed) {
        if (onComplete != null && completed.compareAndSet(false, true)) {
            onComplete.run();
        }
    }

    private static WebClient createWebClient(WebClient.Builder builder, String apiUrl, String apiKey) {
        WebClient.Builder webClientBuilder = builder.baseUrl(apiUrl);
        if (apiKey != null && !apiKey.isBlank()) {
            webClientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        return webClientBuilder.build();
    }
}
