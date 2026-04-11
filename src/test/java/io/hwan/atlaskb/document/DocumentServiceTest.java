package io.hwan.atlaskb.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.hwan.atlaskb.common.exception.BusinessException;
import io.hwan.atlaskb.document.dto.DocumentDownloadInfo;
import io.hwan.atlaskb.document.dto.DocumentFileSummary;
import io.hwan.atlaskb.document.entity.FileUpload;
import io.hwan.atlaskb.document.repository.ChunkInfoRepository;
import io.hwan.atlaskb.document.repository.DocumentVectorRepository;
import io.hwan.atlaskb.document.repository.FileUploadRepository;
import io.hwan.atlaskb.document.service.DocumentService;
import io.hwan.atlaskb.organization.service.OrgTagPermissionService;
import io.hwan.atlaskb.search.service.IndexingService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private FileUploadRepository fileUploadRepository;

    @Mock
    private ChunkInfoRepository chunkInfoRepository;

    @Mock
    private DocumentVectorRepository documentVectorRepository;

    @Mock
    private MinioClient minioClient;

    @Mock
    private IndexingService indexingService;

    @Mock
    private OrgTagPermissionService orgTagPermissionService;

    @Test
    void getUserUploadedFilesReturnsMappedSummaries() {
        FileUpload first = new FileUpload();
        first.setFileMd5("abc123");
        first.setFileName("manual.pdf");
        first.setTotalSize(1024L);
        first.setStatus(2);
        first.setUserId("1");
        first.setOrgTag("default");
        first.setPublic(true);
        first.setMergedAt(LocalDateTime.of(2026, 4, 10, 12, 5, 0));
        ReflectionTestUtils.setField(first, "createdAt", LocalDateTime.of(2026, 4, 10, 12, 0, 0));

        FileUpload second = new FileUpload();
        second.setFileMd5("def456");
        second.setFileName("guide.docx");
        second.setTotalSize(2048L);
        second.setStatus(1);
        second.setUserId("1");
        second.setOrgTag("admin");
        second.setPublic(false);
        ReflectionTestUtils.setField(second, "createdAt", LocalDateTime.of(2026, 4, 9, 10, 0, 0));

        when(fileUploadRepository.findByUserIdOrderByCreatedAtDesc("1")).thenReturn(List.of(first, second));

        DocumentService documentService = new DocumentService(
                fileUploadRepository,
                chunkInfoRepository,
                documentVectorRepository,
                minioClient,
                indexingService,
                orgTagPermissionService,
                "atlas-kb-uploads"
        );

        List<DocumentFileSummary> summaries = documentService.getUserUploadedFiles("1");

        assertEquals(2, summaries.size());
        assertEquals("abc123", summaries.get(0).fileMd5());
        assertEquals("manual.pdf", summaries.get(0).fileName());
        assertEquals(1024L, summaries.get(0).totalSize());
        assertEquals(2, summaries.get(0).status());
        assertEquals("1", summaries.get(0).userId());
        assertEquals("default", summaries.get(0).orgTag());
        assertEquals(true, summaries.get(0).isPublic());
        assertEquals("2026-04-10T12:00", summaries.get(0).createdAt());
        assertEquals("2026-04-10T12:05", summaries.get(0).mergedAt());

        assertEquals("def456", summaries.get(1).fileMd5());
        assertEquals("guide.docx", summaries.get(1).fileName());
        assertEquals(2048L, summaries.get(1).totalSize());
        assertEquals(1, summaries.get(1).status());
        assertEquals("admin", summaries.get(1).orgTag());
        assertEquals(false, summaries.get(1).isPublic());
        assertEquals("2026-04-09T10:00", summaries.get(1).createdAt());
        assertEquals(null, summaries.get(1).mergedAt());
    }

    @Test
    void deleteDocumentRemovesStoredMetadataVectorsAndMergedObject() throws Exception {
        FileUpload fileUpload = new FileUpload();
        fileUpload.setFileMd5("abc123");
        fileUpload.setFileName("manual.pdf");
        fileUpload.setUserId("1");

        when(fileUploadRepository.findByFileMd5AndUserId("abc123", "1")).thenReturn(Optional.of(fileUpload));

        DocumentService documentService = new DocumentService(
                fileUploadRepository,
                chunkInfoRepository,
                documentVectorRepository,
                minioClient,
                indexingService,
                orgTagPermissionService,
                "atlas-kb-uploads"
        );

        documentService.deleteDocument("abc123", "1", "USER");

        verify(indexingService).deleteFile("abc123", "1");
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
        verify(documentVectorRepository).deleteByFileMd5AndUserId("abc123", "1");
        verify(chunkInfoRepository).deleteByFileMd5("abc123");
        verify(fileUploadRepository).delete(fileUpload);
    }

    @Test
    void deleteDocumentAllowsAdminToDeleteOtherUsersFile() {
        FileUpload fileUpload = new FileUpload();
        fileUpload.setFileMd5("abc123");
        fileUpload.setFileName("manual.pdf");
        fileUpload.setUserId("9");

        when(fileUploadRepository.findByFileMd5("abc123")).thenReturn(Optional.of(fileUpload));

        DocumentService documentService = new DocumentService(
                fileUploadRepository,
                chunkInfoRepository,
                documentVectorRepository,
                minioClient,
                indexingService,
                orgTagPermissionService,
                "atlas-kb-uploads"
        );

        documentService.deleteDocument("abc123", "1", "ADMIN");

        verify(indexingService).deleteFile("abc123", "9");
        verify(documentVectorRepository).deleteByFileMd5AndUserId("abc123", "9");
    }

    @Test
    void deleteDocumentThrowsWhenUploadRecordDoesNotExist() {
        when(fileUploadRepository.findByFileMd5AndUserId("missing", "1")).thenReturn(Optional.empty());

        DocumentService documentService = new DocumentService(
                fileUploadRepository,
                chunkInfoRepository,
                documentVectorRepository,
                minioClient,
                indexingService,
                orgTagPermissionService,
                "atlas-kb-uploads"
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentService.deleteDocument("missing", "1", "USER")
        );

        assertEquals(4042, exception.getCode());
        assertEquals("上传记录不存在", exception.getMessage());
    }

    @Test
    void getAccessibleFilesReturnsMappedSummariesForOwnPublicAndOrgFiles() {
        FileUpload ownFile = new FileUpload();
        ownFile.setFileMd5("self123");
        ownFile.setFileName("self.pdf");
        ownFile.setTotalSize(1024L);
        ownFile.setStatus(2);
        ownFile.setUserId("1");
        ownFile.setOrgTag("default");
        ownFile.setPublic(false);
        ReflectionTestUtils.setField(ownFile, "createdAt", LocalDateTime.of(2026, 4, 10, 15, 0, 0));

        FileUpload sharedFile = new FileUpload();
        sharedFile.setFileMd5("shared123");
        sharedFile.setFileName("shared.pdf");
        sharedFile.setTotalSize(2048L);
        sharedFile.setStatus(2);
        sharedFile.setUserId("9");
        sharedFile.setOrgTag("sales");
        sharedFile.setPublic(false);
        ReflectionTestUtils.setField(sharedFile, "createdAt", LocalDateTime.of(2026, 4, 10, 14, 0, 0));

        when(orgTagPermissionService.resolveAccessibleOrgTags("1")).thenReturn(List.of("default", "sales"));
        when(fileUploadRepository.findAccessibleFilesOrderByCreatedAtDesc("1", List.of("default", "sales")))
                .thenReturn(List.of(ownFile, sharedFile));

        DocumentService documentService = new DocumentService(
                fileUploadRepository,
                chunkInfoRepository,
                documentVectorRepository,
                minioClient,
                indexingService,
                orgTagPermissionService,
                "atlas-kb-uploads"
        );

        List<DocumentFileSummary> summaries = documentService.getAccessibleFiles("1");

        assertEquals(2, summaries.size());
        assertEquals("self123", summaries.get(0).fileMd5());
        assertEquals("shared123", summaries.get(1).fileMd5());
        assertEquals("9", summaries.get(1).userId());
        assertEquals("sales", summaries.get(1).orgTag());
    }

    @Test
    void getAccessibleFilesFallsBackToOwnAndPublicFilesWhenUserHasNoOrgTags() {
        FileUpload publicFile = new FileUpload();
        publicFile.setFileMd5("public123");
        publicFile.setFileName("public.pdf");
        publicFile.setTotalSize(512L);
        publicFile.setStatus(2);
        publicFile.setUserId("8");
        publicFile.setOrgTag("public");
        publicFile.setPublic(true);
        ReflectionTestUtils.setField(publicFile, "createdAt", LocalDateTime.of(2026, 4, 10, 12, 0, 0));

        when(orgTagPermissionService.resolveAccessibleOrgTags("1")).thenReturn(List.of());
        when(fileUploadRepository.findByUserIdOrIsPublicTrueOrderByCreatedAtDesc("1"))
                .thenReturn(List.of(publicFile));

        DocumentService documentService = new DocumentService(
                fileUploadRepository,
                chunkInfoRepository,
                documentVectorRepository,
                minioClient,
                indexingService,
                orgTagPermissionService,
                "atlas-kb-uploads"
        );

        List<DocumentFileSummary> summaries = documentService.getAccessibleFiles("1");

        assertEquals(1, summaries.size());
        assertEquals("public123", summaries.get(0).fileMd5());
        assertEquals(true, summaries.get(0).isPublic());
    }

    @Test
    void getDownloadInfoReturnsPublicFileForAnonymousUser() throws Exception {
        FileUpload publicFile = new FileUpload();
        publicFile.setFileMd5("public123");
        publicFile.setFileName("public.pdf");
        publicFile.setTotalSize(4096L);
        publicFile.setPublic(true);

        when(fileUploadRepository.findByFileNameAndIsPublicTrue("public.pdf")).thenReturn(Optional.of(publicFile));
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/download/public.pdf");

        DocumentService documentService = new DocumentService(
                fileUploadRepository,
                chunkInfoRepository,
                documentVectorRepository,
                minioClient,
                indexingService,
                orgTagPermissionService,
                "atlas-kb-uploads"
        );

        DocumentDownloadInfo downloadInfo = documentService.getDownloadInfo("public.pdf", null);

        assertEquals("public.pdf", downloadInfo.fileName());
        assertEquals("http://localhost:9000/download/public.pdf", downloadInfo.downloadUrl());
        assertEquals(4096L, downloadInfo.fileSize());
    }

    @Test
    void getDownloadInfoReturnsAccessibleFileForAuthenticatedUser() throws Exception {
        FileUpload accessibleFile = new FileUpload();
        accessibleFile.setFileMd5("shared123");
        accessibleFile.setFileName("shared.pdf");
        accessibleFile.setTotalSize(2048L);
        accessibleFile.setUserId("9");
        accessibleFile.setOrgTag("sales");
        accessibleFile.setPublic(false);
        ReflectionTestUtils.setField(accessibleFile, "createdAt", LocalDateTime.of(2026, 4, 10, 14, 0, 0));

        when(orgTagPermissionService.resolveAccessibleOrgTags("1")).thenReturn(List.of("default", "sales"));
        when(fileUploadRepository.findAccessibleFilesOrderByCreatedAtDesc("1", List.of("default", "sales")))
                .thenReturn(List.of(accessibleFile));
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/download/shared.pdf");

        DocumentService documentService = new DocumentService(
                fileUploadRepository,
                chunkInfoRepository,
                documentVectorRepository,
                minioClient,
                indexingService,
                orgTagPermissionService,
                "atlas-kb-uploads"
        );

        DocumentDownloadInfo downloadInfo = documentService.getDownloadInfo("shared.pdf", "1");

        assertEquals("shared.pdf", downloadInfo.fileName());
        assertEquals("http://localhost:9000/download/shared.pdf", downloadInfo.downloadUrl());
        assertEquals(2048L, downloadInfo.fileSize());
    }

    @Test
    void getDownloadInfoThrowsWhenAnonymousUserRequestsNonPublicFile() {
        when(fileUploadRepository.findByFileNameAndIsPublicTrue("private.pdf")).thenReturn(Optional.empty());

        DocumentService documentService = new DocumentService(
                fileUploadRepository,
                chunkInfoRepository,
                documentVectorRepository,
                minioClient,
                indexingService,
                orgTagPermissionService,
                "atlas-kb-uploads"
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentService.getDownloadInfo("private.pdf", null)
        );

        assertEquals(4042, exception.getCode());
        assertEquals("文件不存在或需要登录访问", exception.getMessage());
    }
}
