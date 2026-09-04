package io.github.coco.cache;

/**
 * 缓存拓扑。
 */
public enum CocoCacheStoreType {

    /**
     * 仅本地。
     * <p>
     * 单层 Caffeine 缓存,进程内有效。最快、最省心,但多实例部署下各实例缓存相互独立,
     * 写入/失效不跨实例传播。适合单实例或可容忍各实例独立缓存的场景。
     * </p>
     */
    LOCAL,

    /**
     * 两层。
     * <p>
     * L1 为进程内 Caffeine,L2 为共享 Redis。读走 L1→L2,命中 L2 时回填 L1;写入与失效同时
     * 作用于两层,并通过 Redis pub/sub 广播,让其它实例失效各自的 L1,从而保证跨实例一致。
     * 需要 classpath 上有 Spring Data Redis。
     * </p>
     */
    TWO_LEVEL
}
