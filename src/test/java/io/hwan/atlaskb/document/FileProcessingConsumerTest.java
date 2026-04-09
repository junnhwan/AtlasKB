package io.hwan.atlaskb.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.hwan.atlaskb.document.consumer.FileProcessingConsumer;
import io.hwan.atlaskb.document.entity.DocumentVector;
import io.hwan.atlaskb.document.entity.FileUpload;
import io.hwan.atlaskb.document.model.FileProcessingTask;
import io.hwan.atlaskb.document.model.TextChunk;
import io.hwan.atlaskb.document.repository.DocumentVectorRepository;
import io.hwan.atlaskb.document.repository.FileUploadRepository;
import io.hwan.atlaskb.document.service.ParseService;
import io.hwan.atlaskb.search.service.IndexingService;
import io.hwan.atlaskb.storage.service.StorageObjectReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileProcessingConsumerTest {

    @Mock
    private FileUploadRepository fileUploadRepository;

    @Mock
    private DocumentVectorRepository documentVectorRepository;

    @Mock
    private ParseService parseService;

    @Mock
    private StorageObjectReader storageObjectReader;

    @Mock
    private IndexingService indexingService;

    @InjectMocks
    private FileProcessingConsumer fileProcessingConsumer;

    @Test
    void processTaskIndexesChunksAndMarksFileAsIndexed() throws Exception {
        FileUpload fileUpload = new FileUpload();
        fileUpload.setFileMd5("abc123");
        fileUpload.setFileName("manual.pdf");
        fileUpload.setUserId("1");
        fileUpload.setStatus(1);

        FileProcessingTask task = new FileProcessingTask(
                "abc123",
                "http://localhost:9000/atlas-kb-uploads/merged/manual.pdf",
                "manual.pdf",
                "1",
                "default",
                true
        );

        InputStream inputStream = new ByteArrayInputStream("AtlasKB".getBytes(StandardCharsets.UTF_8));
        when(fileUploadRepository.findByFileMd5AndUserId("abc123", "1")).thenReturn(Optional.of(fileUpload));
        when(storageObjectReader.read("http://localhost:9000/atlas-kb-uploads/merged/manual.pdf")).thenReturn(inputStream);
        when(parseService.parse(any(InputStream.class), eq("manual.pdf"))).thenReturn(List.of(
                new TextChunk(1, "chunk one"),
                new TextChunk(2, "chunk two")
        ));
        when(documentVectorRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileUploadRepository.save(any(FileUpload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fileProcessingConsumer.processTask(task);

        ArgumentCaptor<FileUpload> fileCaptor = ArgumentCaptor.forClass(FileUpload.class);
        verify(fileUploadRepository).save(fileCaptor.capture());
        assertEquals(2, fileCaptor.getValue().getStatus());
        verify(storageObjectReader).read("http://localhost:9000/atlas-kb-uploads/merged/manual.pdf");
        verify(parseService).parse(any(InputStream.class), eq("manual.pdf"));
        verify(documentVectorRepository).deleteByFileMd5AndUserId("abc123", "1");
        verify(indexingService).indexFile("abc123", "1");

        ArgumentCaptor<Iterable<DocumentVector>> vectorCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(documentVectorRepository).saveAll(vectorCaptor.capture());
        List<DocumentVector> savedVectors = (List<DocumentVector>) vectorCaptor.getValue();
        assertEquals(2, savedVectors.size());
        assertEquals("abc123", savedVectors.get(0).getFileMd5());
        assertEquals(1, savedVectors.get(0).getChunkId());
        assertEquals("chunk one", savedVectors.get(0).getTextContent());
        assertEquals("1", savedVectors.get(0).getUserId());
        assertEquals("default", savedVectors.get(0).getOrgTag());
        assertEquals(true, savedVectors.get(0).isPublic());
    }

    @Test
    void processTaskMarksFileAsFailedWhenIndexingFails() throws Exception {
        FileUpload fileUpload = new FileUpload();
        fileUpload.setFileMd5("abc123");
        fileUpload.setFileName("manual.pdf");
        fileUpload.setUserId("1");
        fileUpload.setStatus(1);

        FileProcessingTask task = new FileProcessingTask(
                "abc123",
                "http://localhost:9000/atlas-kb-uploads/merged/manual.pdf",
                "manual.pdf",
                "1",
                "default",
                true
        );

        InputStream inputStream = new ByteArrayInputStream("AtlasKB".getBytes(StandardCharsets.UTF_8));
        when(fileUploadRepository.findByFileMd5AndUserId("abc123", "1")).thenReturn(Optional.of(fileUpload));
        when(storageObjectReader.read("http://localhost:9000/atlas-kb-uploads/merged/manual.pdf")).thenReturn(inputStream);
        when(parseService.parse(any(InputStream.class), eq("manual.pdf"))).thenReturn(List.of(new TextChunk(1, "chunk one")));
        when(documentVectorRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileUploadRepository.save(any(FileUpload.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("index failed")).when(indexingService).indexFile("abc123", "1");

        assertThrows(RuntimeException.class, () -> fileProcessingConsumer.processTask(task));

        ArgumentCaptor<FileUpload> fileCaptor = ArgumentCaptor.forClass(FileUpload.class);
        verify(fileUploadRepository).save(fileCaptor.capture());
        assertEquals(3, fileCaptor.getValue().getStatus());
    }

    @Test
    void processTaskFailsWhenUploadRecordIsMissing() {
        FileProcessingTask task = new FileProcessingTask(
                "abc123",
                "http://localhost:9000/atlas-kb-uploads/merged/manual.pdf",
                "manual.pdf",
                "1",
                "default",
                true
        );

        when(fileUploadRepository.findByFileMd5AndUserId("abc123", "1")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> fileProcessingConsumer.processTask(task));
    }
}
