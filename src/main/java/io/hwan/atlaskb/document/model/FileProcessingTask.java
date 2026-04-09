package io.hwan.atlaskb.document.model;

public record FileProcessingTask(
        String fileMd5,
        String objectUrl,
        String fileName,
        String userId,
        String orgTag,
        boolean publicAccessible
) {
}
