package io.github.coco.feature.httpclient;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

/**
 * Coco HTTP 客户端配置。
 */
@Validated
@ConfigurationProperties("coco.http")
public class CocoHttpClientProperties {

    private static final Pattern HEADER_NAME = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");
    private static final Set<String> FORBIDDEN_HEADERS = Set.of("authorization", "proxy-authorization", "cookie", "set-cookie");

    /** HTTP 客户端连接和读取超时的统一最大值。 */
    private static final Duration MAX_TIMEOUT = Duration.ofMinutes(5);

    private boolean enabled = true;
    private Map<String, Client> clients = new LinkedHashMap<>();

    public boolean isEnabled() { return this.enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    @SuppressFBWarnings(value = "EI_EXPOSE_REP",
            justification = "ConfigurationProperties map access must retain live mutable semantics for Spring Binder.")
    public Map<String, Client> getClients() { return this.clients; }
    public void setClients(Map<String, Client> clients) { this.clients = clients == null ? new LinkedHashMap<>() : new LinkedHashMap<>(clients); }

    void validate() {
        this.clients.forEach((name, client) -> {
            if (name == null || name.isBlank()) throw new IllegalStateException("coco.http.clients name must not be blank");
            if (client == null) throw new IllegalStateException("coco.http.clients." + name + " must not be null");
            client.validate("coco.http.clients." + name);
        });
    }

    /** 命名客户端配置。 */
    public static class Client {
        @NotBlank private String baseUrl;
        @NotNull @Positive private Duration connectTimeout = Duration.ofSeconds(2);
        @NotNull @Positive private Duration readTimeout = Duration.ofSeconds(10);
        private Map<String, String> defaultHeaders = new LinkedHashMap<>();
        @NestedConfigurationProperty
        private Signing signing = new Signing();

        public String getBaseUrl() { return this.baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public Duration getConnectTimeout() { return this.connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getReadTimeout() { return this.readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
        @SuppressFBWarnings(value = "EI_EXPOSE_REP",
                justification = "ConfigurationProperties map access must retain live mutable semantics for Spring Binder.")
        public Map<String, String> getDefaultHeaders() { return this.defaultHeaders; }
        public void setDefaultHeaders(Map<String, String> headers) { this.defaultHeaders = headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers); }
        @SuppressFBWarnings(value = "EI_EXPOSE_REP",
                justification = "ConfigurationProperties nested JavaBean accessors must retain live mutable semantics "
                        + "for Spring Binder and existing Java consumers.")
        public Signing getSigning() { return this.signing; }
        public void setSigning(Signing signing) { this.signing = signing == null ? new Signing() : signing; }

        private void validate(String prefix) {
            try {
                URI uri = URI.create(this.baseUrl);
                if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null
                        || uri.getRawQuery() != null || uri.getRawFragment() != null
                        || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                    throw new IllegalArgumentException();
                }
            } catch (RuntimeException ex) {
                throw new IllegalStateException(prefix
                        + ".base-url must be an absolute HTTP URI without user-info, query, or fragment", ex);
            }
            if (this.connectTimeout == null || this.connectTimeout.isZero() || this.connectTimeout.isNegative()) throw new IllegalStateException(prefix + ".connect-timeout must be positive");
            if (this.readTimeout == null || this.readTimeout.isZero() || this.readTimeout.isNegative()) throw new IllegalStateException(prefix + ".read-timeout must be positive");
            if (this.connectTimeout.compareTo(MAX_TIMEOUT) > 0) throw new IllegalStateException(prefix + ".connect-timeout must not exceed 5 minutes");
            if (this.readTimeout.compareTo(MAX_TIMEOUT) > 0) throw new IllegalStateException(prefix + ".read-timeout must not exceed 5 minutes");
            this.defaultHeaders.forEach((name, value) -> {
                if (name == null || !HEADER_NAME.matcher(name).matches()) throw new IllegalStateException(prefix + ".default-headers contains an invalid header name");
                if (FORBIDDEN_HEADERS.contains(name.toLowerCase(Locale.ROOT))) throw new IllegalStateException(prefix + ".default-headers must not configure " + name);
                if (value == null || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) throw new IllegalStateException(prefix + ".default-headers contains an invalid header value");
            });
            this.signing.validate(prefix + ".signing");
        }
    }

    /** 命名客户端出站 Coco 请求签名配置。 */
    public static class Signing {
        private static final int MIN_SECRET_LENGTH = 16;
        private static final int MAX_SECRET_LENGTH = 4096;
        private boolean enabled;
        private String appId;
        private String keyId;
        private String secret;
        private String algorithm = "HMAC-SHA256";
        private String appIdHeaderName = "X-Coco-App-Id";
        private String keyIdHeaderName = "X-Coco-Key-Id";
        private String timestampHeaderName = "X-Coco-Timestamp";
        private String nonceHeaderName = "X-Coco-Nonce";
        private String signatureHeaderName = "X-Coco-Sign";
        private String algorithmHeaderName = "X-Coco-Sign-Algorithm";
        private Set<String> canonicalHeaderNames = Set.of("content-md5", "content-type", "x-coco-app-id",
                "x-coco-timestamp", "x-coco-nonce", "x-coco-key-id", "x-coco-sign-algorithm");

        public boolean isEnabled() { return this.enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAppId() { return this.appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getKeyId() { return this.keyId; }
        public void setKeyId(String keyId) { this.keyId = keyId; }
        public String getSecret() { return this.secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public String getAlgorithm() { return this.algorithm; }
        public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
        public String getAppIdHeaderName() { return this.appIdHeaderName; }
        public void setAppIdHeaderName(String value) { this.appIdHeaderName = value; }
        public String getKeyIdHeaderName() { return this.keyIdHeaderName; }
        public void setKeyIdHeaderName(String value) { this.keyIdHeaderName = value; }
        public String getTimestampHeaderName() { return this.timestampHeaderName; }
        public void setTimestampHeaderName(String value) { this.timestampHeaderName = value; }
        public String getNonceHeaderName() { return this.nonceHeaderName; }
        public void setNonceHeaderName(String value) { this.nonceHeaderName = value; }
        public String getSignatureHeaderName() { return this.signatureHeaderName; }
        public void setSignatureHeaderName(String value) { this.signatureHeaderName = value; }
        public String getAlgorithmHeaderName() { return this.algorithmHeaderName; }
        public void setAlgorithmHeaderName(String value) { this.algorithmHeaderName = value; }
        public Set<String> getCanonicalHeaderNames() { return this.canonicalHeaderNames; }
        public void setCanonicalHeaderNames(Set<String> values) {
            this.canonicalHeaderNames = values == null ? Set.of() : values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> value.trim().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        CocoHttpClientSigningCredential credential() {
            return new CocoHttpClientSigningCredential(this.appId, this.keyId, this.secret, this.algorithm);
        }

        private void validate(String prefix) {
            validateHeader(prefix, "app-id-header-name", this.appIdHeaderName);
            validateHeader(prefix, "key-id-header-name", this.keyIdHeaderName);
            validateHeader(prefix, "timestamp-header-name", this.timestampHeaderName);
            validateHeader(prefix, "nonce-header-name", this.nonceHeaderName);
            validateHeader(prefix, "signature-header-name", this.signatureHeaderName);
            validateHeader(prefix, "algorithm-header-name", this.algorithmHeaderName);
            this.canonicalHeaderNames.forEach(value -> validateHeader(prefix, "canonical-header-names", value));
            if (!this.enabled) return;
            if (this.secret != null && (this.secret.length() < MIN_SECRET_LENGTH || this.secret.length() > MAX_SECRET_LENGTH)) {
                throw new IllegalStateException(prefix + ".secret length must be between 16 and 4096");
            }
            if (this.algorithm == null || !"HMAC-SHA256".equalsIgnoreCase(this.algorithm.trim())) {
                throw new IllegalStateException(prefix + ".algorithm must be HMAC-SHA256");
            }
        }

        private static void validateHeader(String prefix, String name, String value) {
            if (value == null || !HEADER_NAME.matcher(value).matches()) {
                throw new IllegalStateException(prefix + "." + name + " contains an invalid header name");
            }
        }
    }
}
