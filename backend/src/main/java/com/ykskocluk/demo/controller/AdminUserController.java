package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.SuspendRequest;
import com.ykskocluk.demo.dto.SuspendResponse;
import com.ykskocluk.demo.integration.MailClient;
import com.ykskocluk.demo.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for user administrative actions (suspend, etc.), restricted to ADMIN users.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

    private final UserService userService;
    private final MailClient mailClient;

    public AdminUserController(UserService userService, MailClient mailClient) {
        this.userService = userService;
        this.mailClient = mailClient;
    }

    @PostMapping("/{id}/suspend")
    public SuspendResponse suspendUser(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long id,
            @RequestBody(required = false) SuspendRequest request) {
        String reason = (request != null) ? request.reason() : null;
        SuspendResponse response = userService.suspendUser(adminUserId, id, reason);
        try {
            mailClient.sendUserSuspended(response.email());
        } catch (Exception e) {
            // Best-effort: suspension is already committed; never let a mail failure surface.
            log.error("User-suspended email failed for user {}: {}", id, e.getMessage(), e);
        }
        return response;
    }
}
