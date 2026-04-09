package io.hwan.atlaskb.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import io.hwan.atlaskb.document.entity.DocumentVector;
import io.hwan.atlaskb.document.repository.DocumentVectorRepository;
import io.hwan.atlaskb.embedding.client.EmbeddingClient;
import io.hwan.atlaskb.search.entity.EsDocument;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IndexingService {

    private final DocumentVectorRepository documentVectorRepository;
    private final EmbeddingClient embeddingClient;
    private final ElasticsearchClient elasticsearchClient;
    private final String indexName;
    private final String modelVersion;

    public IndexingService(
            DocumentVectorRepository documentVectorRepository,
            EmbeddingClient embeddingClient,
            ElasticsearchClient elasticsearchClient,
            @Value("${elasticsearch.index-name}") String indexName,
            @Value("${embedding.api.model}") String modelVersion
    ) {
        this.documentVectorRepository = documentVectorRepository;
        this.embeddingClient = embeddingClient;
        this.elasticsearchClient = elasticsearchClient;
        this.indexName = indexName;
        this.modelVersion = modelVersion;
    }

    public void indexFile(String fileMd5) {
        List<DocumentVector> chunks = documentVectorRepository.findByFileMd5OrderByChunkIdAsc(fileMd5);
        if (chunks.isEmpty()) {
            return;
        }

        List<String> texts = chunks.stream()
                .map(DocumentVector::getTextContent)
                .toList();
        List<List<Float>> vectors = embeddingClient.embed(texts);
        if (vectors.size() != chunks.size()) {
            throw new IllegalStateException("Embedding result size does not match chunk count");
        }

        for (int index = 0; index < chunks.size(); index++) {
            DocumentVector chunk = chunks.get(index);
            EsDocument document = toEsDocument(chunk, vectors.get(index));
            indexDocument(document);
        }
    }

    private EsDocument toEsDocument(DocumentVector chunk, List<Float> vector) {
        EsDocument document = new EsDocument();
        document.setId(buildDocumentId(chunk));
        document.setFileMd5(chunk.getFileMd5());
        document.setChunkId(chunk.getChunkId());
        document.setTextContent(chunk.getTextContent());
        document.setVector(vector);
        document.setModelVersion(modelVersion);
        document.setUserId(chunk.getUserId());
        document.setOrgTag(chunk.getOrgTag());
        document.setPublic(chunk.isPublic());
        return document;
    }

    private void indexDocument(EsDocument document) {
        try {
            IndexRequest<EsDocument> request = IndexRequest.of(builder -> builder
                    .index(indexName)
                    .id(document.getId())
                    .document(document));
            elasticsearchClient.index(request);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to index document chunk", exception);
        }
    }

    private String buildDocumentId(DocumentVector chunk) {
        return chunk.getFileMd5() + "-" + chunk.getChunkId();
    }
}
