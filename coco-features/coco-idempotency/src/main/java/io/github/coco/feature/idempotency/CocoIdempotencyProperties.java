package io.github.coco.feature.idempotency;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Coco 请求幂等配置。 */
@ConfigurationProperties("coco.idempotency")
public class CocoIdempotencyProperties {
    private boolean enabled;
    private String headerName = "Idempotency-Key";
    private Duration ttl = Duration.ofHours(24);
    private int maxKeyLength = 128;
    private int maxEntries = 100_000;
    private Duration cleanupInterval = Duration.ofMinutes(1);
    private CocoIdempotencyStoreType storeType = CocoIdempotencyStoreType.IN_MEMORY;
    private final Redis redis = new Redis();
    private final List<String> allowedMethods = new ArrayList<>(List.of("POST", "PUT", "PATCH", "DELETE"));
    public boolean isEnabled() { return this.enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getHeaderName() { return this.headerName; }
    public void setHeaderName(String headerName) { this.headerName = headerName; }
    public Duration getTtl() { return this.ttl; }
    public void setTtl(Duration ttl) { this.ttl = ttl; }
    public int getMaxKeyLength() { return this.maxKeyLength; }
    public void setMaxKeyLength(int maxKeyLength) { this.maxKeyLength = maxKeyLength; }
    public int getMaxEntries() { return this.maxEntries; }
    public void setMaxEntries(int maxEntries) { this.maxEntries = maxEntries; }
    public Duration getCleanupInterval() { return this.cleanupInterval; }
    public void setCleanupInterval(Duration cleanupInterval) { this.cleanupInterval = cleanupInterval; }
    public CocoIdempotencyStoreType getStoreType() { return this.storeType; }
    public void setStoreType(CocoIdempotencyStoreType storeType) {
        this.storeType = storeType == null ? CocoIdempotencyStoreType.IN_MEMORY : storeType;
    }
    public Redis getRedis() { return this.redis; }
    public void setRedis(Redis redis) { this.redis.setKeyPrefix(Redis.copyOf(redis).getKeyPrefix()); }
    public List<String> getAllowedMethods() { return this.allowedMethods; }
    public void setAllowedMethods(List<String> allowedMethods) { this.allowedMethods.clear(); if (allowedMethods != null) { this.allowedMethods.addAll(allowedMethods); } }

    /** Redis shared store configuration. */
    public static class Redis {
        private String keyPrefix = "coco:idempotency:";
        public String getKeyPrefix() { return this.keyPrefix; }
        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? "coco:idempotency:" : keyPrefix.trim();
        }
        static Redis copyOf(Redis source) {
            Redis copy = new Redis();
            if (source != null) { copy.setKeyPrefix(source.getKeyPrefix()); }
            return copy;
        }
    }
}
