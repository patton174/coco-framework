package io.github.coco.storage.s3;

import java.net.URI;
import java.time.Duration;

import io.github.coco.feature.storage.CocoObjectKey;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** S3 object storage adapter configuration. */
@ConfigurationProperties("coco.storage.s3")
public class CocoStorageS3Properties {

    private static final long MAX_OBJECT_SIZE_LIMIT = 5L * 1024 * 1024 * 1024;

    private static final Duration MAX_TIMEOUT = Duration.ofMinutes(10);

    private boolean enabled;

    private String bucket;

    private String region;

    private URI endpoint;

    private Boolean pathStyle;

    private String keyPrefix;

    private Duration connectTimeout = Duration.ofSeconds(10);

    private Duration readTimeout = Duration.ofSeconds(30);

    private Duration apiCallTimeout = Duration.ofMinutes(2);

    private Duration apiCallAttemptTimeout = Duration.ofSeconds(45);

    private long maxObjectSize = 1024L * 1024 * 1024;

    private boolean overwrite;

    private int listMaxSize = 1000;

    private String accessKey;

    private String secretKey;

    private String sessionToken;

    public void validate() {
        requireText(this.bucket, "bucket");
        requireText(this.region, "region");
        if (this.endpoint != null && (!"http".equalsIgnoreCase(this.endpoint.getScheme())
                && !"https".equalsIgnoreCase(this.endpoint.getScheme())
                || this.endpoint.getHost() == null || this.endpoint.getUserInfo() != null
                || this.endpoint.getQuery() != null || this.endpoint.getFragment() != null)) {
            throw new IllegalArgumentException("endpoint must be a plain http or https URI");
        }
        if (this.keyPrefix != null && !this.keyPrefix.isBlank()) {
            CocoObjectKey.validate(normalizedKeyPrefix());
        }
        validateTimeout(this.connectTimeout, "connect-timeout");
        validateTimeout(this.readTimeout, "read-timeout");
        validateTimeout(this.apiCallTimeout, "api-call-timeout");
        validateTimeout(this.apiCallAttemptTimeout, "api-call-attempt-timeout");
        if (this.maxObjectSize < 1 || this.maxObjectSize > MAX_OBJECT_SIZE_LIMIT) {
            throw new IllegalArgumentException("max-object-size must be between 1 and " + MAX_OBJECT_SIZE_LIMIT);
        }
        if (this.listMaxSize < 1 || this.listMaxSize > 1000) {
            throw new IllegalArgumentException("list-max-size must be between 1 and 1000");
        }
        boolean accessConfigured = hasText(this.accessKey);
        boolean secretConfigured = hasText(this.secretKey);
        if (accessConfigured != secretConfigured) {
            throw new IllegalArgumentException("access-key and secret-key must be configured together");
        }
        if (hasText(this.sessionToken) && !accessConfigured) {
            throw new IllegalArgumentException("session-token requires access-key and secret-key");
        }
    }

    String normalizedKeyPrefix() {
        String value = this.keyPrefix == null ? "" : this.keyPrefix.trim();
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    boolean resolvedPathStyle() {
        return this.pathStyle == null ? this.endpoint != null : this.pathStyle;
    }

    boolean hasStaticCredentials() {
        return hasText(this.accessKey);
    }

    private static void requireText(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void validateTimeout(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative() || value.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException(name + " must be positive and no greater than " + MAX_TIMEOUT);
        }
    }

    public boolean isEnabled() { return this.enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBucket() { return this.bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getRegion() { return this.region; }
    public void setRegion(String region) { this.region = region; }
    public URI getEndpoint() { return this.endpoint; }
    public void setEndpoint(URI endpoint) { this.endpoint = endpoint; }
    public Boolean getPathStyle() { return this.pathStyle; }
    public void setPathStyle(Boolean pathStyle) { this.pathStyle = pathStyle; }
    public String getKeyPrefix() { return this.keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    public Duration getConnectTimeout() { return this.connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return this.readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public Duration getApiCallTimeout() { return this.apiCallTimeout; }
    public void setApiCallTimeout(Duration apiCallTimeout) { this.apiCallTimeout = apiCallTimeout; }
    public Duration getApiCallAttemptTimeout() { return this.apiCallAttemptTimeout; }
    public void setApiCallAttemptTimeout(Duration apiCallAttemptTimeout) { this.apiCallAttemptTimeout = apiCallAttemptTimeout; }
    public long getMaxObjectSize() { return this.maxObjectSize; }
    public void setMaxObjectSize(long maxObjectSize) { this.maxObjectSize = maxObjectSize; }
    public boolean isOverwrite() { return this.overwrite; }
    public void setOverwrite(boolean overwrite) { this.overwrite = overwrite; }
    public int getListMaxSize() { return this.listMaxSize; }
    public void setListMaxSize(int listMaxSize) { this.listMaxSize = listMaxSize; }
    public String getAccessKey() { return this.accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return this.secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getSessionToken() { return this.sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }

    @Override
    public String toString() {
        return "CocoStorageS3Properties[enabled=" + this.enabled + ", bucket=" + this.bucket
                + ", region=" + this.region + ", endpointConfigured=" + (this.endpoint != null)
                + ", staticCredentialsConfigured=" + hasStaticCredentials() + "]";
    }
}
