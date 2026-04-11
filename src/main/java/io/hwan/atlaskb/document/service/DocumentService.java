package io.hwan.atlaskb.document.service;

import io.hwan.atlaskb.common.exception.BusinessException;
import io.hwan.atlaskb.document.dto.DocumentDownloadInfo;
import io.hwan.atlaskb.document.dto.DocumentFileSummary;
import io.hwan.atlaskb.document.dto.DocumentPreviewInfo;
import io.hwan.atlaskb.document.entity.FileUpload;
import io.hwan.atlaskb.document.repository.ChunkInfoRepository;
import io.hwan.atlaskb.document.repository.DocumentVectorRepository;
import io.hwan.atlaskb.document.repository.FileUploadRepository;
import io.hwan.atlaskb.organization.service.OrgTagPermissionService;
import io.hwan.atlaskb.search.service.IndexingService;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

    private static final int PREVIEW_MAX_BYTES = 10 * 1024;

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
        return getAccessibleFileEntities(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentDownloadInfo getDownloadInfo(String fileName, String userId) {
        FileUpload fileUpload = resolveReadableFile(fileName, userId);
        return new DocumentDownloadInfo(
                fileUpload.getFileName(),
                buildPresignedDownloadUrl(fileUpload.getFileName()),
                fileUpload.getTotalSize()
        );
    }

    @Transactional(readOnly = true)
    public DocumentPreviewInfo getPreviewInfo(String fileName, String userId) {
        FileUpload fileUpload = resolveReadableFile(fileName, userId);
        return new DocumentPreviewInfo(
                fileUpload.getFileName(),
                buildPreviewContent(fileUpload),
                fileUpload.getTotalSize()
        );
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

    private List<FileUpload> getAccessibleFileEntities(String userId) {
        List<String> accessibleOrgTags = orgTagPermissionService.resolveAccessibleOrgTags(userId);
        return accessibleOrgTags.isEmpty()
                ? fileUploadRepository.findByUserIdOrIsPublicTrueOrderByCreatedAtDesc(userId)
                : fileUploadRepository.findAccessibleFilesOrderByCreatedAtDesc(userId, accessibleOrgTags);
    }

    private FileUpload resolveReadableFile(String fileName, String userId) {
        if (userId == null) {
            return fileUploadRepository.findByFileNameAndIsPublicTrue(fileName)
                    .orElseThrow(() -> new BusinessException(4042, "文件不存在或需要登录访问"));
        }
        return getAccessibleFileEntities(userId).stream()
                .filter(fileUpload -> fileName.equals(fileUpload.getFileName()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(4042, "文件不存在或无权限访问"));
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

    private String buildPresignedDownloadUrl(String fileName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object("merged/" + fileName)
                            .expiry(3600)
                            .build()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成下载链接", exception);
        }
    }

    private String buildPreviewContent(FileUpload fileUpload) {
        String extension = getFileExtension(fileUpload.getFileName()).toLowerCase(Locale.ROOT);
        if (!isTextFile(extension)) {
            return buildBinaryFileInfo(fileUpload, extension);
        }

        try (var inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object("merged/" + fileUpload.getFileName())
                        .build());
             var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            int bytesRead = 0;
            while ((line = reader.readLine()) != null && bytesRead < PREVIEW_MAX_BYTES) {
                content.append(line).append("\n");
                bytesRead += line.getBytes(StandardCharsets.UTF_8).length + 1;
            }
            if (bytesRead >= PREVIEW_MAX_BYTES) {
                content.append("\n... (内容已截断，仅显示前10KB)");
            }
            return content.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("无法获取文件预览内容", exception);
        }
    }

    private String buildBinaryFileInfo(FileUpload fileUpload, String extension) {
        String normalizedExtension = extension.isEmpty() ? "UNKNOWN" : extension.toUpperCase(Locale.ROOT);
        return String.format(
                "文件名: %s%n文件大小: %s%n文件类型: %s%n上传时间: %s%n%n此文件类型不支持预览，请下载后查看。",
                fileUpload.getFileName(),
                formatFileSize(fileUpload.getTotalSize()),
                normalizedExtension,
                fileUpload.getCreatedAt()
        );
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDotIndex + 1);
    }

    private boolean isTextFile(String extension) {
        return switch (extension) {
            case "txt", "md", "doc", "docx", "pdf", "html", "htm", "xml", "json",
                    "csv", "log", "java", "js", "ts", "py", "cpp", "c", "h", "css",
                    "scss", "less", "sql", "yml", "yaml", "properties", "conf", "config" -> true;
            default -> false;
        };
    }

    private String formatFileSize(Long size) {
        if (size == null) {
            return "未知";
        }
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", size / 1024.0);
        }
        if (size < 1024 * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f MB", size / (1024.0 * 1024.0));
        }
        return String.format(Locale.ROOT, "%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
    }
}
