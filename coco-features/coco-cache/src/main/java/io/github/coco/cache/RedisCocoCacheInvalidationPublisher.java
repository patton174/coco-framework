package io.github.coco.cache;

import java.util.Objects;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 基于 Redis pub/sub 的 L1 失效广播发布器。
 * <p>
 * 消息以 {@code cacheName|sourceId|key} 的纯文本形式发到配置的频道,{@code key} 为空表示清空。
 * {@link #sourceId()} 在构造时随机生成,保证每个应用实例唯一,使订阅侧能忽略自己的广播。
 * </p>
 */
public final class RedisCocoCacheInvalidationPublisher implements CocoCacheInvalidationPublisher {

    private final StringRedisTemplate redisTemplate;
    private final String channel;
    private final String sourceId = UUID.randomUUID().toString();

    /**
     * 创建 Redis 失效发布器。
     * @param redisTemplate 字符串 RedisTemplate
     * @param channel pub/sub 频道
     */
    public RedisCocoCacheInvalidationPublisher(StringRedisTemplate redisTemplate, String channel) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.channel = Objects.requireNonNull(channel, "channel must not be null");
    }

    @Override
    public void publish(CocoCacheInvalidationMessage message) {
        this.redisTemplate.convertAndSend(this.channel, encode(message));
    }

    @Override
    public String sourceId() {
        return this.sourceId;
    }

    /**
     * 频道名。
     * @return 频道
     */
    public String channel() {
        return this.channel;
    }

    /**
     * 将消息编码为 {@code cacheName|sourceId|key} 纯文本。
     * @param message 消息
     * @return 编码字符串
     */
    static String encode(CocoCacheInvalidationMessage message) {
        return message.cacheName() + "|" + message.sourceId() + "|" + (message.key() == null ? "" : message.key());
    }

    /**
     * 解析 {@code cacheName|sourceId|key} 纯文本;{@code key} 为空还原为 {@code null}。
     * @param payload 编码字符串
     * @return 消息;格式非法返回 {@code null}
     */
    static CocoCacheInvalidationMessage decode(String payload) {
        if (payload == null) {
            return null;
        }
        // Split into exactly 3 fields; key may legitimately contain '|', so limit the split.
        String[] parts = payload.split("\\|", 3);
        if (parts.length < 3) {
            return null;
        }
        String key = parts[2].isEmpty() ? null : parts[2];
        return new CocoCacheInvalidationMessage(parts[0], key, parts[1]);
    }
}
