package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccountReadinessServiceTest {
    private final LegalAcceptanceService legal = mock(LegalAcceptanceService.class);
    private final AccountReadinessService service = new AccountReadinessService(legal);

    @Test
    void unverifiedEmailWinsBeforeLegalGate() {
        User user = new User();
        user.setEmailVerified(false);
        user.setLegalOnboardingCompleted(false);

        ApiException error = catchThrowableOfType(ApiException.class, () -> service.requireReady(user));
        assertThat(error.getErrorCode()).isEqualTo("EMAIL_VERIFICATION_REQUIRED");
    }

    @Test
    void verifiedUserContinuesToIndependentLegalGate() {
        User user = new User();
        user.setEmailVerified(true);
        service.requireReady(user);
        verify(legal).requireCompleted(user);
    }
}
