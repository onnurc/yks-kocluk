package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.Track;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

import java.util.Set;

public record CoachProfileCreateRequest(

        @NotBlank(message = "Başlık boş olamaz")
        @Size(max = 150, message = "Başlık en fazla 150 karakter olabilir")
        String headline,

        @Size(max = 2000, message = "Biyografi en fazla 2000 karakter olabilir")
        String bio,

        @NotNull(message = "Üniversite seçilmeli")
        Long universityId,

        @Size(max = 150, message = "Bölüm en fazla 150 karakter olabilir")
        String department,

        @Min(value = 1950, message = "Mezuniyet yılı 1950 veya sonrası olmalı")
        Integer graduationYear,

        @NotEmpty(message = "En az bir alan seçilmeli")
        Set<Track> tracks
) {
}
