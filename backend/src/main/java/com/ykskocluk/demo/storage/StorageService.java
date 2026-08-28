package com.ykskocluk.demo.storage;

import java.time.Instant;
import java.util.Map;

public interface StorageService {
    UploadTarget createPresignedUpload(String objectKey, String contentType, long sizeBytes);
    StoredObjectMetadata headObject(String objectKey);
    byte[] readObjectPrefix(String objectKey, int maxBytes);
    DownloadTarget createPresignedDownload(String objectKey);
    void deleteObject(String objectKey);

    record UploadTarget(String url, Instant expiresAt, Map<String, String> requiredHeaders) {}
    record DownloadTarget(String url, Instant expiresAt) {}
    record StoredObjectMetadata(boolean exists, String contentType, long sizeBytes) {}
}
