package com.ykskocluk.demo.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Locale;

class EmailAddressesTest {
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "plain-text", "@gmail.com", "emre@.com", "emre@gmail",
            "emre@gmail.", "emre..test@gmail.com", "emre@gmail..com"})
    void malformedAddressesAreRejected(String email) {
        assertThat(EmailAddresses.isValid(email)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"emre@gmail.com", "selin.kacar@hotmail.com", "emre_123@icloud.com",
            "selin+test@gmail.com", "ad.soyad@ogrenci.medipol.edu.tr", "user@example.international"})
    void structurallyValidAddressesAreAccepted(String email) {
        assertThat(EmailAddresses.isValid(email)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"  Emre@GMAIL.COM  ", "SELIN+TEST@Example.International"})
    void normalizationTrimsAndLowercases(String email) {
        assertThat(EmailAddresses.normalize(email)).isEqualTo(email.trim().toLowerCase(Locale.ROOT));
    }
}
