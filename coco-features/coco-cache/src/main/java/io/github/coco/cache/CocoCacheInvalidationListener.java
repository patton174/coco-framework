package io.github.coco.cache;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

/**
 * Redis pub/sub 失效频道的订阅侧。
 * <p>
 * 收到广播后解码并交给 {@link CocoCacheManager#onRemoteInvalidation}(其内部会忽略本实例
 * 自己发出的消息)。解析失败的消息被静默丢弃,不影响其它消息处理。
 * </p>
 */
public final class CocoCacheInvalidationListener implements MessageListener {

    private final CocoCacheManager cacheManager;

    /**
     * 创建失效订阅监听器。
     * @param cacheManager 缓存管理器
     */
    public CocoCacheInvalidationListener(CocoCacheManager cacheManager) {
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager must not be null");
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        CocoCacheInvalidationMessage decoded = RedisCocoCacheInvalidationPublisher.decode(
                new String(message.getBody(), StandardCharsets.UTF_8));
        if (decoded != null) {
            this.cacheManager.onRemoteInvalidation(decoded);
        }
    }
}
