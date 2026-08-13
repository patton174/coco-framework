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
        }
    }
}
