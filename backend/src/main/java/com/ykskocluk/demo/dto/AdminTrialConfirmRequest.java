package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record AdminTrialConfirmRequest(
        @NotBlank(message = "Görüşme bağlantısı zorunludur")
        @Size(max = 1000, message = "Görüşme bağlantısı en fazla 1000 karakter olabilir")
        @URL(protocol = "https", message = "Görüşme bağlantısı geçerli bir HTTPS adresi olmalıdır")
        String meetingUrl
) { }
