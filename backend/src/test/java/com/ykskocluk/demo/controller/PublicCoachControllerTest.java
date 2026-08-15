package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.CoachSummaryResponse;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.enums.Track;
import com.ykskocluk.demo.security.JwtAuthenticationFilter;
import com.ykskocluk.demo.service.CoachSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicCoachController.class)
@AutoConfigureMockMvc(addFilters = false)
class PublicCoachControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean CoachSearchService coachSearchService;
    @MockitoBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void exposesOnlyPublicCoachSummaryFieldsAndPreservesFilters() throws Exception {
        CoachSummaryResponse coach = new CoachSummaryResponse(11L, "Ayşe Yılmaz", "YKS Mentörü",
                "Boğaziçi Üniversitesi", Set.of(Track.NUMERICAL), null, 0, true,
                "https://media.example/coach.jpg", null);
        given(coachSearchService.search(eq(null), eq(Track.NUMERICAL), eq("matematik"), any()))
                .willReturn(new PageResponse<>(List.of(coach), 0, 9, 1, 1, true));

        mockMvc.perform(get("/api/v1/public/coaches")
                        .param("track", "NUMERICAL")
                        .param("q", "matematik")
                        .param("page", "0")
                        .param("size", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(11))
                .andExpect(jsonPath("$.content[0].fullName").value("Ayşe Yılmaz"))
                .andExpect(jsonPath("$.content[0].profileImageUrl").value("https://media.example/coach.jpg"))
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.content[0].phone").doesNotExist())
                .andExpect(jsonPath("$.content[0].status").doesNotExist())
                .andExpect(jsonPath("$.content[0].rejectionReason").doesNotExist());
    }
}
