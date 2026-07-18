package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.HealthResponse;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.security.JwtService;
import com.ykskocluk.demo.service.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice test for {@link HealthController} with the service mocked.
 * Security filters are disabled — this is a pure web-layer check; auth/security
 * behavior is covered by AuthIntegrationTest. Uses @MockitoBean (SB4).
 */
@WebMvcTest(HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
class HealthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    HealthService healthService;

    // The JwtAuthenticationFilter (a @Component Filter) is pulled into the web slice;
    // mock its JwtService/UserRepository dependencies so the context loads (filters
    // are disabled here, so these are never actually invoked).
    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    UserRepository userRepository;

    @Test
    void returnsHealthStatus() throws Exception {
        when(healthService.check()).thenReturn(new HealthResponse("UP"));

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
