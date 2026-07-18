package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.dto.ReportCreateRequest;
import com.ykskocluk.demo.dto.ReportResponse;
import com.ykskocluk.demo.entity.Report;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.entity.Conversation;
import com.ykskocluk.demo.entity.Message;
import com.ykskocluk.demo.enums.ReportStatus;
import com.ykskocluk.demo.enums.ReportTargetType;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.ReportRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Service for submitting safety reports and retrieving reports (admin).
 */
@Service
public class ReportService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "id");

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final com.ykskocluk.demo.repository.ConversationRepository conversationRepository;
    private final com.ykskocluk.demo.repository.MessageRepository messageRepository;

    public ReportService(ReportRepository reportRepository,
                         UserRepository userRepository,
                         com.ykskocluk.demo.repository.ConversationRepository conversationRepository,
                         com.ykskocluk.demo.repository.MessageRepository messageRepository) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public ReportResponse createReport(Long reporterUserId, ReportCreateRequest request) {
        User reporter = userRepository.findById(reporterUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));

        // Validate targets
        if (request.targetType() == ReportTargetType.USER) {
            if (reporterUserId.equals(request.targetId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_REPORT_SELF", "Kendinizi rapor edemezsiniz");
            }
            if (!userRepository.existsById(request.targetId())) {
                throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Bildirilen kullanıcı bulunamadı");
            }
        } else if (request.targetType() == ReportTargetType.CONVERSATION) {
            Conversation conversation = conversationRepository.findById(request.targetId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "Bildirilen konuşma bulunamadı"));

            boolean isParticipant = conversation.getStudent().getId().equals(reporterUserId) ||
                                    conversation.getCoachProfile().getUser().getId().equals(reporterUserId);
            if (!isParticipant) {
                throw new ApiException(HttpStatus.FORBIDDEN, "NOT_CONVERSATION_PARTICIPANT", "Bu konuşmanın katılımcısı değilsiniz");
            }
        } else if (request.targetType() == ReportTargetType.MESSAGE) {
            Message message = messageRepository.findById(request.targetId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "Bildirilen mesaj bulunamadı"));

            Conversation conversation = message.getConversation();
            boolean isParticipant = conversation.getStudent().getId().equals(reporterUserId) ||
                                    conversation.getCoachProfile().getUser().getId().equals(reporterUserId);
            if (!isParticipant) {
                throw new ApiException(HttpStatus.FORBIDDEN, "NOT_CONVERSATION_PARTICIPANT", "Bu mesajın konuşmasının katılımcısı değilsiniz");
            }
        }

        Report report = new Report();
        report.setReporter(reporter);
        report.setTargetType(request.targetType());
        report.setTargetId(request.targetId());
        report.setReason(request.reason());
        report.setDetails(request.details());
        report.setStatus(ReportStatus.OPEN);

        reportRepository.saveAndFlush(report);

        return toResponse(report);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportResponse> listReports(ReportStatus status, Pageable pageable) {
        validateSort(pageable);
        Page<Report> page;
        if (status != null) {
            page = reportRepository.findByStatus(status, pageable);
        } else {
            page = reportRepository.findAll(pageable);
        }
        return PageResponse.from(page.map(this::toResponse));
    }

    private void validateSort(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SORT_FIELD",
                        "Bu alana göre sıralama yapılamaz: " + order.getProperty());
            }
        }
    }

    private ReportResponse toResponse(Report r) {
        return new ReportResponse(
                r.getId(),
                r.getReporter().getId(),
                r.getTargetType(),
                r.getTargetId(),
                r.getReason(),
                r.getDetails(),
                r.getStatus(),
                r.getCreatedAt(),
                r.getReviewedAt(),
                r.getReviewedBy() != null ? r.getReviewedBy().getId() : null
        );
    }
}
