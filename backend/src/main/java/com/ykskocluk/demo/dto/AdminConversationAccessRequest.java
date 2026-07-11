package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for logging admin access to a conversation.
 */
public record AdminConversationAccessRequest(
        @NotBlank(message = "Access reason cannot be blank")
        String reason
) {
}
