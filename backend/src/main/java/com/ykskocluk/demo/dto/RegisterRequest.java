package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "E-posta boş olamaz")
        @Email(message = "Geçerli bir e-posta girin")
        @Size(max = 255, message = "E-posta en fazla 255 karakter olabilir")
        String email,

        @NotBlank(message = "Şifre boş olamaz")
        @Size(min = 12, max = 72, message = "Şifre 12 ile 72 karakter arasında olmalı")
        String password,

        @NotBlank(message = "Ad boş olamaz")
        @Size(max = 150, message = "Ad en fazla 150 karakter olabilir")
        String fullName,

        @NotNull(message = "Rol seçilmeli")
        Role role,

        java.time.LocalDate dateOfBirth,
        Long acceptedTermsDocumentId,
        Long acceptedExplicitConsentDocumentId,
        Boolean marketingEmailOptIn,
        Boolean marketingSmsOptIn
) {
    public RegisterRequest(String email, String password, String fullName, Role role) {
        this(email, password, fullName, role, null, null, null, false, false);
    }

    public RegisterRequest(String email, String password, String fullName, Role role, java.time.LocalDate dateOfBirth) {
        this(email, password, fullName, role, dateOfBirth, null, null, false, false);
    }
}
