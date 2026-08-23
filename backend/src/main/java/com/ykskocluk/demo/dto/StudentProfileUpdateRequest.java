package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.ExamSession;
import com.ykskocluk.demo.enums.Track;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StudentProfileUpdateRequest(

        @Size(max = 30, message = "Sınıf bilgisi en fazla 30 karakter olabilir")
        @Pattern(regexp = "(?:9|10|11|12)\\. Sınıf", message = "Sınıf düzeyi 9, 10, 11 veya 12. Sınıf olmalı")
        String gradeLevel,

        @Size(max = 100, message = "Şehir en fazla 100 karakter olabilir")
        String city,

        @Min(value = 2024, message = "Sınav yılı 2024 veya sonrası olmalı")
        @Max(value = 2100, message = "Sınav yılı 2100 veya öncesi olmalı")
        Integer examYear,

        Track yksScoreType,

        ExamSession examSession,

        @Size(max = 200, message = "Hedef üniversite en fazla 200 karakter olabilir")
        String targetUniversity,

        @Size(max = 150, message = "Hedef bölüm en fazla 150 karakter olabilir")
        String targetDepartment
) {
}
