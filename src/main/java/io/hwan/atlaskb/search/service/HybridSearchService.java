package io.hwan.atlaskb.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.hwan.atlaskb.document.entity.FileUpload;
import io.hwan.atlaskb.document.repository.FileUploadRepository;
import io.hwan.atlaskb.embedding.client.EmbeddingClient;
import io.hwan.atlaskb.organization.service.OrgTagPermissionService;
import io.hwan.atlaskb.search.dto.SearchRequest;
import io.hwan.atlaskb.search.dto.SearchResult;
import io.hwan.atlaskb.search.entity.EsDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class HybridSearchService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MIN_NUM_CANDIDATES = 20;
    private static final int NUM_CANDIDATES_MULTIPLIER = 10;

    private final ElasticsearchClient elasticsearchClient;
    private final EmbeddingClient embeddingClient;
    private final OrgTagPermissionService orgTagPermissionService;
    private final FileUploadRepository fileUploadRepository;
    private final String indexName;

    public HybridSearchService(
            ElasticsearchClient elasticsearchClient,
            EmbeddingClient embeddingClient,
            OrgTagPermissionService orgTagPermissionService,
            FileUploadRepository fileUploadRepository,
            @Value("${elasticsearch.index-name}") String indexName
    ) {
        this.elasticsearchClient = elasticsearchClient;
        this.embeddingClient = embeddingClient;
        this.orgTagPermissionService = orgTagPermissionService;
        this.fileUploadRepository = fileUploadRepository;
        this.indexName = indexName;
    }

    public List<SearchResult> search(SearchRequest request, String userId) {
        int topK = normalizeTopK(request.getTopK());
        List<String> accessibleOrgTags = orgTagPermissionService.resolveAccessibleOrgTags(userId);
        List<Float> queryVector = embedQuery(request.getQuery());

        Query permissionFilter = buildPermissionFilter(userId, accessibleOrgTags);
        co.elastic.clients.elasticsearch.core.SearchRequest esRequest = buildSearchRequest(
                request.getQuery(),
                topK,
                permissionFilter,
                queryVector
        );

        try {
            List<SearchResult> results = elasticsearchClient.search(esRequest, EsDocument.class)
                    .hits()
                    .hits()
                    .stream()
                    .map(this::toSearchResult)
                    .toList();
            attachFileNames(results);
            return results;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to execute hybrid search", exception);
        }
    }

    private co.elastic.clients.elasticsearch.core.SearchRequest buildSearchRequest(
            String queryText,
            int topK,
            Query permissionFilter,
            List<Float> queryVector
    ) {
        return co.elastic.clients.elasticsearch.core.SearchRequest.of(builder -> {
            builder.index(indexName)
                    .size(topK)
                    .query(queryBuilder -> queryBuilder.bool(boolBuilder -> boolBuilder
                            .must(mustBuilder -> mustBuilder.match(matchBuilder -> matchBuilder
                                    .field("textContent")
                                    .query(queryText)))
                            .filter(permissionFilter)));

            if (!queryVector.isEmpty()) {
                builder.knn(buildKnnQuery(topK, queryVector, permissionFilter));
            }
            return builder;
        });
    }

    private KnnQuery buildKnnQuery(int topK, List<Float> queryVector, Query permissionFilter) {
        return KnnQuery.of(builder -> builder
                .field("vector")
                .queryVector(queryVector)
                .k(topK)
                .numCandidates(Math.max(MIN_NUM_CANDIDATES, topK * NUM_CANDIDATES_MULTIPLIER))
                .filter(permissionFilter));
    }

    private Query buildPermissionFilter(String userId, List<String> accessibleOrgTags) {
        return Query.of(queryBuilder -> queryBuilder.bool(boolBuilder -> {
            boolBuilder.should(userQuery -> userQuery.term(termBuilder -> termBuilder
                    .field("userId")
                    .value(userId)));
            boolBuilder.should(publicQuery -> publicQuery.term(termBuilder -> termBuilder
                    .field("isPublic")
                    .value(true)));

            if (!accessibleOrgTags.isEmpty()) {
                boolBuilder.should(orgQuery -> orgQuery.bool(orgBoolBuilder -> {
                    orgBoolBuilder.minimumShouldMatch("1");
                    accessibleOrgTags.forEach(orgTag -> orgBoolBuilder.should(tagQuery ->
                            tagQuery.term(termBuilder -> termBuilder.field("orgTag").value(orgTag))));
                    return orgBoolBuilder;
                }));
            }

            boolBuilder.minimumShouldMatch("1");
            return boolBuilder;
        }));
    }

    private List<Float> embedQuery(String queryText) {
        if (!StringUtils.hasText(queryText)) {
            return List.of();
        }

        List<List<Float>> vectors = embeddingClient.embed(List.of(queryText));
        if (vectors.isEmpty()) {
            return List.of();
        }
        return vectors.get(0);
    }

    private int normalizeTopK(Integer requestedTopK) {
        if (requestedTopK == null || requestedTopK <= 0) {
            return DEFAULT_TOP_K;
        }
        return requestedTopK;
    }

    private SearchResult toSearchResult(Hit<EsDocument> hit) {
        EsDocument document = hit.source();
        SearchResult result = new SearchResult();
        if (document == null) {
            return result;
        }

        result.setFileMd5(document.getFileMd5());
        result.setChunkId(document.getChunkId());
        result.setTextContent(document.getTextContent());
        result.setScore(hit.score());
        result.setUserId(document.getUserId());
        result.setOrgTag(document.getOrgTag());
        result.setPublic(document.isPublic());
        return result;
    }

    private void attachFileNames(List<SearchResult> results) {
        if (results.isEmpty()) {
            return;
        }

        Set<String> fileMd5Set = results.stream()
                .map(SearchResult::getFileMd5)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (fileMd5Set.isEmpty()) {
            return;
        }

        Map<String, String> fileNameByMd5 = fileUploadRepository.findByFileMd5In(new ArrayList<>(fileMd5Set))
                .stream()
                .collect(Collectors.toMap(FileUpload::getFileMd5, FileUpload::getFileName, (left, right) -> left));

        results.forEach(result -> result.setFileName(fileNameByMd5.get(result.getFileMd5())));
    }
}
