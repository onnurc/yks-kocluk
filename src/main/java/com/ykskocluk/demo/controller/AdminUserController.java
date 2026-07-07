package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.SuspendRequest;
import com.ykskocluk.demo.dto.SuspendResponse;
import com.ykskocluk.demo.service.UserService;
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

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/{id}/suspend")
    public SuspendResponse suspendUser(
            @PathVariable Long id,
            @RequestBody(required = false) SuspendRequest request) {
        String reason = (request != null) ? request.reason() : null;
        return userService.suspendUser(id, reason);
    }
}
