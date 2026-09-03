package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.PublicPackageResponse;
import com.ykskocluk.demo.enums.PackageType;
import com.ykskocluk.demo.security.JwtAuthenticationFilter;
import com.ykskocluk.demo.service.PackageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicPackageController.class)
@AutoConfigureMockMvc(addFilters = false)
class PublicPackageControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PackageService packageService;
    @MockitoBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void exposesOnlyThePublicMarketingProjection() throws Exception {
        given(packageService.listPublicActive()).willReturn(List.of(
                new PublicPackageResponse(7L, PackageType.THREE_MONTHS, "3 Aylık", true, 3, null,
                        new BigDecimal("8990.00"), new BigDecimal("7990.00"), true,
                        "Dönem Kampanyası", null, 1, 4, 5)));

        mockMvc.perform(get("/api/v1/public/packages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].name").value("3 Aylık"))
                .andExpect(jsonPath("$[0].weeklyMeetingsPerMonth").value(4))
                .andExpect(jsonPath("$[0].durationMonths").value(3))
                .andExpect(jsonPath("$[0].effectivePrice").value(7990.00))
                .andExpect(jsonPath("$[0].active").doesNotExist())
                .andExpect(jsonPath("$[0].version").doesNotExist())
                .andExpect(jsonPath("$[0].discountValue").doesNotExist())
                .andExpect(jsonPath("$[0].providerProductId").doesNotExist());
    }
}
