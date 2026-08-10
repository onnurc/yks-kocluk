package com.ykskocluk.demo.storage;

import com.ykskocluk.demo.config.R2Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "app.r2.enabled", havingValue = "false", matchIfMissing = true)
public class StubStorageService implements StorageService {
    private final R2Properties properties;
    private final Map<String, StoredObjectMetadata> preparedObjects = new ConcurrentHashMap<>();

    public StubStorageService(R2Properties properties) { this.properties = properties; }

    @Override
    public UploadTarget createPresignedUpload(String key, String contentType, long sizeBytes) {
        Instant expiresAt = Instant.now().plus(properties.uploadUrlExpirationMinutes(), ChronoUnit.MINUTES);
        preparedObjects.put(key, new StoredObjectMetadata(true, contentType, sizeBytes));
        return new UploadTarget(stubUrl("upload", key), expiresAt,
                Map.of("Content-Type", contentType, "Content-Length", Long.toString(sizeBytes)));
    }

    @Override public StoredObjectMetadata headObject(String key) {
        return preparedObjects.getOrDefault(key, new StoredObjectMetadata(false, null, 0));
    }

    @Override public DownloadTarget createPresignedDownload(String key) {
        return new DownloadTarget(stubUrl("download", key),
                Instant.now().plus(properties.downloadUrlExpirationMinutes(), ChronoUnit.MINUTES));
    }

    @Override public void deleteObject(String key) { preparedObjects.remove(key); }

    @Override public String resolvePublicUrl(String key) {
        return properties.publicBaseUrl() == null ? null : properties.publicBaseUrl() + "/" + key;
    }

    private String stubUrl(String operation, String key) {
        return "https://stub-storage.invalid/" + operation + "/" + URLEncoder.encode(key, StandardCharsets.UTF_8);
    }
}
