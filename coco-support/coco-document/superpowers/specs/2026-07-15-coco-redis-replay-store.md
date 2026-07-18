# Coco Redis Replay Store Specification

## Scope

`coco-replay-redis` is an optional, independent adapter for the existing `CocoReplayStore` SPI. It participates in the root reactor through the `coco-features` aggregate, and its module coordinate is managed by the `coco-dependencies` BOM. It does not add a Coco feature or change the default in-memory store, and it remains absent from `coco-spring-boot-starter`, `StandardCocoFeatures`, and the generated standard feature plan.

Applications opt in by adding both `coco-replay-redis` and Spring Data Redis, then setting:

```yaml
coco:
  web:
    replay:
      redis:
        enabled: true
```

The auto-configuration runs before the Web default store. It registers only when Spring Data Redis and a `RedisConnectionFactory` are available, replay Redis is explicitly enabled, and the application has not supplied a `CocoReplayStore` bean.

## Reservation Protocol

1. Open one Redis connection.
2. Execute one structured single-key Lua `EVAL` invocation. The script first enables effects replication, reads Redis `TIME`, rejects an already-expired deadline, and executes `SET` with `PX` TTL and `NX`.
3. The script declares exactly one key, so in Redis Cluster its time decision and key write execute on the same key-owning node.
4. Close the connection.

The script result is the only reservation result: `1` reserves the key and `0` means an expired deadline or an unexpired reservation already exists. Redis itself owns expiry, so no client cleanup task, client clock, multi-key operation, or command-string construction is used. Decimal-string TTL subtraction avoids Lua floating-point precision loss at the Redis `PX` boundary. Single-key operations are valid for Redis Cluster routing.

Every infrastructure failure, unavailable connection, missing server time, null command result, close failure, or use after store close propagates as an exception. The adapter never converts those conditions into a duplicate result.

## Key And Value Material

Redis keys are exactly `coco:replay:` followed by the lowercase SHA-256 hexadecimal digest of `CocoReplayKey.value()` encoded in UTF-8. The raw appId, keyId, timestamp, nonce, HTTP method, and path are never placed in a Redis key or value. The reserved value is the fixed single byte `0x01`; no application serializer is involved.

## Verification

Protocol tests use a strict fake `RedisConnectionFactory`. It permits only a one-key `EVAL` carrying the fixed script and `close`, rejects all other connection operations, and records the exact byte-level command sequence. Tests cover atomic concurrent contention, Redis Cluster single-key routing, key-node clock skew, expiry, 1ms and maximum TTL boundaries, key material, binary value, script timeout, connection interruption, close failures, closed-store behavior, custom-store backoff, explicit enablement, and absent Spring Data Redis.
