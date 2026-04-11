package io.hwan.atlaskb.document.dto;

public record DocumentPreviewInfo(
        String fileName,
        String content,
        Long fileSize
) {
}
