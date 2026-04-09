package io.hwan.atlaskb.storage.model;

import java.util.List;

public record UploadStatusResult(
        List<Integer> uploaded,
        double progress,
        String fileName,
        String fileType
) {
}
