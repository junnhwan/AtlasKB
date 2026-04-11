package io.hwan.atlaskb.document.dto;

public record DocumentDownloadInfo(
        String fileName,
        String downloadUrl,
        Long fileSize
) {
}
