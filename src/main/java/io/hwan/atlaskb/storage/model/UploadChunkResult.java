package io.hwan.atlaskb.storage.model;

import java.util.List;

public record UploadChunkResult(
        List<Integer> uploaded,
        double progress
) {
}
