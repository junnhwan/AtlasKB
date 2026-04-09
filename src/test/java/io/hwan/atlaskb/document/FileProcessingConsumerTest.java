package io.hwan.atlaskb.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.hwan.atlaskb.document.consumer.FileProcessingConsumer;
import io.hwan.atlaskb.document.entity.FileUpload;
import io.hwan.atlaskb.document.model.FileProcessingTask;
import io.hwan.atlaskb.document.repository.FileUploadRepository;
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

    @InjectMocks
    private FileProcessingConsumer fileProcessingConsumer;

    @Test
    void processTaskKeepsFileInPendingProcessingStatus() {
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

        when(fileUploadRepository.findByFileMd5AndUserId("abc123", "1")).thenReturn(Optional.of(fileUpload));
        when(fileUploadRepository.save(any(FileUpload.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fileProcessingConsumer.processTask(task);

        ArgumentCaptor<FileUpload> fileCaptor = ArgumentCaptor.forClass(FileUpload.class);
        verify(fileUploadRepository).save(fileCaptor.capture());
        assertEquals(1, fileCaptor.getValue().getStatus());
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
