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

    @Override public byte[] readObjectPrefix(String key, int maxBytes) {
        StoredObjectMetadata metadata = preparedObjects.get(key);
        if (metadata == null || maxBytes < 1) return new byte[0];
        byte[] signature = switch (metadata.contentType()) {
            case "image/jpeg" -> new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
            case "image/png" -> new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
            case "image/webp" -> new byte[] {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50};
            case "application/pdf" -> new byte[] {0x25, 0x50, 0x44, 0x46, 0x2d};
            default -> new byte[0];
        };
        return java.util.Arrays.copyOf(signature, Math.min(signature.length, maxBytes));
    }

    @Override public DownloadTarget createPresignedDownload(String key) {
        return new DownloadTarget(stubUrl("download", key),
                Instant.now().plus(properties.downloadUrlExpirationMinutes(), ChronoUnit.MINUTES));
    }

    @Override public void deleteObject(String key) { preparedObjects.remove(key); }

    private String stubUrl(String operation, String key) {
        return "https://stub-storage.invalid/" + operation + "/" + URLEncoder.encode(key, StandardCharsets.UTF_8);
    }
}
