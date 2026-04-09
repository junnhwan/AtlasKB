package io.hwan.atlaskb.storage.service;

import io.hwan.atlaskb.document.entity.ChunkInfo;
import io.hwan.atlaskb.document.entity.FileUpload;
import io.hwan.atlaskb.document.repository.ChunkInfoRepository;
import io.hwan.atlaskb.document.repository.FileUploadRepository;
import io.hwan.atlaskb.common.exception.BusinessException;
import io.hwan.atlaskb.storage.model.UploadChunkCommand;
import io.hwan.atlaskb.storage.model.UploadChunkResult;
import io.hwan.atlaskb.storage.model.UploadStatusResult;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

@Service
public class UploadService {

    private static final long DEFAULT_CHUNK_SIZE = 5L * 1024 * 1024;
    private static final int STATUS_CREATED = 0;

    private final MinioClient minioClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final FileUploadRepository fileUploadRepository;
    private final ChunkInfoRepository chunkInfoRepository;
    private final String bucketName;
    private final String publicUrl;

    public UploadService(
            MinioClient minioClient,
            StringRedisTemplate stringRedisTemplate,
            FileUploadRepository fileUploadRepository,
            ChunkInfoRepository chunkInfoRepository,
            @Value("${minio.bucket-name}") String bucketName,
            @Value("${minio.public-url}") String publicUrl
    ) {
        this.minioClient = minioClient;
        this.stringRedisTemplate = stringRedisTemplate;
        this.fileUploadRepository = fileUploadRepository;
        this.chunkInfoRepository = chunkInfoRepository;
        this.bucketName = bucketName;
        this.publicUrl = publicUrl;
    }

    @Transactional
    public UploadChunkResult uploadChunk(UploadChunkCommand command) {
        FileUpload fileUpload = fileUploadRepository.findByFileMd5AndUserId(command.fileMd5(), command.userId())
                .orElseGet(() -> createFileUpload(command));

        if (chunkInfoRepository.findByFileMd5AndChunkIndex(command.fileMd5(), command.chunkIndex()).isPresent()) {
            return buildChunkResult(command.fileMd5(), fileUpload.getTotalSize());
        }

        String storagePath = buildChunkPath(command.fileMd5(), command.chunkIndex());
        String chunkMd5 = calculateChunkMd5(command);
        uploadToMinio(command, storagePath);
        stringRedisTemplate.opsForValue().setBit(buildRedisKey(command.userId(), command.fileMd5()), command.chunkIndex(), true);

        ChunkInfo chunkInfo = new ChunkInfo();
        chunkInfo.setFileMd5(command.fileMd5());
        chunkInfo.setChunkIndex(command.chunkIndex());
        chunkInfo.setChunkMd5(chunkMd5);
        chunkInfo.setStoragePath(storagePath);
        chunkInfoRepository.save(chunkInfo);

        return buildChunkResult(command.fileMd5(), fileUpload.getTotalSize());
    }

    private FileUpload createFileUpload(UploadChunkCommand command) {
        FileUpload fileUpload = new FileUpload();
        fileUpload.setFileMd5(command.fileMd5());
        fileUpload.setFileName(command.fileName());
        fileUpload.setTotalSize(command.totalSize());
        fileUpload.setStatus(STATUS_CREATED);
        fileUpload.setUserId(command.userId());
        fileUpload.setOrgTag(command.orgTag());
        fileUpload.setPublic(command.isPublic());
        return fileUploadRepository.save(fileUpload);
    }

    private String calculateChunkMd5(UploadChunkCommand command) {
        try {
            return DigestUtils.md5DigestAsHex(command.file().getBytes());
        } catch (IOException exception) {
            throw new RuntimeException("Failed to calculate chunk md5", exception);
        }
    }

    private void uploadToMinio(UploadChunkCommand command, String storagePath) {
        try {
            String contentType = command.file().getContentType();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storagePath)
                            .stream(command.file().getInputStream(), command.file().getSize(), -1)
                            .contentType(contentType == null ? "application/octet-stream" : contentType)
                            .build()
            );
        } catch (Exception exception) {
            throw new RuntimeException("Failed to upload chunk", exception);
        }
    }

    private UploadChunkResult buildChunkResult(String fileMd5, long totalSize) {
        List<Integer> uploadedChunks = chunkInfoRepository.findByFileMd5OrderByChunkIndexAsc(fileMd5)
                .stream()
                .map(ChunkInfo::getChunkIndex)
                .toList();
        int totalChunks = calculateTotalChunks(totalSize);
        double progress = totalChunks == 0 ? 0.0d : uploadedChunks.size() * 100.0d / totalChunks;
        return new UploadChunkResult(uploadedChunks, progress);
    }

    @Transactional(readOnly = true)
    public UploadStatusResult getUploadStatus(String fileMd5, String userId) {
        FileUpload fileUpload = fileUploadRepository.findByFileMd5AndUserId(fileMd5, userId)
                .orElseThrow(() -> new BusinessException(4042, "上传记录不存在"));
        List<Integer> uploadedChunks = chunkInfoRepository.findByFileMd5OrderByChunkIndexAsc(fileMd5)
                .stream()
                .map(ChunkInfo::getChunkIndex)
                .toList();
        int totalChunks = calculateTotalChunks(fileUpload.getTotalSize());
        double progress = totalChunks == 0 ? 0.0d : uploadedChunks.size() * 100.0d / totalChunks;

        return new UploadStatusResult(
                uploadedChunks,
                progress,
                fileUpload.getFileName(),
                resolveFileType(fileUpload.getFileName())
        );
    }

    @Transactional
    public String mergeChunks(String fileMd5, String fileName, String userId) {
        FileUpload fileUpload = fileUploadRepository.findByFileMd5AndUserId(fileMd5, userId)
                .orElseThrow(() -> new BusinessException(4042, "上传记录不存在"));
        List<ChunkInfo> chunks = chunkInfoRepository.findByFileMd5OrderByChunkIndexAsc(fileMd5);
        int expectedChunks = calculateTotalChunks(fileUpload.getTotalSize());
        if (chunks.size() != expectedChunks) {
            throw new BusinessException(4003, "文件分片未上传完成");
        }

        String mergedPath = "merged/" + fileName;
        List<ComposeSource> sources = chunks.stream()
                .map(chunkInfo -> ComposeSource.builder()
                        .bucket(bucketName)
                        .object(chunkInfo.getStoragePath())
                        .build())
                .toList();

        try {
            minioClient.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(bucketName)
                            .object(mergedPath)
                            .sources(sources)
                            .build()
            );

            for (ChunkInfo chunk : chunks) {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucketName)
                                .object(chunk.getStoragePath())
                                .build()
                );
            }
        } catch (Exception exception) {
            throw new RuntimeException("Failed to merge chunks", exception);
        }

        stringRedisTemplate.delete(buildRedisKey(userId, fileMd5));
        fileUpload.setStatus(1);
        fileUpload.setMergedAt(LocalDateTime.now());
        fileUploadRepository.save(fileUpload);

        return buildObjectUrl(mergedPath);
    }

    private int calculateTotalChunks(long totalSize) {
        return (int) Math.ceil(totalSize * 1.0d / DEFAULT_CHUNK_SIZE);
    }

    private String buildChunkPath(String fileMd5, int chunkIndex) {
        return "chunks/" + fileMd5 + "/" + chunkIndex;
    }

    private String buildRedisKey(String userId, String fileMd5) {
        return "upload:" + userId + ":" + fileMd5;
    }

    private String resolveFileType(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == fileName.length() - 1) {
            return "unknown";
        }
        return fileName.substring(lastDotIndex + 1).toLowerCase();
    }

    private String buildObjectUrl(String mergedPath) {
        String normalizedPublicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        return normalizedPublicUrl + "/" + bucketName + "/" + mergedPath;
    }
}
