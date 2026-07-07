package com.ykskocluk.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ykskocluk.demo.dto.ConsentCreateRequest;
import com.ykskocluk.demo.dto.ConsentResponse;
import com.ykskocluk.demo.enums.ConsentType;
import com.ykskocluk.demo.security.JwtService;
import com.ykskocluk.demo.service.ConsentService;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice test for {@link ConsentController}.
 */
@WebMvcTest(ConsentController.class)
class ConsentControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ConsentService consentService;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @WithMockUser(username = "1")
    void recordConsent_authenticated_success() throws Exception {
        ConsentCreateRequest request = new ConsentCreateRequest(ConsentType.KVKK, "v1.0");
        ConsentResponse response = new ConsentResponse(100L, ConsentType.KVKK, "v1.0", Instant.now());

        when(consentService.recordConsent(any(), eq(request), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/consents")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consentId").value(100))
                .andExpect(jsonPath("$.consentType").value("KVKK"))
                .andExpect(jsonPath("$.documentVersion").value("v1.0"));
    }
}
