package io.github.coco.feature.lock.redis;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.coco.feature.lock.CocoLock;
import io.github.coco.feature.lock.CocoLockException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisScriptingCommands;
import org.springframework.data.redis.connection.RedisSentinelConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.types.Expiration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisCocoLockManagerTest {
    @Test
    void usesNxPxHashesBusinessKeyAndCompareDeletesOnlyOwner() {
        FakeRedis redis = new FakeRedis();
        RedisCocoLockManager manager = new RedisCocoLockManager(redis, "coco:lock:app:");
        CocoLock old = manager.tryLock("secret business key", Duration.ZERO, Duration.ofMillis(10)).orElseThrow();
        assertThat(redis.setOption).isEqualTo(RedisStringCommands.SetOption.SET_IF_ABSENT);
        assertThat(redis.expiration.getExpirationTimeInMilliseconds()).isEqualTo(10);
        assertThat(redis.lastKey).doesNotContain("secret business key").startsWith("coco:lock:app:");
        redis.values.put(redis.lastKey, "new-owner".getBytes(StandardCharsets.UTF_8));
        old.close();
        assertThat(redis.values).containsKey(redis.lastKey);
        assertThat(redis.closedConnections.get()).isEqualTo(2);
        old.close();
        assertThat(redis.evalCalls.get()).isEqualTo(1);
    }

    @Test
    void contentionTimeoutInterruptAndClosedManagerFollowSpi() throws Exception {
        FakeRedis redis = new FakeRedis();
        RedisCocoLockManager manager = new RedisCocoLockManager(redis, "coco:lock:app:");
        CocoLock held = manager.tryLock("key", Duration.ZERO, Duration.ofSeconds(1)).orElseThrow();
        assertThat(manager.tryLock("key", Duration.ZERO, Duration.ofSeconds(1))).isEmpty();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread interrupted = new Thread(() -> { Thread.currentThread().interrupt(); try { manager.tryLock("key", Duration.ofSeconds(1), Duration.ofSeconds(1)); } catch (Throwable ex) { failure.set(ex); } });
        interrupted.start(); interrupted.join();
        assertThat(failure.get()).isInstanceOf(CocoLockException.class).hasMessageContaining("Interrupted");
        held.close(); manager.close();
        assertThatIllegalStateException().isThrownBy(() -> manager.tryLock("next", Duration.ZERO, Duration.ofSeconds(1)));
    }

    @Test
    void rejectsInvalidLeaseAndRedisFailuresFailClosed() {
        assertThatIllegalArgumentException().isThrownBy(() -> RedisCocoLockManager.leaseMillis(Duration.ofNanos(1)));
        RedisConnectionFactory failing = new FakeRedis() { @Override public RedisConnection getConnection() { throw new org.springframework.dao.DataAccessResourceFailureException("offline"); } };
        assertThatThrownBy(() -> new RedisCocoLockManager(failing, "coco:lock:app:").tryLock("key", Duration.ZERO, Duration.ofSeconds(1))).isInstanceOf(CocoLockException.class);
    }

    static class FakeRedis implements RedisConnectionFactory {
        final Map<String, byte[]> values = new ConcurrentHashMap<>(); final AtomicInteger closedConnections = new AtomicInteger(); final AtomicInteger evalCalls = new AtomicInteger(); volatile String lastKey; volatile Expiration expiration; volatile RedisStringCommands.SetOption setOption;
        @Override public RedisConnection getConnection() { return (RedisConnection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { RedisConnection.class }, (p, m, a) -> {
            if (m.getName().equals("stringCommands")) return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { RedisStringCommands.class }, (q, n, b) -> { if (n.getName().equals("set")) { lastKey = new String((byte[]) b[0], StandardCharsets.UTF_8); expiration = (Expiration) b[2]; setOption = (RedisStringCommands.SetOption) b[3]; return values.putIfAbsent(lastKey, Arrays.copyOf((byte[]) b[1], ((byte[]) b[1]).length)) == null; } return null; });
            if (m.getName().equals("scriptingCommands")) return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { RedisScriptingCommands.class }, (q, n, b) -> { if (n.getName().equals("eval")) { evalCalls.incrementAndGet(); byte[][] keysAndArgs = (byte[][]) b[3]; String key = new String(keysAndArgs[0], StandardCharsets.UTF_8); byte[] token = keysAndArgs[1]; byte[] current = values.get(key); if (Arrays.equals(current, token)) { values.remove(key); return 1L; } return 0L; } return null; });
            if (m.getName().equals("close")) { closedConnections.incrementAndGet(); return null; } return primitiveDefault(m.getReturnType()); }); }
        @Override public boolean getConvertPipelineAndTxResults() { return false; } @Override public RedisClusterConnection getClusterConnection() { return null; } @Override public RedisSentinelConnection getSentinelConnection() { return null; } @Override public DataAccessException translateExceptionIfPossible(RuntimeException e) { return null; }
        private static Object primitiveDefault(Class<?> type) { if (!type.isPrimitive()) return null; if (type == boolean.class) return false; if (type == long.class) return 0L; if (type == int.class) return 0; return 0; }
    }
}
