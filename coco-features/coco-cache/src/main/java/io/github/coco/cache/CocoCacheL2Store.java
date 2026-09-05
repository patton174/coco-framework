package io.github.coco.cache;

/**
 * L2(共享)缓存存储 SPI。
 * <p>
 * 只描述按缓存名 + 键的读写与失效。抽成接口是为了让两层缓存逻辑不依赖真实 Redis 即可单测;
 * 生产实现为 {@link RedisCocoCacheL2Store}。返回的 {@link Entry} 用 {@code present} 区分
 * "键不存在" 与 "键存在但值为 null"(穿透防护需要缓存 null)。
 * </p>
 */
public interface CocoCacheL2Store {

    /**
     * 读取一个键。
     * @param cacheName 缓存名
     * @param key 键的字符串形式
     * @return L2 命中结果;未命中返回 {@link Entry#miss()}
     */
    Entry get(String cacheName, String key);

    /**
     * 写入一个键。
     * @param cacheName 缓存名
     * @param key 键的字符串形式
     * @param value 值(可为 {@code null},表示缓存的空值)
     * @param ttlMillis 存活毫秒数
     */
    void put(String cacheName, String key, Object value, long ttlMillis);

    /**
     * 失效一个键。
     * @param cacheName 缓存名
     * @param key 键的字符串形式
     */
    void evict(String cacheName, String key);

    /**
     * 清空一个缓存的全部键。
     * @param cacheName 缓存名
     */
    void clear(String cacheName);

    /**
     * L2 读取结果。
     * @param present 键是否存在于 L2
     * @param value 存在时的值(可为 {@code null})
     */
    record Entry(boolean present, Object value) {

        private static final Entry MISS = new Entry(false, null);

        /**
         * 未命中。
         * @return 表示未命中的结果
         */
        public static Entry miss() {
            return MISS;
        }

        /**
         * 命中。
         * @param value 命中值(可为 {@code null})
         * @return 表示命中的结果
         */
        public static Entry hit(Object value) {
            return new Entry(true, value);
        }
    }
}
