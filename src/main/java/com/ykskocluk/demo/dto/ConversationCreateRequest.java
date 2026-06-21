package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotNull;

public record ConversationCreateRequest(

        @NotNull(message = "Koç seçilmeli")
        Long coachId
) {
}
