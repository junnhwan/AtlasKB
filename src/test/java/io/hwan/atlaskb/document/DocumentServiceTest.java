package io.hwan.atlaskb.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import io.hwan.atlaskb.document.dto.DocumentFileSummary;
import io.hwan.atlaskb.document.entity.FileUpload;
import io.hwan.atlaskb.document.repository.FileUploadRepository;
import io.hwan.atlaskb.document.service.DocumentService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private FileUploadRepository fileUploadRepository;

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

        DocumentService documentService = new DocumentService(fileUploadRepository);

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
}
