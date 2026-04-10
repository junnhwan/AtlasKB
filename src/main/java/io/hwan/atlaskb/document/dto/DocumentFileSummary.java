package io.hwan.atlaskb.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DocumentFileSummary(
        String fileMd5,
        String fileName,
        Long totalSize,
        Integer status,
        String userId,
        String orgTag,
        @JsonProperty("public")
        boolean isPublic,
        String createdAt,
        String mergedAt
) {
}
