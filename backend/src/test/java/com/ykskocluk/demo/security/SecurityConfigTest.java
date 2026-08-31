package com.ykskocluk.demo.security;

import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.ProbeController.class, properties = {
        "app.jwt.secret=test-only-security-config-secret-0123456789",
        "app.cors.allowed-origins=http://localhost:5173,https://frontend.example.com"
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class,
        ProblemDetailAuthenticationEntryPoint.class, ProblemDetailAccessDeniedHandler.class,
        SecurityConfigTest.ProbeController.class})
class SecurityConfigTest {

    @Autowired MockMvc mvc;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    @Test
    void unauthenticatedProtectedRequestReturns401() throws Exception {
        mvc.perform(get("/api/v1/admin/probe")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/media/uploads/presign")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/trial-consultations/probe")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/coach-applications/1/approve")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/coach-applications/1/reject")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/refund-requests/1/approve")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/refund-requests/1/reject")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/trial-consultations/1/confirm")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/media/1/download-url")).andExpect(status().isUnauthorized());
    }

    @Test @WithMockUser(roles = "STUDENT")
    void studentIsDeniedFromAdminAndCoachSelfRoutes() throws Exception {
        mvc.perform(get("/api/v1/admin/probe")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/coach/probe")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/students/probe")).andExpect(status().isOk());
        assertSensitiveAdminWritesDenied();
    }

    @Test @WithMockUser(roles = "COACH")
    void coachIsDeniedFromAdminAndCanUseCoachSelfRoute() throws Exception {
        mvc.perform(get("/api/v1/admin/probe")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/coach/probe")).andExpect(status().isOk());
        assertSensitiveAdminWritesDenied();
    }

    @Test @WithMockUser(roles = "ADMIN")
    void adminCanUseAdminButNotCoachSelfRoute() throws Exception {
        mvc.perform(get("/api/v1/admin/probe")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/coach/probe")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/coach-applications/1/approve")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/coach-applications/1/reject")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/refund-requests/1/approve")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/refund-requests/1/reject")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/trial-consultations/1/confirm")).andExpect(status().isOk());
    }

    @Test
    void publicHttpEndpointsRemainReachableWithoutJwt() throws Exception {
        mvc.perform(post("/api/v1/auth/login")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/legal-documents/probe")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/payments/iyzico/webhook")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/packages")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/coaches")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/coaches/11")).andExpect(status().isOk());
    }

    @Test
    void oauth2AuthorizationStartRemainsPublicAndSessionCompatible() throws Exception {
        mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("https://accounts.google.com/**"));
    }

    @Test
    void localhostAndConfiguredProductionOriginsAreAllowed() throws Exception {
        preflight("http://localhost:5173").andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS));
        preflight("https://frontend.example.com").andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "https://frontend.example.com"));
    }

    @Test
    void hostileOriginIsRejected() throws Exception {
        preflight("https://evil.example").andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void explicitSecurityHeadersArePresentWithoutWeakeningSpringDefaults() throws Exception {
        mvc.perform(get("/api/v1/public/packages"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void swaggerRoutesAreAbsentByDefault() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }

    private void assertSensitiveAdminWritesDenied() throws Exception {
        mvc.perform(post("/api/v1/admin/coach-applications/1/approve")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/coach-applications/1/reject")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/refund-requests/1/approve")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/refund-requests/1/reject")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/trial-consultations/1/confirm")).andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions preflight(String origin) throws Exception {
        return mvc.perform(options("/api/v1/admin/probe")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"));
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/v1/admin/probe") String admin() { return "ok"; }
        @GetMapping("/api/v1/coach/probe") String coach() { return "ok"; }
        @GetMapping("/api/v1/students/probe") String student() { return "ok"; }
        @GetMapping("/api/v1/legal-documents/probe") String legal() { return "ok"; }
        @PostMapping("/api/v1/auth/login") String login() { return "ok"; }
        @PostMapping("/api/v1/payments/iyzico/webhook") String webhook() { return "ok"; }
        @GetMapping("/api/v1/health") String health() { return "ok"; }
        @GetMapping("/api/v1/public/packages") String publicPackages() { return "ok"; }
        @GetMapping("/api/v1/public/coaches") String publicCoaches() { return "ok"; }
        @GetMapping("/api/v1/public/coaches/{id}") String publicCoach() { return "ok"; }
        @PostMapping("/api/v1/media/uploads/presign") String mediaPresign() { return "ok"; }
        @PostMapping("/api/v1/trial-consultations/probe") String trialWrite() { return "ok"; }
        @PostMapping("/api/v1/admin/coach-applications/{id}/approve") String approveCoachApplication() { return "ok"; }
        @PostMapping("/api/v1/admin/coach-applications/{id}/reject") String rejectCoachApplication() { return "ok"; }
        @PostMapping("/api/v1/admin/refund-requests/{id}/approve") String approveRefund() { return "ok"; }
        @PostMapping("/api/v1/admin/refund-requests/{id}/reject") String rejectRefund() { return "ok"; }
        @PostMapping("/api/v1/admin/trial-consultations/{id}/confirm") String confirmTrial() { return "ok"; }
        @GetMapping("/api/v1/media/{id}/download-url") String downloadMedia() { return "ok"; }
    }
}
