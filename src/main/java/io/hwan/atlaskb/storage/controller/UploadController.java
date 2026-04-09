package io.hwan.atlaskb.storage.controller;

import io.hwan.atlaskb.common.api.ApiResponse;
import io.hwan.atlaskb.common.exception.BusinessException;
import io.hwan.atlaskb.document.entity.FileUpload;
import io.hwan.atlaskb.document.model.FileProcessingTask;
import io.hwan.atlaskb.document.repository.FileUploadRepository;
import io.hwan.atlaskb.document.service.FileTypeValidationService;
import io.hwan.atlaskb.storage.dto.MergeRequest;
import io.hwan.atlaskb.storage.model.MergeResult;
import io.hwan.atlaskb.storage.model.UploadChunkCommand;
import io.hwan.atlaskb.storage.model.UploadChunkResult;
import io.hwan.atlaskb.storage.model.UploadStatusResult;
import io.hwan.atlaskb.storage.service.UploadService;
import io.hwan.atlaskb.user.service.UserQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/upload")
public class UploadController {

    private final UploadService uploadService;
    private final UserQueryService userQueryService;
    private final FileTypeValidationService fileTypeValidationService;
    private final FileUploadRepository fileUploadRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String fileProcessingTopic;

    public UploadController(
            UploadService uploadService,
            UserQueryService userQueryService,
            FileTypeValidationService fileTypeValidationService,
            FileUploadRepository fileUploadRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${spring.kafka.topic.file-processing}") String fileProcessingTopic
    ) {
        this.uploadService = uploadService;
        this.userQueryService = userQueryService;
        this.fileTypeValidationService = fileTypeValidationService;
        this.fileUploadRepository = fileUploadRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.fileProcessingTopic = fileProcessingTopic;
    }

    @PostMapping("/chunk")
    public ApiResponse<UploadChunkResult> uploadChunk(
            @RequestParam("fileMd5") String fileMd5,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("totalSize") long totalSize,
            @RequestParam("fileName") String fileName,
            @RequestParam(value = "orgTag", required = false) String orgTag,
            @RequestParam(value = "isPublic", defaultValue = "false") boolean isPublic,
            @RequestParam("file") MultipartFile file,
            @RequestAttribute("userId") Long userId
    ) {
        if (chunkIndex == 0 && !fileTypeValidationService.isAllowed(fileName)) {
            throw new BusinessException(4001, "Unsupported file type");
        }

        String resolvedOrgTag = StringUtils.hasText(orgTag) ? orgTag : userQueryService.getPrimaryOrg(userId);
        UploadChunkCommand command = new UploadChunkCommand(
                fileMd5,
                chunkIndex,
                totalSize,
                fileName,
                file,
                resolvedOrgTag,
                isPublic,
                String.valueOf(userId)
        );
        return ApiResponse.success(uploadService.uploadChunk(command));
    }

    @GetMapping("/status")
    public ApiResponse<UploadStatusResult> getUploadStatus(
            @RequestParam("file_md5") String fileMd5,
            @RequestAttribute("userId") Long userId
    ) {
        return ApiResponse.success(uploadService.getUploadStatus(fileMd5, String.valueOf(userId)));
    }

    @PostMapping("/merge")
    public ApiResponse<MergeResult> mergeChunks(
            @RequestBody MergeRequest request,
            @RequestAttribute("userId") Long userId
    ) {
        String objectUrl = uploadService.mergeChunks(request.fileMd5(), request.fileName(), String.valueOf(userId));
        FileUpload fileUpload = fileUploadRepository.findByFileMd5AndUserId(request.fileMd5(), String.valueOf(userId))
                .orElseThrow(() -> new BusinessException(4042, "上传记录不存在"));
        FileProcessingTask task = new FileProcessingTask(
                request.fileMd5(),
                objectUrl,
                request.fileName(),
                String.valueOf(userId),
                fileUpload.getOrgTag(),
                fileUpload.isPublic()
        );
        kafkaTemplate.send(fileProcessingTopic, task);
        return ApiResponse.success(new MergeResult(objectUrl));
    }
}
