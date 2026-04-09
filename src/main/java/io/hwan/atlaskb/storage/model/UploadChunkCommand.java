package io.hwan.atlaskb.storage.model;

import org.springframework.web.multipart.MultipartFile;

public record UploadChunkCommand(
        String fileMd5,
        int chunkIndex,
        long totalSize,
        String fileName,
        MultipartFile file,
        String orgTag,
        boolean isPublic,
        String userId
) {
}
