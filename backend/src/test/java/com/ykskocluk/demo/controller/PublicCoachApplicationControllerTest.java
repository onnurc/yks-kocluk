package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.security.JwtAuthenticationFilter;
import com.ykskocluk.demo.security.ratelimit.AuthRateLimitService;
import com.ykskocluk.demo.service.CoachApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicCoachApplicationController.class)
@AutoConfigureMockMvc(addFilters = false)
class PublicCoachApplicationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CoachApplicationService service;
    @MockitoBean AuthRateLimitService rateLimitService;
    @MockitoBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void invalidEmailDoesNotReachApplicationService() throws Exception {
        mvc.perform(post("/api/v1/public/coach-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Aday Koç\",\"email\":\"emre@gmail..com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verifyNoInteractions(service, rateLimitService);
    }

    @Test
    void validSubdomainAndPlusAddressReachesRealSubmissionFlow() throws Exception {
        mvc.perform(post("/api/v1/public/coach-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Aday Koç\",\"email\":\"selin+coach@ogrenci.medipol.edu.tr\"}"))
                .andExpect(status().isCreated());
        verify(service).submit(any());
    }
}
