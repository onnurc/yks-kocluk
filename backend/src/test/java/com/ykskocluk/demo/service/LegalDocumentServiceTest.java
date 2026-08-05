package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.LegalDocument;
import com.ykskocluk.demo.enums.LegalDocumentStatus;
import com.ykskocluk.demo.enums.LegalDocumentType;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.LegalDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegalDocumentServiceTest {
    @Mock LegalDocumentRepository repository;

    @Test
    void kvkkNotice_currentPublished_isPubliclyReadableWithoutAcceptance() {
        LegalDocument document = new LegalDocument();
        document.setType(LegalDocumentType.KVKK_NOTICE);
        document.setDocumentVersion("1.0");
        document.setTitle("KVKK");
        document.setContent("placeholder");
        document.setContentHash("a".repeat(64));
        document.setEffectiveAt(Instant.now());
        document.setRequiredForRegistration(false);
        when(repository.findFirstByTypeAndStatusAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                eq(LegalDocumentType.KVKK_NOTICE), eq(LegalDocumentStatus.PUBLISHED), any())).thenReturn(Optional.of(document));

        var response = new LegalDocumentService(repository).current(LegalDocumentType.KVKK_NOTICE);
        assertThat(response.type()).isEqualTo(LegalDocumentType.KVKK_NOTICE);
        assertThat(document.isRequiredForRegistration()).isFalse();
    }

    @Test
    void draftOrRetiredDocument_isNotExposedAsCurrent() {
        when(repository.findFirstByTypeAndStatusAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                eq(LegalDocumentType.TERMS_OF_USE), eq(LegalDocumentStatus.PUBLISHED), any())).thenReturn(Optional.empty());
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> new LegalDocumentService(repository).current(LegalDocumentType.TERMS_OF_USE));
        assertThat(ex.getErrorCode()).isEqualTo("LEGAL_DOCUMENT_NOT_FOUND");
    }

    private static <T> T eq(T value) { return org.mockito.ArgumentMatchers.eq(value); }
}
