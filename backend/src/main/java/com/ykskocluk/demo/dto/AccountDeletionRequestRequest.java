package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record AccountDeletionRequestRequest(
        @NotBlank(message = "Hesap silme onayı zorunludur") String confirmation
) { }
