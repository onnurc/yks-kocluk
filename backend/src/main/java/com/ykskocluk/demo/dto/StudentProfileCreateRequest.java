package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record StudentProfileCreateRequest(

        @Size(max = 30, message = "Sınıf bilgisi en fazla 30 karakter olabilir")
        @Pattern(regexp = "(?:9|10|11|12)\\. Sınıf", message = "Sınıf düzeyi 9, 10, 11 veya 12. Sınıf olmalı")
        String gradeLevel,

        @Size(max = 100, message = "Şehir en fazla 100 karakter olabilir")
        String city
) {
}
