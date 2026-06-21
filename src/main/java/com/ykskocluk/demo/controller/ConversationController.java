package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.ConversationCreateRequest;
import com.ykskocluk.demo.dto.ConversationResponse;
import com.ykskocluk.demo.dto.MessageResponse;
import com.ykskocluk.demo.dto.MessageSendRequest;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST messaging (Phase 5a). Real-time delivery is layered on in Phase 5b over the same
 * {@link MessageService}. Conversation membership and the child-safety gate are enforced
 * server-side in the service, never by role or path alone.
 */
@RestController
@RequestMapping("/api/v1/conversations")
@PreAuthorize("hasAnyRole('STUDENT', 'COACH')")
public class ConversationController {

    private final MessageService messageService;

    public ConversationController(MessageService messageService) {
        this.messageService = messageService;
    }

    /** Student-only initiation (open-or-get). */
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ConversationResponse> open(@AuthenticationPrincipal Long studentUserId,
                                                     @Valid @RequestBody ConversationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messageService.openConversation(studentUserId, request.coachId()));
    }

    @GetMapping
    public List<ConversationResponse> myConversations(@AuthenticationPrincipal Long userId) {
        return messageService.myConversations(userId);
    }

    @GetMapping("/{id}/messages")
    public PageResponse<MessageResponse> history(@AuthenticationPrincipal Long userId,
                                                 @PathVariable Long id,
                                                 @PageableDefault(size = 30) Pageable pageable) {
        return messageService.history(userId, id, pageable);
    }

    /** REST send fallback — same gate+persist path the STOMP handler will use in 5b. */
    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageResponse> send(@AuthenticationPrincipal Long userId,
                                                @PathVariable Long id,
                                                @Valid @RequestBody MessageSendRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messageService.sendMessage(userId, id, request.content()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        messageService.markRead(userId, id);
        return ResponseEntity.noContent().build();
    }
}
