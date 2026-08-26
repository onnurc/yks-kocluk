package com.ykskocluk.demo.service;

import com.ykskocluk.demo.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class AfterCommitStorageDeletionService {
    private static final Logger log = LoggerFactory.getLogger(AfterCommitStorageDeletionService.class);
    private static final Object RESOURCE_KEY = AfterCommitStorageDeletionService.class.getName() + ".keys";

    private final StorageService storage;

    public AfterCommitStorageDeletionService(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Coalesces object keys per transaction and performs irreversible storage deletion only after
     * the database commit. A rollback therefore leaves every object referenced by the database in
     * place. After-commit failures are logged and deliberately cannot roll back the committed DB.
     */
    public void deleteAfterCommit(String objectKey) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteSafely(objectKey);
            return;
        }

        @SuppressWarnings("unchecked")
        Set<String> keys = (Set<String>) TransactionSynchronizationManager.getResource(RESOURCE_KEY);
        if (keys != null) {
            keys.add(objectKey);
            return;
        }

        Set<String> transactionKeys = new LinkedHashSet<>();
        transactionKeys.add(objectKey);
        TransactionSynchronizationManager.bindResource(RESOURCE_KEY, transactionKeys);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                transactionKeys.forEach(AfterCommitStorageDeletionService.this::deleteSafely);
            }

            @Override
            public void afterCompletion(int status) {
                if (TransactionSynchronizationManager.hasResource(RESOURCE_KEY)) {
                    TransactionSynchronizationManager.unbindResource(RESOURCE_KEY);
                }
            }
        });
    }

    private void deleteSafely(String objectKey) {
        try {
            storage.deleteObject(objectKey);
        } catch (RuntimeException ex) {
            log.error("Storage object cleanup failed after database lifecycle update; objectKey={}", objectKey, ex);
        }
    }
}
