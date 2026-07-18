package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for logging admin access to a conversation.
 */
public record AdminConversationAccessRequest(
        @NotBlank(message = "Access reason cannot be blank")
        @Size(max = 2000, message = "Reason cannot exceed 2000 characters")
        String reason
) {
}
