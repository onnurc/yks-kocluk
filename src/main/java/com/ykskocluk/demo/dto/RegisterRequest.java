package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "E-posta boş olamaz")
        @Email(message = "Geçerli bir e-posta girin")
        String email,

        @NotBlank(message = "Şifre boş olamaz")
        @Size(min = 8, max = 72, message = "Şifre 8 ile 72 karakter arasında olmalı")
        String password,

        @NotBlank(message = "Ad boş olamaz")
        @Size(max = 150, message = "Ad en fazla 150 karakter olabilir")
        String fullName,

        @NotNull(message = "Rol seçilmeli")
        Role role
) {
}
