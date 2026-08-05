package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.*;
import com.ykskocluk.demo.enums.AccountDeletionStatus;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.security.JwtService;
import com.ykskocluk.demo.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PrivacyController.class)
class PrivacyControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean MarketingPreferenceService marketingPreferenceService;
    @MockitoBean PrivacyPreferenceService privacyPreferenceService;
    @MockitoBean LegalAcceptanceService legalAcceptanceService;
    @MockitoBean AccountDeletionService accountDeletionService;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserRepository userRepository;

    @Test
    void endpointWithoutAuthentication_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/privacy/preferences"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void marketingUpdateUsesOnlyAuthenticatedPrincipal() throws Exception {
        when(marketingPreferenceService.update(eq(7L), eq(new MarketingPreferencesUpdateRequest(true, false))))
                .thenReturn(new MarketingPreferencesResponse(
                        new MarketingChannelPreferenceResponse(true, Instant.now(), null),
                        new MarketingChannelPreferenceResponse(false, null, Instant.now())));

        mockMvc.perform(patch("/api/v1/privacy/marketing-preferences")
                        .with(authentication(authenticated(7L))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":true,\"sms\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email.granted").value(true))
                .andExpect(jsonPath("$.sms.granted").value(false));
        verify(marketingPreferenceService).update(7L, new MarketingPreferencesUpdateRequest(true, false));
    }

    @Test
    void deletionUsesAuthenticatedPrincipalAndConfirmationOnly() throws Exception {
        Instant now = Instant.now();
        when(accountDeletionService.requestAndComplete(eq(7L), eq(new AccountDeletionRequestRequest("DELETE"))))
                .thenReturn(new AccountDeletionResponse(AccountDeletionStatus.COMPLETED, now, now));

        mockMvc.perform(post("/api/v1/privacy/account-deletion")
                        .with(authentication(authenticated(7L))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"DELETE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        verify(accountDeletionService).requestAndComplete(7L, new AccountDeletionRequestRequest("DELETE"));
    }

    private UsernamePasswordAuthenticationToken authenticated(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
