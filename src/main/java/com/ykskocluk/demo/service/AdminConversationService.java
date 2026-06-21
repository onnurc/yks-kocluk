package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.ConversationSummaryResponse;
import com.ykskocluk.demo.dto.ConversationSummaryResponse.CoachRef;
import com.ykskocluk.demo.dto.ConversationSummaryResponse.StudentRef;
import com.ykskocluk.demo.dto.MessageResponse;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.entity.Conversation;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.MessageMapper;
import com.ykskocluk.demo.repository.ConversationMessageCount;
import com.ykskocluk.demo.repository.ConversationRepository;
import com.ykskocluk.demo.repository.MessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin oversight reads for messaging (Phase 5c) — child-safety visibility.
 *
 * <p><strong>Physically separate from {@link MessageService} by design.</strong> Participants
 * (STUDENT/COACH) only ever go through {@code MessageService} and its membership gate; admins only
 * ever go through this service, which reads <em>directly</em> from the repositories with no gate.
 * This class MUST NOT depend on or call {@code MessageService}, and {@code MessageService} MUST NOT
 * grow {@code ...ForAdmin} methods.
 *
 * <p>All methods are {@code @Transactional(readOnly = true)} and strictly <em>invisible</em>: an
 * admin read never touches {@code read_at}, {@code last_message_at}, or any membership state.
 */
@Service
public class AdminConversationService {

    private static final Set<String> CONVERSATION_SORT_FIELDS = Set.of("lastMessageAt", "createdAt");
    private static final Set<String> MESSAGE_SORT_FIELDS = Set.of("createdAt");

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageMapper messageMapper;

    public AdminConversationService(ConversationRepository conversationRepository,
                                    MessageRepository messageRepository,
                                    MessageMapper messageMapper) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.messageMapper = messageMapper;
    }

    /**
     * All conversations, newest activity first (default), independent of subscription/user status.
     * One data query (participants fetched via entity graph) + one aggregate for the page's counts.
     */
    @Transactional(readOnly = true)
    public PageResponse<ConversationSummaryResponse> listConversations(Pageable pageable) {
        validateSort(pageable, CONVERSATION_SORT_FIELDS);

        Page<Conversation> page = conversationRepository.findAllForAdmin(pageable);

        List<Long> ids = page.getContent().stream().map(Conversation::getId).toList();
        Map<Long, Long> counts = ids.isEmpty()
                ? Map.of()
                : messageRepository.countByConversationIdIn(ids).stream()
                        .collect(Collectors.toMap(
                                ConversationMessageCount::getConversationId,
                                ConversationMessageCount::getMessageCount));

        return PageResponse.from(page.map(c -> toSummary(c, counts.getOrDefault(c.getId(), 0L))));
    }

    /**
     * A single conversation's messages (admin). 404 if the conversation does not exist —
     * a not-found shape with no participant leakage. Ordering = the caller's whitelisted Pageable
     * (defaulted {@code createdAt} DESC, matching the participant history view).
     */
    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> getMessages(Long conversationId, Pageable pageable) {
        validateSort(pageable, MESSAGE_SORT_FIELDS);

        if (!conversationRepository.existsById(conversationId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "Konuşma bulunamadı");
        }

        return PageResponse.from(
                messageRepository.findByConversationId(conversationId, pageable)
                        .map(messageMapper::toResponse));
    }

    // Inline mapping (not MapStruct): the count is computed separately and merged here, and the
    // nested refs are trivial — keeps the per-page count merge in one obvious place.
    private ConversationSummaryResponse toSummary(Conversation c, long messageCount) {
        return new ConversationSummaryResponse(
                c.getId(),
                new StudentRef(c.getStudent().getId(), c.getStudent().getFullName()),
                new CoachRef(c.getCoachProfile().getId(),
                        c.getCoachProfile().getUser().getFullName(),
                        c.getCoachProfile().getUniversity().getName()),
                c.getLastMessageAt(),
                messageCount);
    }

    private void validateSort(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SORT_FIELD",
                        "Bu alana göre sıralama yapılamaz: " + order.getProperty());
            }
        }
    }
}
