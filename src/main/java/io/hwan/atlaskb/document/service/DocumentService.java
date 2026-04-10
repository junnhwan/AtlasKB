package io.hwan.atlaskb.document.service;

import io.hwan.atlaskb.document.dto.DocumentFileSummary;
import io.hwan.atlaskb.document.entity.FileUpload;
import io.hwan.atlaskb.document.repository.FileUploadRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {

    private final FileUploadRepository fileUploadRepository;

    public DocumentService(FileUploadRepository fileUploadRepository) {
        this.fileUploadRepository = fileUploadRepository;
    }

    public List<DocumentFileSummary> getUserUploadedFiles(String userId) {
        return fileUploadRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    private DocumentFileSummary toSummary(FileUpload fileUpload) {
        return new DocumentFileSummary(
                fileUpload.getFileMd5(),
                fileUpload.getFileName(),
                fileUpload.getTotalSize(),
                fileUpload.getStatus(),
                fileUpload.getUserId(),
                fileUpload.getOrgTag(),
                fileUpload.isPublic(),
                fileUpload.getCreatedAt() == null ? null : fileUpload.getCreatedAt().toString(),
                fileUpload.getMergedAt() == null ? null : fileUpload.getMergedAt().toString()
        );
    }
}
