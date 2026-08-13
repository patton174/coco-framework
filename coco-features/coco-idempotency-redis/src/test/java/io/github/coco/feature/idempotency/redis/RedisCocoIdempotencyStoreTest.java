package io.github.coco.feature.idempotency.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import io.github.coco.feature.idempotency.store.CocoIdempotencyAcquireStatus;
import io.github.coco.feature.idempotency.store.CocoIdempotencyLease;
import io.github.coco.feature.idempotency.store.CocoIdempotencyRequest;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStoredResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisScriptingCommands;
import org.springframework.data.redis.connection.ReturnType;

class RedisCocoIdempotencyStoreTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void acquiresInProgressDetectsMismatchAndReplaysStructuredResponse() {
        FakeRedisConnectionFactory factory = new FakeRedisConnectionFactory(NOW.toEpochMilli());
        RedisCocoIdempotencyStore store = store(factory);
        CocoIdempotencyRequest request = request("a", "a");

        var acquired = store.acquire(request, NOW, NOW.plusSeconds(60));
        assertThat(acquired.status()).isEqualTo(CocoIdempotencyAcquireStatus.ACQUIRED);
        assertThat(acquired.lease().orElseThrow().ownerToken()).matches("[0-9a-f]{64}");
        assertThat(store.acquire(request, NOW, NOW.plusSeconds(60)).status())
                .isEqualTo(CocoIdempotencyAcquireStatus.IN_PROGRESS);
        assertThat(store.acquire(request("a", "b"), NOW, NOW.plusSeconds(60)).status())
                .isEqualTo(CocoIdempotencyAcquireStatus.PAYLOAD_MISMATCH);

        CocoIdempotencyStoredResponse response = response();
        assertThat(store.complete(acquired.lease().orElseThrow(), response, NOW)).isTrue();
        var replay = store.acquire(request, NOW, NOW.plusSeconds(60));
        assertThat(replay.status()).isEqualTo(CocoIdempotencyAcquireStatus.REPLAY);
        assertThat(replay.response().orElseThrow().status()).isEqualTo(201);
        assertThat(replay.response().orElseThrow().headers()).containsEntry("Set-Cookie", List.of("a=1", "b=2"));
        assertThat(replay.response().orElseThrow().body()).containsExactly((byte) 0, (byte) 1, (byte) 127);
        assertThat(factory.closeCount).isEqualTo(5);
    }

    @Test
    void takesOverExpiredEntryUsingRedisServerTime() {
        FakeRedisConnectionFactory factory = new FakeRedisConnectionFactory(NOW.toEpochMilli());
        RedisCocoIdempotencyStore store = store(factory);
        CocoIdempotencyRequest request = request("expired", "payload");
        assertThat(store.acquire(request, NOW, NOW.plusSeconds(1)).status())
                .isEqualTo(CocoIdempotencyAcquireStatus.ACQUIRED);
        factory.now.addAndGet(1_000);

        assertThat(store.acquire(request, NOW, NOW.plusSeconds(61)).status())
                .isEqualTo(CocoIdempotencyAcquireStatus.ACQUIRED);
    }

    @Test
    void completeAndFailRequireOwnerAndDoNotExtendTtl() {
        FakeRedisConnectionFactory factory = new FakeRedisConnectionFactory(NOW.toEpochMilli());
        RedisCocoIdempotencyStore store = store(factory);
        CocoIdempotencyRequest request = request("owner", "payload");
        CocoIdempotencyLease lease = store.acquire(request, NOW, NOW.plusSeconds(60)).lease().orElseThrow();
        long initialTtl = factory.ttl(request.keyHash());
        CocoIdempotencyLease wrongOwner = new CocoIdempotencyLease(request, "other", lease.expiresAt());

        assertThat(store.complete(wrongOwner, response(), NOW)).isFalse();
        factory.now.addAndGet(1_000);
        assertThat(store.complete(lease, response(), NOW)).isTrue();
        assertThat(factory.ttl(request.keyHash())).isEqualTo(initialTtl - 1_000);
        assertThat(store.fail(lease, NOW)).isFalse();

        CocoIdempotencyLease active = store.acquire(request("fail", "payload"), NOW, NOW.plusSeconds(60))
                .lease().orElseThrow();
        assertThat(store.fail(new CocoIdempotencyLease(active.request(), "other", active.expiresAt()), NOW)).isFalse();
        assertThat(store.fail(active, NOW)).isTrue();
    }

    @Test
    void supportsEmptyBodyAndRejectsResponseAndHeaderLimits() {
        FakeRedisConnectionFactory factory = new FakeRedisConnectionFactory(NOW.toEpochMilli());
        RedisCocoIdempotencyStore store = store(factory);
        CocoIdempotencyLease lease = store.acquire(request("empty", "payload"), NOW, NOW.plusSeconds(60))
                .lease().orElseThrow();
        assertThat(store.complete(lease, new CocoIdempotencyStoredResponse(204, Map.of(), new byte[0]), NOW)).isTrue();
        assertThat(store.acquire(lease.request(), NOW, NOW.plusSeconds(60)).response().orElseThrow().body()).isEmpty();

        CocoIdempotencyRedisProperties responseLimit = properties();
        responseLimit.setMaxResponseBytes(2);
        RedisCocoIdempotencyStore smallResponse = new RedisCocoIdempotencyStore(factory, responseLimit);
        assertThatThrownBy(() -> smallResponse.complete(lease, response(), NOW)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-response-bytes");
        CocoIdempotencyRedisProperties headerLimit = properties();
        headerLimit.setMaxHeaderBytes(4);
        RedisCocoIdempotencyStore smallHeader = new RedisCocoIdempotencyStore(factory, headerLimit);
        assertThatThrownBy(() -> smallHeader.complete(lease, response(), NOW)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-header-bytes");
    }

    @Test
    void failsClosedForBadRedisResultsFailuresAndAfterClose() {
        FakeRedisConnectionFactory factory = new FakeRedisConnectionFactory(NOW.toEpochMilli());
        RedisCocoIdempotencyStore store = store(factory);
        factory.scriptResult = List.of("NOT_A_STATUS".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> store.acquire(request("bad", "payload"), NOW, NOW.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("acquire returned");
        factory.scriptResult = null;
        factory.failure = new DataAccessResourceFailureException("down");
        assertThatThrownBy(() -> store.acquire(request("down", "payload"), NOW, NOW.plusSeconds(60)))
                .isInstanceOf(DataAccessResourceFailureException.class);
        factory.failure = null;
        store.close();
        assertThatThrownBy(() -> store.acquire(request("closed", "payload"), NOW, NOW.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("closed");
    }

    @Test
    void usesExactlyOneClusterSafeNamespacedKey() {
        FakeRedisConnectionFactory factory = new FakeRedisConnectionFactory(NOW.toEpochMilli());
        RedisCocoIdempotencyStore store = store(factory);
        CocoIdempotencyRequest request = request("key", "payload");
        store.acquire(request, NOW, NOW.plusSeconds(60));
        assertThat(factory.lastKey).isEqualTo("coco:idempotency:" + request.keyHash());
        assertThat(factory.lastKey).matches("coco:idempotency:[0-9a-f]{64}");
        assertThat(factory.lastKey).doesNotContain(request.requestHash());
    }

    private static RedisCocoIdempotencyStore store(FakeRedisConnectionFactory factory) {
        return new RedisCocoIdempotencyStore(factory, properties());
    }

    private static CocoIdempotencyRedisProperties properties() {
        CocoIdempotencyRedisProperties properties = new CocoIdempotencyRedisProperties();
        properties.setKeyPrefix("coco:idempotency:");
        return properties;
    }

    private static CocoIdempotencyRequest request(String key, String payload) {
        return new CocoIdempotencyRequest(hash(key), hash(payload));
    }

    private static String hash(String value) {
        return String.format("%064x", new java.math.BigInteger(1, value.getBytes(StandardCharsets.UTF_8)));
    }

    private static CocoIdempotencyStoredResponse response() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Set-Cookie", List.of("a=1", "b=2"));
        headers.put("X-Trace", List.of("trace"));
        return new CocoIdempotencyStoredResponse(201, headers, new byte[] { 0, 1, 127 });
    }

    private static final class FakeRedisConnectionFactory implements RedisConnectionFactory {

        private final AtomicLong now;

        private final Map<String, Entry> entries = new LinkedHashMap<>();

        private int closeCount;

        private String lastKey;

        private Object scriptResult;

        private RuntimeException failure;

        private FakeRedisConnectionFactory(long now) {
            this.now = new AtomicLong(now);
        }

        @Override
        public RedisConnection getConnection() {
            return (RedisConnection) Proxy.newProxyInstance(RedisConnection.class.getClassLoader(),
                    new Class<?>[] { RedisConnection.class }, (proxy, method, arguments) -> switch (method.getName()) {
                        case "scriptingCommands" -> scriptingCommands();
                        case "close" -> { this.closeCount++; yield null; }
                        case "isClosed" -> false;
                        default -> throw new AssertionError("Unexpected Redis connection method: " + method);
                    });
        }

        @Override public boolean getConvertPipelineAndTxResults() { return false; }
        @Override public org.springframework.data.redis.connection.RedisClusterConnection getClusterConnection() { return null; }
        @Override public org.springframework.data.redis.connection.RedisSentinelConnection getSentinelConnection() { return null; }
        @Override public org.springframework.dao.DataAccessException translateExceptionIfPossible(RuntimeException exception) { return null; }

        private RedisScriptingCommands scriptingCommands() {
            return (RedisScriptingCommands) Proxy.newProxyInstance(RedisScriptingCommands.class.getClassLoader(),
                    new Class<?>[] { RedisScriptingCommands.class }, (proxy, method, arguments) -> {
                        if (!method.getName().equals("eval")) throw new AssertionError("Unexpected scripting method");
                        if (this.failure != null) throw this.failure;
                        byte[] script = (byte[]) arguments[0];
                        ReturnType type = (ReturnType) arguments[1];
                        int keys = (int) arguments[2];
                        byte[][] values = (byte[][]) arguments[3];
                        if (keys != 1) throw new AssertionError("Only one cluster key is allowed");
                        this.lastKey = new String(values[0], StandardCharsets.UTF_8);
                        if (this.scriptResult != null) return this.scriptResult;
                        String source = new String(script, StandardCharsets.UTF_8);
                        return source.contains("ACQUIRE_V1") ? acquire(values, type)
                                : source.contains("COMPLETE_V1") ? complete(values, type) : fail(values, type);
                    });
        }

        private List<byte[]> acquire(byte[][] values, ReturnType type) {
            if (type != ReturnType.MULTI) throw new AssertionError("Acquire must return MULTI");
            String key = this.lastKey;
            String requestHash = text(values[1]);
            String expiresAt = text(values[2]);
            String token = text(values[3]);
            Entry entry = this.entries.get(key);
            if (entry != null && entry.expiresAt > this.now.get()) {
                if (!entry.requestHash.equals(requestHash)) return reply("PAYLOAD_MISMATCH");
                return entry.response == null ? reply("IN_PROGRESS") : reply("REPLAY", entry.response);
            }
            long expiry = Long.parseLong(expiresAt);
            if (expiry <= this.now.get()) return reply("INVALID");
            this.entries.put(key, new Entry(requestHash, token, expiry, null));
            return reply("ACQUIRED", token.getBytes(StandardCharsets.UTF_8), expiresAt.getBytes(StandardCharsets.UTF_8));
        }

        private Long complete(byte[][] values, ReturnType type) { return transition(values, type, true); }
        private Long fail(byte[][] values, ReturnType type) { return transition(values, type, false); }

        private Long transition(byte[][] values, ReturnType type, boolean completing) {
            if (type != ReturnType.INTEGER) throw new AssertionError("Transition must return INTEGER");
            Entry entry = this.entries.get(this.lastKey);
            if (entry == null || entry.response != null || entry.expiresAt <= this.now.get()
                    || !entry.requestHash.equals(text(values[1])) || !entry.ownerToken.equals(text(values[2]))
                    || entry.expiresAt != Long.parseLong(text(values[3]))) return 0L;
            if (completing) this.entries.put(this.lastKey, new Entry(entry.requestHash, entry.ownerToken,
                    entry.expiresAt, Arrays.copyOf(values[4], values[4].length)));
            else this.entries.remove(this.lastKey);
            return 1L;
        }

        private long ttl(String keyHash) {
            Entry entry = this.entries.get("coco:idempotency:" + keyHash);
            return entry == null ? -1 : entry.expiresAt - this.now.get();
        }

        private static List<byte[]> reply(String status, byte[]... values) {
            List<byte[]> reply = new java.util.ArrayList<>();
            reply.add(status.getBytes(StandardCharsets.UTF_8));
            reply.addAll(Arrays.asList(values));
            return reply;
        }

        private static String text(byte[] value) { return new String(value, StandardCharsets.UTF_8); }
    }

    private record Entry(String requestHash, String ownerToken, long expiresAt, byte[] response) { }
}
