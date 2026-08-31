package com.ykskocluk.demo.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AfterCommitStorageDeletionServiceTest {
    @Mock RetiredMediaStorageCleanupService cleanup;
    AfterCommitStorageDeletionService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        service = new AfterCommitStorageDeletionService(cleanup);
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
        service.deleteAfterCommit(7L, "public/old.jpg");
        service.deleteAfterCommit(7L, "public/old.jpg");
        verify(cleanup, never()).deleteSafely(7L, "public/old.jpg");

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        verify(cleanup, times(1)).deleteSafely(7L, "public/old.jpg");
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }

    @Test
    void rollbackNeverDeletesTheStorageObject() {
        service.deleteAfterCommit(8L, "public/kept.jpg");

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(cleanup, never()).deleteSafely(8L, "public/kept.jpg");
    }

    @Test
    void noTransactionDelegatesToRetrySafeCleanupImmediately() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        service.deleteAfterCommit(9L, "public/orphan.jpg");
        verify(cleanup).deleteSafely(9L, "public/orphan.jpg");
    }
}
