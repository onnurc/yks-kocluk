package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.Size;

public record StudentProfileCreateRequest(

        @Size(max = 30, message = "Sınıf bilgisi en fazla 30 karakter olabilir")
        String gradeLevel,

        @Size(max = 100, message = "Şehir en fazla 100 karakter olabilir")
        String city
) {
}
