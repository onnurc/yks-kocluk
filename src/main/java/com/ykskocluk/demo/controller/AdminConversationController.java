package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.ConversationSummaryResponse;
import com.ykskocluk.demo.dto.MessageResponse;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.service.AdminConversationService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin oversight of messaging (Phase 5c). ADMIN-only — same P2 pattern as
 * {@link AdminCoachController} (class-level {@code @PreAuthorize} + {@code @EnableMethodSecurity};
 * there is no {@code /api/v1/admin/**} URL matcher, the annotation IS the rule). Non-admin → 403.
 *
 * <p>Read-only and invisible: viewing a thread changes nothing (no read_at / last_message_at /
 * membership writes). Reads go through {@link AdminConversationService}, never {@code MessageService}.
 */
@RestController
@RequestMapping("/api/v1/admin/conversations")
@PreAuthorize("hasRole('ADMIN')")
public class AdminConversationController {

    private final AdminConversationService adminConversationService;

    public AdminConversationController(AdminConversationService adminConversationService) {
        this.adminConversationService = adminConversationService;
    }

    @GetMapping
    public PageResponse<ConversationSummaryResponse> list(
            @PageableDefault(size = 20, sort = "lastMessageAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return adminConversationService.listConversations(pageable);
    }

    @GetMapping("/{id}/messages")
    public PageResponse<MessageResponse> messages(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return adminConversationService.getMessages(id, pageable);
    }
}
