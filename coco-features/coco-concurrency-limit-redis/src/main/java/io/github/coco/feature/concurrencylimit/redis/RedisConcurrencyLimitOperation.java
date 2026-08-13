package io.github.coco.feature.concurrencylimit.redis;

/** Redis Lua 协议操作。 */
enum RedisConcurrencyLimitOperation { ACQUIRE, RENEW, RELEASE }
