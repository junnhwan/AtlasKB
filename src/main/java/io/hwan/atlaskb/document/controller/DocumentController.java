package io.hwan.atlaskb.document.controller;

import io.hwan.atlaskb.common.api.ApiResponse;
import io.hwan.atlaskb.common.exception.BusinessException;
import io.hwan.atlaskb.document.dto.DocumentFileSummary;
import io.hwan.atlaskb.document.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/uploads")
    public ApiResponse<List<DocumentFileSummary>> getUserUploadedFiles(HttpServletRequest httpServletRequest) {
        Object userId = httpServletRequest.getAttribute("userId");
        if (!(userId instanceof Long)) {
            throw new BusinessException(4011, "Unauthorized");
        }

        return ApiResponse.success(documentService.getUserUploadedFiles(String.valueOf(userId)));
    }
}
