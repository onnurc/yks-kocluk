package com.ykskocluk.demo.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Returns a 403 in the project's RFC 9457 ProblemDetail shape (with errorCode +
 * timestamp) when an authenticated user lacks the required role.
 */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"Forbidden","status":403,\
                "detail":"Bu işlem için yetkiniz yok","errorCode":"ACCESS_DENIED","timestamp":"%s"}\
                """.formatted(Instant.now()));
    }
}
