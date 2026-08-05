package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.LegalDocumentResponse;
import com.ykskocluk.demo.enums.LegalDocumentType;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.security.JwtService;
import com.ykskocluk.demo.security.SecurityConfig;
import com.ykskocluk.demo.service.LegalDocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LegalDocumentController.class)
@Import({SecurityConfig.class, com.ykskocluk.demo.security.JwtAuthenticationFilter.class})
class LegalDocumentControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean LegalDocumentService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean com.ykskocluk.demo.security.ProblemDetailAuthenticationEntryPoint authenticationEntryPoint;
    @MockitoBean com.ykskocluk.demo.security.ProblemDetailAccessDeniedHandler accessDeniedHandler;
    @MockitoBean com.ykskocluk.demo.security.OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    @Test
    void currentKvkkNotice_isReadableWithoutAuthentication() throws Exception {
        when(service.current(LegalDocumentType.KVKK_NOTICE)).thenReturn(new LegalDocumentResponse(
                1L, LegalDocumentType.KVKK_NOTICE, "1.0", "KVKK", "placeholder", "a".repeat(64), Instant.now()));

        mockMvc.perform(get("/api/v1/legal-documents/KVKK_NOTICE/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("KVKK_NOTICE"))
                .andExpect(jsonPath("$.version").value("1.0"));
    }

    @Test
    void currentDraftOrRetiredDocument_isNotExposed() throws Exception {
        when(service.current(LegalDocumentType.TERMS_OF_USE)).thenThrow(new ApiException(
                HttpStatus.NOT_FOUND, "LEGAL_DOCUMENT_NOT_FOUND", "Güncel hukuki doküman bulunamadı"));

        mockMvc.perform(get("/api/v1/legal-documents/TERMS_OF_USE/current"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LEGAL_DOCUMENT_NOT_FOUND"));
    }

    @Test
    void currentCheckoutDocuments_arePubliclyReadable() throws Exception {
        LegalDocumentType[] types = {
                LegalDocumentType.PRE_INFORMATION_FORM,
                LegalDocumentType.DISTANCE_SALES_AGREEMENT,
                LegalDocumentType.REFUND_CANCELLATION_POLICY
        };
        for (int i = 0; i < types.length; i++) {
            LegalDocumentType type = types[i];
            when(service.current(type)).thenReturn(new LegalDocumentResponse(
                    6L + i, type, "1.0", type.name(), "placeholder", "a".repeat(64), Instant.now()));
            mockMvc.perform(get("/api/v1/legal-documents/{type}/current", type))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value(type.name()))
                    .andExpect(jsonPath("$.version").value("1.0"));
        }
    }
}
