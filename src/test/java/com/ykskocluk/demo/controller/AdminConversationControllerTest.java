package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.security.JwtService;
import com.ykskocluk.demo.service.AdminConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice for {@link AdminConversationController}. Unlike HealthControllerTest, filters are
 * NOT disabled and {@code @PreAuthorize} is genuinely enabled (imported {@link EnableMethodSecurity}
 * config), so the 403s below are enforced by method security — not a slice that silently skips it.
 * The differential (ADMIN → 200 vs STUDENT/COACH → 403 on the SAME endpoint) is the proof: were
 * {@code @PreAuthorize} inert, STUDENT would also get 200. Real-JWT enforcement through the actual
 * SecurityConfig is additionally covered by AdminConversationIntegrationTest.
 */
@WebMvcTest(AdminConversationController.class)
@Import(AdminConversationControllerTest.MethodSecurityTestConfig.class)
class AdminConversationControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminConversationService adminConversationService;

    // JwtAuthenticationFilter (a @Component Filter) is pulled into the web slice; mock its
    // dependency so the context loads. No bearer token is sent — auth comes from @WithMockUser.
    @MockitoBean
    JwtService jwtService;

    private static <T> PageResponse<T> emptyPage() {
        return new PageResponse<>(List.of(), 0, 20, 0, 0, true);
    }

    // --- authorization: @PreAuthorize("hasRole('ADMIN')") really fires ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_canListConversations() throws Exception {
        when(adminConversationService.listConversations(any())).thenReturn(emptyPage());

        mockMvc.perform(get("/api/v1/admin/conversations"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void student_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/conversations"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COACH")
    void coach_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/conversations"))
                .andExpect(status().isForbidden());
    }

    // Unauthenticated → 401 is asserted in AdminConversationIntegrationTest against the real
    // SecurityConfig + ProblemDetailAuthenticationEntryPoint; the @WebMvcTest default chain
    // redirects (302) instead, so the meaningful 401 check lives in the integration test.

    // --- 404: non-existent conversation → ProblemDetail, no participant leakage ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void messages_nonExistentConversation_notFound() throws Exception {
        when(adminConversationService.getMessages(eq(999L), any()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "Konuşma bulunamadı"));

        mockMvc.perform(get("/api/v1/admin/conversations/999/messages"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CONVERSATION_NOT_FOUND"))
                // no participant data leaked in the not-found body
                .andExpect(jsonPath("$.student").doesNotExist())
                .andExpect(jsonPath("$.coach").doesNotExist());
    }

    // --- 400: sort field outside the whitelist is rejected ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_invalidSortField_badRequest() throws Exception {
        when(adminConversationService.listConversations(any()))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SORT_FIELD",
                        "Bu alana göre sıralama yapılamaz: content"));

        mockMvc.perform(get("/api/v1/admin/conversations").param("sort", "content"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_SORT_FIELD"));
    }
}
