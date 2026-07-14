# Coco Redis Replay Store Specification

## Scope

`coco-replay-redis` is an optional, independent adapter for the existing `CocoReplayStore` SPI. It does not add a Coco feature, change the default in-memory store, or add Redis to the root BOM, starter, or feature metadata.

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
2. Read Redis server time with structured `TIME` API in milliseconds.
3. Subtract the server time from the absolute `expiresAt`. A non-positive TTL returns `false` without a write.
4. Execute structured single-key `SET` with `PX` TTL and `NX` option on that same connection.
5. Close the connection.

The `SET NX` result is the only reservation result: `true` reserves the key, `false` means an unexpired reservation already exists. Redis itself owns expiry, so no client cleanup task, clock, script, multi-key operation, or command-string construction is used. Single-key operations are valid for Redis Cluster routing.

Every infrastructure failure, unavailable connection, missing server time, null command result, close failure, or use after store close propagates as an exception. The adapter never converts those conditions into a duplicate result.

## Key And Value Material

Redis keys are exactly `coco:replay:` followed by the lowercase SHA-256 hexadecimal digest of `CocoReplayKey.value()` encoded in UTF-8. The raw appId, keyId, timestamp, nonce, HTTP method, and path are never placed in a Redis key or value. The reserved value is the fixed single byte `0x01`; no application serializer is involved.

## Verification

Protocol tests use a strict fake `RedisConnectionFactory`. It permits only `TIME(MILLISECONDS)`, `SET key value PX ttl NX`, and `close`, rejects all other connection operations, and records the exact byte-level command sequence. Tests cover atomic concurrent contention, Redis Cluster single-key routing, expiry, 1ms and maximum TTL boundaries, key material, binary value, TIME failure, SET timeout, connection interruption, close failures, closed-store behavior, custom-store backoff, explicit enablement, and absent Spring Data Redis.
