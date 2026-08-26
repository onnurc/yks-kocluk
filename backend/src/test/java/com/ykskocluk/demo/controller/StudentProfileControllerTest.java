package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.StudentProfileResponse;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.security.JwtService;
import com.ykskocluk.demo.service.StudentProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudentProfileController.class)
@Import(StudentProfileControllerTest.MethodSecurityTestConfig.class)
class StudentProfileControllerTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean StudentProfileService studentProfileService;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserRepository userRepository;

    @Test
    void getOwn_googleCreatedStudentReturnsProvisionedProfile() throws Exception {
        StudentProfileResponse profile = new StudentProfileResponse(
                12L, 42L, "Google Student", "google@example.com",
                null, null, null, null, null, null, null, null, null);
        when(studentProfileService.getOwn(42L)).thenReturn(profile);
        var auth = new UsernamePasswordAuthenticationToken(
                42L, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));

        mockMvc.perform(get("/api/v1/student/profile/me").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(42L))
                .andExpect(jsonPath("$.email").value("google@example.com"));
    }

    @Test
    void getOwn_nonStudentRemainsForbidden() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                7L, null, List.of(new SimpleGrantedAuthority("ROLE_COACH")));

        mockMvc.perform(get("/api/v1/student/profile/me").with(authentication(auth)))
                .andExpect(status().isForbidden());
    }
}
