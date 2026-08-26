package com.ykskocluk.demo.service;

import com.ykskocluk.demo.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AfterCommitStorageDeletionServiceTest {
    @Mock StorageService storage;
    AfterCommitStorageDeletionService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        service = new AfterCommitStorageDeletionService(storage);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void commitDeletesOnlyAfterCommitAndCoalescesDuplicateKeys() {
        service.deleteAfterCommit("public/old.jpg");
        service.deleteAfterCommit("public/old.jpg");
        verify(storage, never()).deleteObject("public/old.jpg");

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        verify(storage, times(1)).deleteObject("public/old.jpg");
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }

    @Test
    void rollbackNeverDeletesTheStorageObject() {
        service.deleteAfterCommit("public/kept.jpg");

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(storage, never()).deleteObject("public/kept.jpg");
    }

    @Test
    void afterCommitStorageFailureIsHandledWithoutEscapingCommitCallback() {
        doThrow(new IllegalStateException("R2 unavailable")).when(storage).deleteObject("public/orphan.jpg");
        service.deleteAfterCommit("public/orphan.jpg");

        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
    }
}
