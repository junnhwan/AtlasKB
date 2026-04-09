package io.hwan.atlaskb.search.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "elasticsearch.init.enabled", havingValue = "true", matchIfMissing = true)
public class EsIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EsIndexInitializer.class);

    private final ElasticsearchClient elasticsearchClient;
    private final Resource mappingResource;
    private final String indexName;

    public EsIndexInitializer(
            ElasticsearchClient elasticsearchClient,
            @Value("classpath:es-mappings/atlas_kb_knowledge_base.json") Resource mappingResource,
            @Value("${elasticsearch.index-name}") String indexName
    ) {
        this.elasticsearchClient = elasticsearchClient;
        this.mappingResource = mappingResource;
        this.indexName = indexName;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        initialize();
    }

    public void initialize() throws Exception {
        ExistsRequest existsRequest = ExistsRequest.of(request -> request.index(indexName));
        BooleanResponse existsResponse = elasticsearchClient.indices().exists(existsRequest);
        if (existsResponse.value()) {
            log.info("Elasticsearch index already exists: {}", indexName);
            return;
        }

        createIndex();
    }

    private void createIndex() throws Exception {
        try (InputStream inputStream = mappingResource.getInputStream();
             Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            CreateIndexRequest request = CreateIndexRequest.of(builder -> builder
                    .index(indexName)
                    .withJson(reader));
            elasticsearchClient.indices().create(request);
            log.info("Created Elasticsearch index: {}", indexName);
        }
    }
}
