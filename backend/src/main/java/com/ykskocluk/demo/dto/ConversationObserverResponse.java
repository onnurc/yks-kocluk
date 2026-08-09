package com.ykskocluk.demo.dto;

public record ConversationObserverResponse(String type, String displayName, boolean readOnly) {
    public static ConversationObserverResponse platformAdmin() {
        return new ConversationObserverResponse("ADMIN", "Uniform Akademi Admin", true);
    }
}
