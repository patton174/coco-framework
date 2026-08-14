package io.github.coco.feature.ratelimit;

import java.util.ArrayList;
import java.util.List;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco Servlet 限流配置。
 * <p>
 * 默认关闭；启用后仍只有显式声明的路由会被拦截。生产多实例部署应提供共享的
 * {@link CocoRateLimitStore} Bean，进程内实现仅适用于单实例或开发环境。
 * </p>
 */
@ConfigurationProperties("coco.rate-limit")
public class CocoRateLimitProperties {

    private boolean enabled;

    private final List<CocoRateLimitRoute> routes = new ArrayList<>();

    private final InMemory inMemory = new InMemory();

    private final Filter filter = new Filter();

    private final TrustedProxy trustedProxy = new TrustedProxy();

    /**
     * 是否启用限流。
     * @return 启用时为 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * 设置是否启用限流。
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回限流路由。
     * @return 显式限流路由
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The established JavaBean API intentionally exposes a live routes list; runtime matchers take deep snapshots.")
    public List<CocoRateLimitRoute> getRoutes() {
        return this.routes;
    }

    /**
     * <p>
     * 设置限流路由。
     * </p>
     * @param routes 显式限流路由
     */
    public void setRoutes(List<CocoRateLimitRoute> routes) {
        List<CocoRateLimitRoute> copy = copyRoutes(routes);
        this.routes.clear();
        this.routes.addAll(copy);
    }

    /**
     * 返回进程内参考存储配置。
     * @return 进程内存储配置
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The established JavaBean API intentionally exposes a live nested property; stores take a snapshot at construction.")
    public InMemory getInMemory() {
        return this.inMemory;
    }

    /**
     * <p>
     * 设置进程内参考存储配置。
     * </p>
     * @param inMemory 进程内存储配置
     */
    public void setInMemory(InMemory inMemory) {
        InMemory copy = InMemory.copyOf(inMemory);
        this.inMemory.setMaxEntries(copy.getMaxEntries());
        this.inMemory.setCleanupIntervalSeconds(copy.getCleanupIntervalSeconds());
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The established JavaBean API intentionally exposes a live nested property; filters take a snapshot at construction.")
    public Filter getFilter() {
        return this.filter;
    }

    public void setFilter(Filter filter) {
        this.filter.setExcludedPathPatterns(Filter.copyOf(filter).getExcludedPathPatterns());
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The established JavaBean API intentionally exposes a live nested property; key resolvers take a snapshot at construction.")
    public TrustedProxy getTrustedProxy() {
        return this.trustedProxy;
    }

    public void setTrustedProxy(TrustedProxy trustedProxy) {
        this.trustedProxy.setRemoteAddresses(TrustedProxy.copyOf(trustedProxy).getRemoteAddresses());
    }

    private static List<CocoRateLimitRoute> copyRoutes(List<CocoRateLimitRoute> routes) {
        List<CocoRateLimitRoute> copy = new ArrayList<>();
        if (routes != null) {
            routes.forEach(route -> copy.add(CocoRateLimitRoute.copyOf(route)));
        }
        return copy;
    }

    /**
     * 进程内参考存储配置。
     */
    public static class InMemory {

        private int maxEntries = 10_000;

        private int cleanupIntervalSeconds = 60;

        /**
         * 返回最大活动限流键数。
         * @return 最大键数
         */
        public int getMaxEntries() {
            return this.maxEntries;
        }

        /**
         * 设置最大活动限流键数。
         * @param maxEntries 最大键数
         */
        public void setMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
        }

        /**
         * 返回过期键清理间隔秒数。
         * @return 清理间隔秒数
         */
        public int getCleanupIntervalSeconds() {
            return this.cleanupIntervalSeconds;
        }

        /**
         * 设置过期键清理间隔秒数。
         * @param cleanupIntervalSeconds 清理间隔秒数
         */
        public void setCleanupIntervalSeconds(int cleanupIntervalSeconds) {
            this.cleanupIntervalSeconds = cleanupIntervalSeconds;
        }

        static InMemory copyOf(InMemory source) {
            InMemory copy = new InMemory();
            if (source == null) {
                return copy;
            }
            copy.setMaxEntries(source.getMaxEntries());
            copy.setCleanupIntervalSeconds(source.getCleanupIntervalSeconds());
            return copy;
        }
    }

    /**
     * Filter 跳过路径配置。
     * <p>
     * 默认跳过常用管理与健康端点，避免监控请求占用业务配额。需要覆盖该默认值时可显式设置
     * {@code coco.rate-limit.filter.excluded-path-patterns}。
     * </p>
     */
    public static class Filter {

        private final List<String> excludedPathPatterns = new ArrayList<>(List.of(
                "/actuator", "/actuator/**", "/health", "/health/**"));

        @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Spring configuration binding requires a live list; filters snapshot it at construction.")
        public List<String> getExcludedPathPatterns() {
            return this.excludedPathPatterns;
        }

        public void setExcludedPathPatterns(List<String> excludedPathPatterns) {
            this.excludedPathPatterns.clear();
            if (excludedPathPatterns != null) {
                excludedPathPatterns.stream().filter(value -> value != null && !value.isBlank())
                        .map(String::trim).forEach(this.excludedPathPatterns::add);
            }
        }

        static Filter copyOf(Filter source) {
            Filter copy = new Filter();
            if (source != null) {
                copy.setExcludedPathPatterns(source.getExcludedPathPatterns());
            }
            return copy;
        }
    }

    /**
     * 可信反向代理边界配置。
     * <p>
     * 空列表是安全默认值，不读取任何转发头。仅当 Servlet remote address 精确匹配该列表时，默认键解析器才会
     * 从 {@code X-Forwarded-For} 链中按右向左信任边界解析第一个非代理地址。
     * </p>
     */
    public static class TrustedProxy {

        private final List<String> remoteAddresses = new ArrayList<>();

        @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Spring configuration binding requires a live list; key resolvers snapshot it at construction.")
        public List<String> getRemoteAddresses() {
            return this.remoteAddresses;
        }

        public void setRemoteAddresses(List<String> remoteAddresses) {
            this.remoteAddresses.clear();
            if (remoteAddresses != null) {
                remoteAddresses.stream().filter(value -> value != null && !value.isBlank())
                        .map(String::trim).forEach(this.remoteAddresses::add);
            }
        }

        static TrustedProxy copyOf(TrustedProxy source) {
            TrustedProxy copy = new TrustedProxy();
            copy.setRemoteAddresses(source == null ? null : source.getRemoteAddresses());
            return copy;
        }
    }
}
