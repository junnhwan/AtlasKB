package io.hwan.atlaskb.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import io.hwan.atlaskb.document.entity.FileUpload;
import io.hwan.atlaskb.document.repository.FileUploadRepository;
import io.hwan.atlaskb.embedding.client.EmbeddingClient;
import io.hwan.atlaskb.organization.service.OrgTagPermissionService;
import io.hwan.atlaskb.search.dto.SearchRequest;
import io.hwan.atlaskb.search.dto.SearchResult;
import io.hwan.atlaskb.search.entity.EsDocument;
import io.hwan.atlaskb.search.service.HybridSearchService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HybridSearchServiceTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private OrgTagPermissionService orgTagPermissionService;

    @Mock
    private FileUploadRepository fileUploadRepository;

    @Mock
    private SearchResponse<EsDocument> searchResponse;

    @Mock
    private HitsMetadata<EsDocument> hitsMetadata;

    @Mock
    private Hit<EsDocument> hit;

    @Test
    void searchBuildsHybridPermissionAwareRequestAndMapsHits() throws Exception {
        when(orgTagPermissionService.resolveAccessibleOrgTags("1")).thenReturn(List.of("default", "sales"));
        when(embeddingClient.embed(List.of("atlas"))).thenReturn(List.of(List.of(0.1F, 0.2F)));
        when(elasticsearchClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(EsDocument.class)))
                .thenReturn(searchResponse);
        when(searchResponse.hits()).thenReturn(hitsMetadata);
        when(hitsMetadata.hits()).thenReturn(List.of(hit));
        when(hit.score()).thenReturn(0.91D);
        when(hit.source()).thenReturn(createEsDocument());
        when(fileUploadRepository.findByFileMd5In(List.of("abc123"))).thenReturn(List.of(createFileUpload()));

        HybridSearchService searchService = new HybridSearchService(
                elasticsearchClient,
                embeddingClient,
                orgTagPermissionService,
                fileUploadRepository,
                "atlas_kb_knowledge_base"
        );

        List<SearchResult> results = searchService.search(new SearchRequest("atlas", 4), "1");

        ArgumentCaptor<co.elastic.clients.elasticsearch.core.SearchRequest> requestCaptor =
                ArgumentCaptor.forClass(co.elastic.clients.elasticsearch.core.SearchRequest.class);
        verify(elasticsearchClient).search(requestCaptor.capture(), eq(EsDocument.class));

        co.elastic.clients.elasticsearch.core.SearchRequest request = requestCaptor.getValue();
        assertEquals("atlas_kb_knowledge_base", request.index().get(0));
        assertEquals(4, request.size());
        assertEquals(1, request.knn().size());
        assertEquals("vector", request.knn().get(0).field());
        assertEquals(List.of(0.1F, 0.2F), request.knn().get(0).queryVector());
        assertEquals(4L, request.knn().get(0).k());
        assertEquals(40L, request.knn().get(0).numCandidates());

        assertEquals(1, request.query().bool().must().size());
        assertEquals("textContent", request.query().bool().must().get(0).match().field());
        assertEquals("atlas", request.query().bool().must().get(0).match().query().stringValue());
        assertEquals(1, request.query().bool().filter().size());
        assertEquals("1", request.query().bool().filter().get(0).bool().minimumShouldMatch());
        assertEquals(3, request.query().bool().filter().get(0).bool().should().size());

        assertEquals(1, results.size());
        assertEquals("abc123", results.get(0).getFileMd5());
        assertEquals(2, results.get(0).getChunkId());
        assertEquals("AtlasKB search result", results.get(0).getTextContent());
        assertEquals(0.91D, results.get(0).getScore());
        assertEquals("1", results.get(0).getUserId());
        assertEquals("default", results.get(0).getOrgTag());
        assertEquals(true, results.get(0).isPublic());
        assertEquals("manual.pdf", results.get(0).getFileName());
    }

    @Test
    void searchFallsBackToTextOnlyWhenEmbeddingUnavailable() throws Exception {
        when(orgTagPermissionService.resolveAccessibleOrgTags("9")).thenReturn(List.of());
        when(embeddingClient.embed(List.of("atlas"))).thenReturn(List.of());
        when(elasticsearchClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(EsDocument.class)))
                .thenReturn(searchResponse);
        when(searchResponse.hits()).thenReturn(hitsMetadata);
        when(hitsMetadata.hits()).thenReturn(List.of());

        HybridSearchService searchService = new HybridSearchService(
                elasticsearchClient,
                embeddingClient,
                orgTagPermissionService,
                fileUploadRepository,
                "atlas_kb_knowledge_base"
        );

        List<SearchResult> results = searchService.search(new SearchRequest("atlas", null), "9");

        ArgumentCaptor<co.elastic.clients.elasticsearch.core.SearchRequest> requestCaptor =
                ArgumentCaptor.forClass(co.elastic.clients.elasticsearch.core.SearchRequest.class);
        verify(elasticsearchClient).search(requestCaptor.capture(), eq(EsDocument.class));

        co.elastic.clients.elasticsearch.core.SearchRequest request = requestCaptor.getValue();
        assertEquals(5, request.size());
        assertEquals(0, request.knn().size());
        assertEquals(2, request.query().bool().filter().get(0).bool().should().size());
        assertEquals(0, results.size());
    }

    private EsDocument createEsDocument() {
        EsDocument document = new EsDocument();
        document.setId("abc123-2");
        document.setFileMd5("abc123");
        document.setChunkId(2);
        document.setTextContent("AtlasKB search result");
        document.setVector(List.of(0.1F, 0.2F));
        document.setModelVersion("text-embedding-v4");
        document.setUserId("1");
        document.setOrgTag("default");
        document.setPublic(true);
        return document;
    }

    private FileUpload createFileUpload() {
        FileUpload fileUpload = new FileUpload();
        fileUpload.setFileMd5("abc123");
        fileUpload.setFileName("manual.pdf");
        fileUpload.setUserId("1");
        fileUpload.setStatus(2);
        fileUpload.setTotalSize(1L);
        return fileUpload;
    }
}
