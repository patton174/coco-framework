package io.github.coco.cache;

/**
 * L1 失效广播消息。
 * <p>
 * 某实例写入或驱逐后,通过 Redis pub/sub 广播此消息,其它实例据此失效各自的 L1(不动共享 L2)。
 * {@code key} 为 {@code null} 表示整表清空。{@code sourceId} 用于让发布者忽略自己的消息,
 * 避免刚写好的 L1 又被自己的广播清掉。
 * </p>
 * @param cacheName 缓存名
 * @param key 被失效的键的字符串形式;{@code null} 表示清空整个缓存
 * @param sourceId 发布实例的唯一标识
 */
public record CocoCacheInvalidationMessage(String cacheName, String key, String sourceId) {
}
