package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CoachYoutubeIntroRequest(
        @NotBlank(message = "YouTube bağlantısı veya video kimliği zorunludur")
        @Size(max = 300, message = "YouTube bağlantısı en fazla 300 karakter olabilir")
        String youtubeUrlOrVideoId
) {
}
