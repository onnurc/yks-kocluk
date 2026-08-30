package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.ykskocluk.demo.validation.StrictEmail;

public record RegisterRequest(

        @NotBlank(message = "E-posta boş olamaz")
        @StrictEmail
        @Size(max = 255, message = "E-posta en fazla 255 karakter olabilir")
        String email,

        @NotBlank(message = "Şifre boş olamaz")
        @Size(min = 12, max = 72, message = "Şifre 12 ile 72 karakter arasında olmalı")
        String password,

        @NotBlank(message = "Ad boş olamaz")
        @Size(max = 150, message = "Ad en fazla 150 karakter olabilir")
        String fullName,

        java.time.LocalDate dateOfBirth,
        Long acceptedTermsDocumentId,
        Long acceptedExplicitConsentDocumentId,
        Boolean marketingEmailOptIn,
        Boolean marketingSmsOptIn
) {
    public RegisterRequest(String email, String password, String fullName) {
        this(email, password, fullName, null, null, null, false, false);
    }

    public RegisterRequest(String email, String password, String fullName, java.time.LocalDate dateOfBirth) {
        this(email, password, fullName, dateOfBirth, null, null, false, false);
    }
}
