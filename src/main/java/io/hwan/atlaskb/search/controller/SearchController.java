package io.hwan.atlaskb.search.controller;

import io.hwan.atlaskb.common.api.ApiResponse;
import io.hwan.atlaskb.common.exception.BusinessException;
import io.hwan.atlaskb.search.dto.SearchRequest;
import io.hwan.atlaskb.search.dto.SearchResult;
import io.hwan.atlaskb.search.service.HybridSearchService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final HybridSearchService hybridSearchService;

    public SearchController(HybridSearchService hybridSearchService) {
        this.hybridSearchService = hybridSearchService;
    }

    @PostMapping
    public ApiResponse<List<SearchResult>> search(
            @RequestBody SearchRequest request,
            HttpServletRequest httpServletRequest
    ) {
        Object userId = httpServletRequest.getAttribute("userId");
        if (!(userId instanceof Long)) {
            throw new BusinessException(4011, "Unauthorized");
        }

        return ApiResponse.success(hybridSearchService.search(request, String.valueOf(userId)));
    }
}
