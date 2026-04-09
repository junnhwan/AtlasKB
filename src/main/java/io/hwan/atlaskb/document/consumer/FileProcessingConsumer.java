package io.hwan.atlaskb.document.consumer;

import io.hwan.atlaskb.common.exception.BusinessException;
import io.hwan.atlaskb.document.entity.FileUpload;
import io.hwan.atlaskb.document.model.FileProcessingTask;
import io.hwan.atlaskb.document.repository.FileUploadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class FileProcessingConsumer {

    private static final Logger log = LoggerFactory.getLogger(FileProcessingConsumer.class);
    private static final int STATUS_MERGED_PENDING = 1;

    private final FileUploadRepository fileUploadRepository;

    public FileProcessingConsumer(FileUploadRepository fileUploadRepository) {
        this.fileUploadRepository = fileUploadRepository;
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
        fileUpload.setStatus(STATUS_MERGED_PENDING);
        fileUploadRepository.save(fileUpload);
    }
}
