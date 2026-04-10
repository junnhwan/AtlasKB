package io.hwan.atlaskb.chat.controller;

import io.hwan.atlaskb.chat.dto.ConversationMessage;
import io.hwan.atlaskb.chat.dto.ConversationSelectionRequest;
import io.hwan.atlaskb.chat.dto.ConversationSelectionResult;
import io.hwan.atlaskb.chat.dto.ConversationSessionSummary;
import io.hwan.atlaskb.chat.service.ConversationQueryService;
import io.hwan.atlaskb.chat.service.ConversationSessionService;
import io.hwan.atlaskb.common.api.ApiResponse;
import io.hwan.atlaskb.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/conversation")
public class ConversationController {

    private final ConversationQueryService conversationQueryService;
    private final ConversationSessionService conversationSessionService;

    public ConversationController(
            ConversationQueryService conversationQueryService,
            ConversationSessionService conversationSessionService
    ) {
        this.conversationQueryService = conversationQueryService;
        this.conversationSessionService = conversationSessionService;
    }

    @GetMapping
    public ApiResponse<List<ConversationMessage>> getConversationHistory(
            @RequestParam(name = "conversation_id", required = false) String conversationId,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(
                conversationQueryService.getConversationHistory(resolveUserId(httpServletRequest), conversationId)
        );
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ConversationSessionSummary>> getConversationSessions(HttpServletRequest httpServletRequest) {
        return ApiResponse.success(
                conversationQueryService.getConversationSessions(resolveUserId(httpServletRequest))
        );
    }

    @PostMapping("/session/select")
    public ApiResponse<ConversationSelectionResult> selectConversationSession(
            @RequestBody ConversationSelectionRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ApiResponse.success(
                conversationSessionService.selectConversation(resolveUserId(httpServletRequest), request.conversationId())
        );
    }

    private String resolveUserId(HttpServletRequest httpServletRequest) {
        Object userId = httpServletRequest.getAttribute("userId");
        if (!(userId instanceof Long)) {
            throw new BusinessException(4011, "Unauthorized");
        }
        return String.valueOf(userId);
    }
}
