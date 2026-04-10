package io.hwan.atlaskb.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import io.hwan.atlaskb.document.entity.DocumentVector;
import io.hwan.atlaskb.document.repository.DocumentVectorRepository;
import io.hwan.atlaskb.embedding.client.EmbeddingClient;
import io.hwan.atlaskb.search.entity.EsDocument;
import io.hwan.atlaskb.search.service.IndexingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IndexingServiceTest {

    @Mock
    private DocumentVectorRepository documentVectorRepository;

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Test
    void indexFileEmbedsStoredChunksAndWritesDocumentsToElasticsearch() throws Exception {
        DocumentVector chunkOne = createVector("abc123", 1, "chunk one", "1", "default", true);
        DocumentVector chunkTwo = createVector("abc123", 2, "chunk two", "1", "default", true);

        when(documentVectorRepository.findByFileMd5AndUserIdOrderByChunkIdAsc("abc123", "1"))
                .thenReturn(List.of(chunkOne, chunkTwo));
        when(embeddingClient.embed(List.of("chunk one", "chunk two")))
                .thenReturn(List.of(List.of(0.1F, 0.2F), List.of(0.3F, 0.4F)));

        IndexingService indexingService = new IndexingService(
                documentVectorRepository,
                embeddingClient,
                elasticsearchClient,
                "atlas_kb_knowledge_base",
                "text-embedding-v4"
        );

        indexingService.indexFile("abc123", "1");

        verify(documentVectorRepository).findByFileMd5AndUserIdOrderByChunkIdAsc("abc123", "1");
        verify(embeddingClient).embed(List.of("chunk one", "chunk two"));

        ArgumentCaptor<IndexRequest<EsDocument>> requestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
        verify(elasticsearchClient, times(2)).index(requestCaptor.capture());
        List<IndexRequest<EsDocument>> requests = requestCaptor.getAllValues();

        assertEquals("atlas_kb_knowledge_base", requests.get(0).index());
        assertEquals("abc123-1", requests.get(0).id());
        assertEquals("abc123", requests.get(0).document().getFileMd5());
        assertEquals(1, requests.get(0).document().getChunkId());
        assertEquals("chunk one", requests.get(0).document().getTextContent());
        assertEquals(List.of(0.1F, 0.2F), requests.get(0).document().getVector());
        assertEquals("text-embedding-v4", requests.get(0).document().getModelVersion());
        assertEquals("1", requests.get(0).document().getUserId());
        assertEquals("default", requests.get(0).document().getOrgTag());
        assertEquals(true, requests.get(0).document().isPublic());

        assertEquals("atlas_kb_knowledge_base", requests.get(1).index());
        assertEquals("abc123-2", requests.get(1).id());
        assertEquals("abc123", requests.get(1).document().getFileMd5());
        assertEquals(2, requests.get(1).document().getChunkId());
        assertEquals("chunk two", requests.get(1).document().getTextContent());
        assertEquals(List.of(0.3F, 0.4F), requests.get(1).document().getVector());
    }

    @Test
    void indexFileSkipsEmbeddingWhenNoStoredChunks() throws Exception {
        when(documentVectorRepository.findByFileMd5AndUserIdOrderByChunkIdAsc("empty-file", "9"))
                .thenReturn(List.of());

        IndexingService indexingService = new IndexingService(
                documentVectorRepository,
                embeddingClient,
                elasticsearchClient,
                "atlas_kb_knowledge_base",
                "text-embedding-v4"
        );

        indexingService.indexFile("empty-file", "9");

        verify(documentVectorRepository).findByFileMd5AndUserIdOrderByChunkIdAsc("empty-file", "9");
        verify(embeddingClient, never()).embed(any());
        verify(elasticsearchClient, never()).index(any(IndexRequest.class));
    }

    @Test
    void deleteFileRemovesIndexedChunksFromElasticsearch() throws Exception {
        IndexingService indexingService = new IndexingService(
                documentVectorRepository,
                embeddingClient,
                elasticsearchClient,
                "atlas_kb_knowledge_base",
                "text-embedding-v4"
        );

        indexingService.deleteFile("abc123", "1");

        verify(elasticsearchClient).deleteByQuery(any(DeleteByQueryRequest.class));
    }

    private DocumentVector createVector(
            String fileMd5,
            int chunkId,
            String textContent,
            String userId,
            String orgTag,
            boolean publicAccessible
    ) {
        DocumentVector vector = new DocumentVector();
        vector.setFileMd5(fileMd5);
        vector.setChunkId(chunkId);
        vector.setTextContent(textContent);
        vector.setUserId(userId);
        vector.setOrgTag(orgTag);
        vector.setPublic(publicAccessible);
        return vector;
    }
}
