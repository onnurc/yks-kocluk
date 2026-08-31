package com.ykskocluk.demo.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AfterCommitStorageDeletionService {
    private static final Object RESOURCE_KEY = AfterCommitStorageDeletionService.class.getName() + ".keys";

    private final RetiredMediaStorageCleanupService cleanup;

    public AfterCommitStorageDeletionService(RetiredMediaStorageCleanupService cleanup) {
        this.cleanup = cleanup;
    }

    /**
     * Coalesces object keys per transaction and performs irreversible storage deletion only after
     * the database commit. A rollback therefore leaves every object referenced by the database in
     * place. After-commit failures are logged and deliberately cannot roll back the committed DB.
     */
    public void deleteAfterCommit(Long assetId, String objectKey) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            cleanup.deleteSafely(assetId, objectKey);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<Long, String> keys = (Map<Long, String>) TransactionSynchronizationManager.getResource(RESOURCE_KEY);
        if (keys != null) {
            keys.putIfAbsent(assetId, objectKey);
            return;
        }

        Map<Long, String> transactionKeys = new LinkedHashMap<>();
        transactionKeys.put(assetId, objectKey);
        TransactionSynchronizationManager.bindResource(RESOURCE_KEY, transactionKeys);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                transactionKeys.forEach(cleanup::deleteSafely);
            }

            @Override
            public void afterCompletion(int status) {
                if (TransactionSynchronizationManager.hasResource(RESOURCE_KEY)) {
                    TransactionSynchronizationManager.unbindResource(RESOURCE_KEY);
                }
            }
        });
    }

}
