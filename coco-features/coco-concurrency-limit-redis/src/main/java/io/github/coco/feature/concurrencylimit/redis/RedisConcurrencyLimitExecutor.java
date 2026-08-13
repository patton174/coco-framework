package io.github.coco.feature.concurrencylimit.redis;

import java.util.List;

/** Redis 并发许可 Lua 命令端口，仅供适配器内部实现和测试使用。 */
interface RedisConcurrencyLimitExecutor {

    /** 执行一个已定义的 Lua 协议操作。 */
    String execute(RedisConcurrencyLimitOperation operation, List<String> keys, List<String> arguments);
}
