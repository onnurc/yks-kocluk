package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MessageSendRequest(

        @NotBlank(message = "Mesaj boş olamaz")
        @Size(max = 4000, message = "Mesaj en fazla 4000 karakter olabilir")
        String content
) {
}
