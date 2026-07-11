package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UniversityCreateRequest(

        @NotBlank(message = "Üniversite adı boş olamaz")
        @Size(max = 200, message = "Üniversite adı en fazla 200 karakter olabilir")
        String name,

        @Size(max = 100, message = "Şehir en fazla 100 karakter olabilir")
        String city
) {
}
