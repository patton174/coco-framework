package io.github.coco.feature.replay.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 防重放存储配置。
 * <p>
 * Redis 适配器需要显式启用，避免在仅引入 Web 模块的应用中改变默认进程内存储行为。
 * </p>
 */
@ConfigurationProperties(prefix = "coco.web.replay.redis")
public class CocoReplayRedisProperties {

    private boolean enabled;

    /**
     * 返回是否启用 Redis 防重放存储。
     * @return 启用 Redis 防重放存储时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * 设置是否启用 Redis 防重放存储。
     * @param enabled 是否启用 Redis 防重放存储
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
