package io.hwan.atlaskb.embedding.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hwan.atlaskb.embedding.client.EmbeddingClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class EmbeddingConfig {

    @Bean
    public WebClient embeddingWebClient(
            @Value("${embedding.api.url}") String apiUrl,
            @Value("${embedding.api.key:}") String apiKey
    ) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        return WebClient.builder()
                .baseUrl(apiUrl)
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public EmbeddingClient embeddingClient(
            WebClient embeddingWebClient,
            ObjectMapper objectMapper,
            @Value("${embedding.api.model}") String model,
            @Value("${embedding.api.batch-size:10}") int batchSize,
            @Value("${embedding.api.dimension:2048}") int dimension
    ) {
        return new EmbeddingClient(embeddingWebClient, objectMapper, model, batchSize, dimension);
    }
}
