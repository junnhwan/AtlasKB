package io.hwan.atlaskb.document.consumer;

import io.hwan.atlaskb.common.exception.BusinessException;
import io.hwan.atlaskb.document.entity.DocumentVector;
import io.hwan.atlaskb.document.entity.FileUpload;
import io.hwan.atlaskb.document.model.FileProcessingTask;
import io.hwan.atlaskb.document.model.TextChunk;
import io.hwan.atlaskb.document.repository.DocumentVectorRepository;
import io.hwan.atlaskb.document.repository.FileUploadRepository;
import io.hwan.atlaskb.document.service.ParseService;
import io.hwan.atlaskb.storage.service.StorageObjectReader;
import java.io.InputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class FileProcessingConsumer {

    private static final Logger log = LoggerFactory.getLogger(FileProcessingConsumer.class);
    private static final int STATUS_MERGED_PENDING = 1;

    private final FileUploadRepository fileUploadRepository;
    private final DocumentVectorRepository documentVectorRepository;
    private final ParseService parseService;
    private final StorageObjectReader storageObjectReader;

    public FileProcessingConsumer(
            FileUploadRepository fileUploadRepository,
            DocumentVectorRepository documentVectorRepository,
            ParseService parseService,
            StorageObjectReader storageObjectReader
    ) {
        this.fileUploadRepository = fileUploadRepository;
        this.documentVectorRepository = documentVectorRepository;
        this.parseService = parseService;
        this.storageObjectReader = storageObjectReader;
    }

    @KafkaListener(
            topics = "${spring.kafka.topic.file-processing}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void processTask(FileProcessingTask task) {
        log.info(
                "Received file processing task: fileMd5={}, fileName={}, userId={}, orgTag={}, publicAccessible={}",
                task.fileMd5(),
                task.fileName(),
                task.userId(),
                task.orgTag(),
                task.publicAccessible()
        );

        FileUpload fileUpload = fileUploadRepository.findByFileMd5AndUserId(task.fileMd5(), task.userId())
                .orElseThrow(() -> new BusinessException(4042, "上传记录不存在"));

        try (InputStream inputStream = storageObjectReader.read(task.objectUrl())) {
            List<TextChunk> chunks = parseService.parse(inputStream, task.fileName());
            if (!chunks.isEmpty()) {
                List<DocumentVector> vectors = chunks.stream()
                        .map(chunk -> toDocumentVector(task, chunk))
                        .toList();
                documentVectorRepository.saveAll(vectors);
            }

            fileUpload.setStatus(STATUS_MERGED_PENDING);
            fileUploadRepository.save(fileUpload);
        } catch (Exception exception) {
            log.error("Failed to parse and persist chunks for fileMd5={}", task.fileMd5(), exception);
            throw new RuntimeException("Failed to process file task", exception);
        }
    }

    private DocumentVector toDocumentVector(FileProcessingTask task, TextChunk chunk) {
        DocumentVector vector = new DocumentVector();
        vector.setFileMd5(task.fileMd5());
        vector.setChunkId(chunk.chunkId());
        vector.setTextContent(chunk.content());
        vector.setUserId(task.userId());
        vector.setOrgTag(task.orgTag());
        vector.setPublic(task.publicAccessible());
        return vector;
    }
}
