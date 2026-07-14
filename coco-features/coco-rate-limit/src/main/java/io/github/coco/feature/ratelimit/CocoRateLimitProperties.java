package io.github.coco.feature.ratelimit;

import java.util.ArrayList;
import java.util.List;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco Web 限流配置。
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
}
