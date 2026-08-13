package io.github.coco.feature.idempotency;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

/**
 * Coco 请求幂等配置。
 *
 * @author patton174
 * @since 1.0.0
 */
@ConfigurationProperties(CocoIdempotencyFeature.PROPERTY_PREFIX)
public class CocoIdempotencyProperties {

    private static final Pattern HEADER_TOKEN = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

    private boolean enabled;

    private String headerName = "Idempotency-Key";

    private int maxKeyLength = 255;

    private long ttlSeconds = 86_400;

    private int maxEntries = 10_000;

    private int maxRequestBodyBytes = 1_048_576;

    private int maxResponseBodyBytes = 1_048_576;

    private int maxResponseHeaderCount = 64;

    private int maxResponseHeaderValueBytes = 8_192;

    private int maxResponseHeaderBytes = 65_536;

    private long cleanupIntervalSeconds = 60;

    private int filterOrder = Ordered.LOWEST_PRECEDENCE - 100;

    private List<Route> routes = new ArrayList<>();

    /** @return 是否启用请求幂等 */
    public boolean isEnabled() {
        return this.enabled;
    }

    /** @param enabled 是否启用请求幂等 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return 幂等键请求头名称 */
    public String getHeaderName() {
        return this.headerName;
    }

    /** @param headerName 幂等键请求头名称 */
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    /** @return 幂等键最大长度 */
    public int getMaxKeyLength() {
        return this.maxKeyLength;
    }

    /** @param maxKeyLength 幂等键最大长度 */
    public void setMaxKeyLength(int maxKeyLength) {
        this.maxKeyLength = maxKeyLength;
    }

    /** @return 幂等记录 TTL 秒数 */
    public long getTtlSeconds() {
        return this.ttlSeconds;
    }

    /** @param ttlSeconds 幂等记录 TTL 秒数 */
    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    /** @return 进程内最大记录数 */
    public int getMaxEntries() {
        return this.maxEntries;
    }

    /** @param maxEntries 进程内最大记录数 */
    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    /** @return 最大请求体字节数 */
    public int getMaxRequestBodyBytes() {
        return this.maxRequestBodyBytes;
    }

    /** @param maxRequestBodyBytes 最大请求体字节数 */
    public void setMaxRequestBodyBytes(int maxRequestBodyBytes) {
        this.maxRequestBodyBytes = maxRequestBodyBytes;
    }

    /** @return 最大响应体字节数 */
    public int getMaxResponseBodyBytes() {
        return this.maxResponseBodyBytes;
    }

    /** @param maxResponseBodyBytes 最大响应体字节数 */
    public void setMaxResponseBodyBytes(int maxResponseBodyBytes) {
        this.maxResponseBodyBytes = maxResponseBodyBytes;
    }

    /** @return 可缓存响应头字段的最大数量 */
    public int getMaxResponseHeaderCount() {
        return this.maxResponseHeaderCount;
    }

    /** @param maxResponseHeaderCount 可缓存响应头字段的最大数量 */
    public void setMaxResponseHeaderCount(int maxResponseHeaderCount) {
        this.maxResponseHeaderCount = maxResponseHeaderCount;
    }

    /** @return 单个可缓存响应头值的最大字节数 */
    public int getMaxResponseHeaderValueBytes() {
        return this.maxResponseHeaderValueBytes;
    }

    /** @param maxResponseHeaderValueBytes 单个可缓存响应头值的最大字节数 */
    public void setMaxResponseHeaderValueBytes(int maxResponseHeaderValueBytes) {
        this.maxResponseHeaderValueBytes = maxResponseHeaderValueBytes;
    }

    /** @return 可缓存响应头的最大总字节数 */
    public int getMaxResponseHeaderBytes() {
        return this.maxResponseHeaderBytes;
    }

    /** @param maxResponseHeaderBytes 可缓存响应头的最大总字节数 */
    public void setMaxResponseHeaderBytes(int maxResponseHeaderBytes) {
        this.maxResponseHeaderBytes = maxResponseHeaderBytes;
    }

    /** @return 过期记录清理间隔秒数 */
    public long getCleanupIntervalSeconds() {
        return this.cleanupIntervalSeconds;
    }

    /** @param cleanupIntervalSeconds 过期记录清理间隔秒数 */
    public void setCleanupIntervalSeconds(long cleanupIntervalSeconds) {
        this.cleanupIntervalSeconds = cleanupIntervalSeconds;
    }

    /** @return Servlet 过滤器顺序 */
    public int getFilterOrder() {
        return this.filterOrder;
    }

    /** @param filterOrder Servlet 过滤器顺序 */
    public void setFilterOrder(int filterOrder) {
        this.filterOrder = filterOrder;
    }

    /** @return 显式幂等路由规则 */
    public List<Route> getRoutes() {
        return List.copyOf(this.routes);
    }

    /** @param routes 显式幂等路由规则 */
    public void setRoutes(List<Route> routes) {
        this.routes = routes == null ? new ArrayList<>() : new ArrayList<>(routes);
    }

    /**
     * 校验完整配置，错误配置直接阻止自动配置启动。
     */
    public void validate() {
        if (this.headerName == null || !HEADER_TOKEN.matcher(this.headerName).matches()) {
            throw new IllegalArgumentException("coco.idempotency.header-name must be a valid HTTP token");
        }
        requireRange(this.maxKeyLength, 1, 1_024, "max-key-length");
        requireRange(this.ttlSeconds, 1, 604_800, "ttl-seconds");
        requireRange(this.maxEntries, 1, 1_000_000, "max-entries");
        requireRange(this.maxRequestBodyBytes, 0, 16_777_216, "max-request-body-bytes");
        requireRange(this.maxResponseBodyBytes, 0, 16_777_216, "max-response-body-bytes");
        requireRange(this.maxResponseHeaderCount, 1, 256, "max-response-header-count");
        requireRange(this.maxResponseHeaderValueBytes, 1, 65_536, "max-response-header-value-bytes");
        requireRange(this.maxResponseHeaderBytes, 1, 1_048_576, "max-response-header-bytes");
        if (this.maxResponseHeaderValueBytes > this.maxResponseHeaderBytes) {
            throw new IllegalArgumentException("coco.idempotency.max-response-header-value-bytes must not exceed "
                    + "max-response-header-bytes");
        }
        requireRange(this.cleanupIntervalSeconds, 1, 86_400, "cleanup-interval-seconds");
        if (this.routes == null || this.routes.isEmpty()) {
            throw new IllegalArgumentException("coco.idempotency.routes must contain at least one route");
        }
        for (int index = 0; index < this.routes.size(); index++) {
            Route route = this.routes.get(index);
            if (route == null) {
                throw new IllegalArgumentException("coco.idempotency.routes[" + index + "] must not be null");
            }
            route.validate(index);
        }
    }

    private static void requireRange(long value, long minimum, long maximum, String property) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("coco.idempotency." + property + " must be between "
                    + minimum + " and " + maximum);
        }
    }

    /**
     * 显式幂等路由规则。
     */
    public static class Route {

        private Set<String> methods = new LinkedHashSet<>();

        private Set<String> pathPatterns = new LinkedHashSet<>();

        /** @return HTTP 方法集合 */
        public Set<String> getMethods() {
            return Set.copyOf(this.methods);
        }

        /** @param methods HTTP 方法集合 */
        public void setMethods(Set<String> methods) {
            this.methods = methods == null ? new LinkedHashSet<>() : new LinkedHashSet<>(methods);
        }

        /** @return 路径模式集合 */
        public Set<String> getPathPatterns() {
            return Set.copyOf(this.pathPatterns);
        }

        /** @param pathPatterns 路径模式集合 */
        public void setPathPatterns(Set<String> pathPatterns) {
            this.pathPatterns = pathPatterns == null ? new LinkedHashSet<>() : new LinkedHashSet<>(pathPatterns);
        }

        private void validate(int index) {
            if (this.methods.isEmpty()) {
                throw new IllegalArgumentException("coco.idempotency.routes[" + index + "].methods must not be empty");
            }
            Set<String> normalizedMethods = new LinkedHashSet<>();
            for (String method : this.methods) {
                if (method == null || !HEADER_TOKEN.matcher(method).matches()) {
                    throw new IllegalArgumentException("coco.idempotency.routes[" + index
                            + "].methods contains an invalid HTTP method");
                }
                normalizedMethods.add(method.toUpperCase(Locale.ROOT));
            }
            this.methods = normalizedMethods;
            if (this.pathPatterns.isEmpty()) {
                throw new IllegalArgumentException("coco.idempotency.routes[" + index
                        + "].path-patterns must not be empty");
            }
            for (String pattern : this.pathPatterns) {
                if (pattern == null || pattern.isBlank() || !pattern.startsWith("/")) {
                    throw new IllegalArgumentException("coco.idempotency.routes[" + index
                            + "].path-patterns must contain non-blank absolute patterns");
                }
            }
        }
    }
}
