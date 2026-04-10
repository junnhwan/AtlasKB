package io.hwan.atlaskb.document.controller;

import io.hwan.atlaskb.common.api.ApiResponse;
import io.hwan.atlaskb.common.exception.BusinessException;
import io.hwan.atlaskb.document.dto.DocumentFileSummary;
import io.hwan.atlaskb.document.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
        return ApiResponse.success(documentService.getUserUploadedFiles(resolveUserId(httpServletRequest)));
    }

    @GetMapping("/accessible")
    public ApiResponse<List<DocumentFileSummary>> getAccessibleFiles(HttpServletRequest httpServletRequest) {
        return ApiResponse.success(documentService.getAccessibleFiles(resolveUserId(httpServletRequest)));
    }

    @DeleteMapping("/{fileMd5}")
    public ApiResponse<Void> deleteDocument(@PathVariable String fileMd5, HttpServletRequest httpServletRequest) {
        documentService.deleteDocument(fileMd5, resolveUserId(httpServletRequest), resolveRole(httpServletRequest));
        return ApiResponse.success(null);
    }

    private String resolveUserId(HttpServletRequest httpServletRequest) {
        Object userId = httpServletRequest.getAttribute("userId");
        if (!(userId instanceof Long)) {
            throw new BusinessException(4011, "Unauthorized");
        }
        return String.valueOf(userId);
    }

    private String resolveRole(HttpServletRequest httpServletRequest) {
        Object role = httpServletRequest.getAttribute("role");
        return role instanceof String ? (String) role : null;
    }
}
