package io.hwan.atlaskb.storage.dto;

public record MergeRequest(
        String fileMd5,
        String fileName
) {
}
