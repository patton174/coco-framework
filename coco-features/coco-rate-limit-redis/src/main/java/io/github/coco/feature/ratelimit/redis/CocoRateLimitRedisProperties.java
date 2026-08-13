package io.github.coco.feature.ratelimit.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco Redis 限流存储配置。
 * <p>
 * Redis key 仅保存限流逻辑键摘要和固定窗口结束时间，不保存路由或主体原文。
 * </p>
 */
@ConfigurationProperties("coco.rate-limit.redis")
public class CocoRateLimitRedisProperties {

    /** 默认 Redis key 前缀。 */
    public static final String DEFAULT_KEY_PREFIX = "coco:rate-limit";

    /** Redis key 前缀。 */
    private String keyPrefix = DEFAULT_KEY_PREFIX;

    /**
     * 返回 Redis key 前缀。
     * @return Redis key 前缀
     */
    public String getKeyPrefix() {
        return this.keyPrefix;
    }

    /**
     * 设置 Redis key 前缀。
     * @param keyPrefix Redis key 前缀
     */
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }
}
