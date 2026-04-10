package io.hwan.atlaskb.document.service;

import io.hwan.atlaskb.common.exception.BusinessException;
import io.hwan.atlaskb.document.dto.DocumentFileSummary;
import io.hwan.atlaskb.document.entity.FileUpload;
import io.hwan.atlaskb.document.repository.ChunkInfoRepository;
import io.hwan.atlaskb.document.repository.DocumentVectorRepository;
import io.hwan.atlaskb.document.repository.FileUploadRepository;
import io.hwan.atlaskb.organization.service.OrgTagPermissionService;
import io.hwan.atlaskb.search.service.IndexingService;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

    private final FileUploadRepository fileUploadRepository;
    private final ChunkInfoRepository chunkInfoRepository;
    private final DocumentVectorRepository documentVectorRepository;
    private final MinioClient minioClient;
    private final IndexingService indexingService;
    private final OrgTagPermissionService orgTagPermissionService;
    private final String bucketName;

    public DocumentService(
            FileUploadRepository fileUploadRepository,
            ChunkInfoRepository chunkInfoRepository,
            DocumentVectorRepository documentVectorRepository,
            MinioClient minioClient,
            IndexingService indexingService,
            OrgTagPermissionService orgTagPermissionService,
            @Value("${minio.bucket-name}") String bucketName
    ) {
        this.fileUploadRepository = fileUploadRepository;
        this.chunkInfoRepository = chunkInfoRepository;
        this.documentVectorRepository = documentVectorRepository;
        this.minioClient = minioClient;
        this.indexingService = indexingService;
        this.orgTagPermissionService = orgTagPermissionService;
        this.bucketName = bucketName;
    }

    public List<DocumentFileSummary> getUserUploadedFiles(String userId) {
        return fileUploadRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentFileSummary> getAccessibleFiles(String userId) {
        List<String> accessibleOrgTags = orgTagPermissionService.resolveAccessibleOrgTags(userId);
        List<FileUpload> fileUploads = accessibleOrgTags.isEmpty()
                ? fileUploadRepository.findByUserIdOrIsPublicTrueOrderByCreatedAtDesc(userId)
                : fileUploadRepository.findAccessibleFilesOrderByCreatedAtDesc(userId, accessibleOrgTags);
        return fileUploads.stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public void deleteDocument(String fileMd5, String userId, String role) {
        FileUpload fileUpload = resolveFileUpload(fileMd5, userId, role);
        String fileOwnerId = fileUpload.getUserId();

        deleteIndexedChunks(fileMd5, fileOwnerId);
        deleteMergedObject(fileUpload.getFileName());
        documentVectorRepository.deleteByFileMd5AndUserId(fileMd5, fileOwnerId);
        chunkInfoRepository.deleteByFileMd5(fileMd5);
        fileUploadRepository.delete(fileUpload);
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

    private FileUpload resolveFileUpload(String fileMd5, String userId, String role) {
        if ("ADMIN".equals(role)) {
            return fileUploadRepository.findByFileMd5(fileMd5)
                    .orElseThrow(() -> new BusinessException(4042, "上传记录不存在"));
        }
        return fileUploadRepository.findByFileMd5AndUserId(fileMd5, userId)
                .orElseThrow(() -> new BusinessException(4042, "上传记录不存在"));
    }

    private void deleteIndexedChunks(String fileMd5, String userId) {
        try {
            indexingService.deleteFile(fileMd5, userId);
        } catch (RuntimeException ignored) {
            // 忠实保留参考项目的 best-effort 清理策略，索引删除失败不阻断元数据删除。
        }
    }

    private void deleteMergedObject(String fileName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object("merged/" + fileName)
                            .build()
            );
        } catch (Exception ignored) {
            // 忠实保留参考项目的 best-effort 清理策略，对象存储删除失败不阻断元数据删除。
        }
    }
}
