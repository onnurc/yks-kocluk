package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CoachApplicationRequest(

        @NotBlank(message = "Ad Soyad boş olamaz")
        @Size(max = 150, message = "Ad Soyad en fazla 150 karakter olabilir")
        String fullName,

        @NotBlank(message = "E-posta boş olamaz")
        @Email(message = "Geçerli bir e-posta girin")
        @Size(max = 255, message = "E-posta en fazla 255 karakter olabilir")
        String email,

        @Size(max = 30, message = "Telefon en fazla 30 karakter olabilir")
        String phone,

        @Size(max = 2000, message = "Deneyim metni en fazla 2000 karakter olabilir")
        String experience
) {
}
