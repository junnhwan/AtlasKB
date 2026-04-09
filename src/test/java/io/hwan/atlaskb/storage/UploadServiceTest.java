package io.hwan.atlaskb.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.hwan.atlaskb.document.entity.ChunkInfo;
import io.hwan.atlaskb.document.entity.FileUpload;
import io.hwan.atlaskb.document.repository.ChunkInfoRepository;
import io.hwan.atlaskb.document.repository.FileUploadRepository;
import io.hwan.atlaskb.storage.model.UploadChunkCommand;
import io.hwan.atlaskb.storage.model.UploadChunkResult;
import io.hwan.atlaskb.storage.model.UploadStatusResult;
import io.hwan.atlaskb.storage.service.UploadService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private FileUploadRepository fileUploadRepository;

    @Mock
    private ChunkInfoRepository chunkInfoRepository;

    private UploadService uploadService;

    @BeforeEach
    void setUp() {
        uploadService = new UploadService(
                minioClient,
                stringRedisTemplate,
                fileUploadRepository,
                chunkInfoRepository,
                "atlas-kb-uploads"
        );
    }

    @Test
    void uploadChunkCreatesMetadataStoresObjectAndReturnsProgress() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "manual.pdf",
                "application/pdf",
                "AtlasKB handbook".getBytes()
        );

        UploadChunkCommand command = new UploadChunkCommand(
                "abc123",
                0,
                10L * 1024 * 1024,
                "manual.pdf",
                file,
                "default",
                false,
                "1"
        );

        ChunkInfo chunkInfo = new ChunkInfo();
        chunkInfo.setFileMd5("abc123");
        chunkInfo.setChunkIndex(0);
        chunkInfo.setChunkMd5("chunk-md5");
        chunkInfo.setStoragePath("chunks/abc123/0");

        when(fileUploadRepository.findByFileMd5AndUserId("abc123", "1")).thenReturn(Optional.empty());
        when(fileUploadRepository.save(any(FileUpload.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chunkInfoRepository.findByFileMd5AndChunkIndex("abc123", 0)).thenReturn(Optional.empty());
        when(chunkInfoRepository.findByFileMd5OrderByChunkIndexAsc("abc123")).thenReturn(List.of(chunkInfo));

        UploadChunkResult result = uploadService.uploadChunk(command);

        verify(fileUploadRepository).save(argThat(fileUpload ->
                "abc123".equals(fileUpload.getFileMd5())
                        && "manual.pdf".equals(fileUpload.getFileName())
                        && fileUpload.getTotalSize() == 10L * 1024 * 1024
                        && fileUpload.getStatus() == 0
                        && "1".equals(fileUpload.getUserId())
                        && "default".equals(fileUpload.getOrgTag())
                        && !fileUpload.isPublic()
        ));
        verify(minioClient).putObject(any(PutObjectArgs.class));
        verify(valueOperations).setBit("upload:1:abc123", 0, true);
        verify(chunkInfoRepository).save(argThat(savedChunk ->
                "abc123".equals(savedChunk.getFileMd5())
                        && savedChunk.getChunkIndex() == 0
                        && "chunks/abc123/0".equals(savedChunk.getStoragePath())
        ));
        assertIterableEquals(List.of(0), result.uploaded());
        assertEquals(50.0d, result.progress());
    }

    @Test
    void getUploadStatusReadsMetadataAndCalculatesProgress() {
        FileUpload fileUpload = new FileUpload();
        fileUpload.setFileMd5("abc123");
        fileUpload.setFileName("manual.pdf");
        fileUpload.setTotalSize(10L * 1024 * 1024);
        fileUpload.setUserId("1");

        ChunkInfo chunkInfo = new ChunkInfo();
        chunkInfo.setFileMd5("abc123");
        chunkInfo.setChunkIndex(0);
        chunkInfo.setChunkMd5("chunk-md5");
        chunkInfo.setStoragePath("chunks/abc123/0");

        when(fileUploadRepository.findByFileMd5AndUserId("abc123", "1")).thenReturn(Optional.of(fileUpload));
        when(chunkInfoRepository.findByFileMd5OrderByChunkIndexAsc("abc123")).thenReturn(List.of(chunkInfo));

        UploadStatusResult result = uploadService.getUploadStatus("abc123", "1");

        assertIterableEquals(List.of(0), result.uploaded());
        assertEquals(50.0d, result.progress());
        assertEquals("manual.pdf", result.fileName());
        assertEquals("pdf", result.fileType());
    }
}
