package io.github.coco.cache;

/**
 * L1 失效广播发布器 SPI。
 * <p>
 * 抽成接口是为了让两层缓存逻辑不依赖真实 Redis pub/sub 即可单测;生产实现基于
 * {@code RedisTemplate#convertAndSend}。{@link #sourceId()} 是本实例的唯一标识,
 * 用于订阅侧忽略自己发出的消息。
 * </p>
 */
public interface CocoCacheInvalidationPublisher {

    /**
     * 广播一条失效消息。
     * @param message 失效消息
     */
    void publish(CocoCacheInvalidationMessage message);

    /**
     * 返回本实例的唯一标识。
     * @return 源标识
     */
    String sourceId();
}
