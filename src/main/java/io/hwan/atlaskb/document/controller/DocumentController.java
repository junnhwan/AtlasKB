package io.hwan.atlaskb.document.controller;

import io.hwan.atlaskb.common.api.ApiResponse;
import io.hwan.atlaskb.auth.service.JwtService;
import io.hwan.atlaskb.common.exception.BusinessException;
import io.hwan.atlaskb.document.dto.DocumentDownloadInfo;
import io.hwan.atlaskb.document.dto.DocumentFileSummary;
import io.hwan.atlaskb.document.dto.DocumentPreviewInfo;
import io.hwan.atlaskb.document.service.DocumentService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final JwtService jwtService;

    public DocumentController(DocumentService documentService, JwtService jwtService) {
        this.documentService = documentService;
        this.jwtService = jwtService;
    }

    @GetMapping("/uploads")
    public ApiResponse<List<DocumentFileSummary>> getUserUploadedFiles(HttpServletRequest httpServletRequest) {
        return ApiResponse.success(documentService.getUserUploadedFiles(resolveUserId(httpServletRequest)));
    }

    @GetMapping("/accessible")
    public ApiResponse<List<DocumentFileSummary>> getAccessibleFiles(HttpServletRequest httpServletRequest) {
        return ApiResponse.success(documentService.getAccessibleFiles(resolveUserId(httpServletRequest)));
    }

    @GetMapping("/download")
    public ApiResponse<DocumentDownloadInfo> downloadFileByName(
            @RequestParam("fileName") String fileName,
            @RequestParam(name = "token", required = false) String token,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(documentService.getDownloadInfo(fileName, resolveOptionalUserId(httpServletRequest, token)));
    }

    @GetMapping("/preview")
    public ApiResponse<DocumentPreviewInfo> previewFileByName(
            @RequestParam("fileName") String fileName,
            @RequestParam(name = "token", required = false) String token,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(documentService.getPreviewInfo(fileName, resolveOptionalUserId(httpServletRequest, token)));
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

    private String resolveOptionalUserId(HttpServletRequest httpServletRequest, String token) {
        Object userId = httpServletRequest.getAttribute("userId");
        if (userId instanceof Long) {
            return String.valueOf(userId);
        }
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            Claims claims = jwtService.parseToken(token);
            return claims.getSubject();
        } catch (Exception ignored) {
            return null;
        }
    }
}
