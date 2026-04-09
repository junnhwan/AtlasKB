package io.hwan.atlaskb.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import io.hwan.atlaskb.search.config.EsIndexInitializer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

@ExtendWith(MockitoExtension.class)
class EsIndexInitializerTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    @Mock
    private BooleanResponse existsResponse;

    @Test
    void initializeCreatesIndexWhenMissing() throws Exception {
        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(existsResponse);
        when(existsResponse.value()).thenReturn(false);

        EsIndexInitializer initializer = new EsIndexInitializer(
                elasticsearchClient,
                new ByteArrayResource("""
                        {
                          "mappings": {
                            "properties": {}
                          }
                        }
                        """.getBytes(StandardCharsets.UTF_8)),
                "atlas_kb_knowledge_base"
        );

        initializer.initialize();

        ArgumentCaptor<ExistsRequest> existsCaptor = ArgumentCaptor.forClass(ExistsRequest.class);
        verify(indicesClient).exists(existsCaptor.capture());
        assertEquals("atlas_kb_knowledge_base", existsCaptor.getValue().index().get(0));

        ArgumentCaptor<CreateIndexRequest> createCaptor = ArgumentCaptor.forClass(CreateIndexRequest.class);
        verify(indicesClient).create(createCaptor.capture());
        assertEquals("atlas_kb_knowledge_base", createCaptor.getValue().index());
    }

    @Test
    void initializeSkipsCreateWhenIndexAlreadyExists() throws Exception {
        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(existsResponse);
        when(existsResponse.value()).thenReturn(true);

        EsIndexInitializer initializer = new EsIndexInitializer(
                elasticsearchClient,
                new ByteArrayResource("{\"mappings\":{}}".getBytes(StandardCharsets.UTF_8)),
                "atlas_kb_knowledge_base"
        );

        initializer.initialize();

        verify(indicesClient, never()).create(any(CreateIndexRequest.class));
    }
}
