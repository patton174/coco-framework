package io.github.coco.feature.lock;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisScriptingCommands;
import org.springframework.data.redis.connection.RedisSentinelConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisCocoLockManagerTest {

    @Test
    void usesNxPxAndCompareAndDeleteWithSeparateClosedConnections() {
        RecordingFactory factory = new RecordingFactory(true);
        RedisCocoLockManager manager = new RedisCocoLockManager(factory, "prefix:");
        CocoLock lock = manager.tryLock("key", Duration.ZERO, Duration.ofMillis(25)).orElseThrow();
        assertThat(factory.setCalls.get()).isEqualTo(1);
        assertThat(factory.lastOption).isEqualTo(SetOption.SET_IF_ABSENT);
        assertThat(factory.lastExpiration.getExpirationTimeInMilliseconds()).isEqualTo(25);
        lock.close();
        assertThat(factory.evalCalls.get()).isEqualTo(1);
        assertThat(factory.connectionsClosed.get()).isEqualTo(2);
        assertThat(factory.lastKey).isEqualTo("prefix:key");
        lock.close();
        assertThat(factory.evalCalls.get()).isEqualTo(1);
    }

    @Test
    void returnsEmptyForContentionButExposesRedisFailure() {
        RedisCocoLockManager unavailable = new RedisCocoLockManager(new RecordingFactory(false), "prefix:");
        Optional<CocoLock> result = unavailable.tryLock("key", Duration.ZERO, Duration.ofSeconds(1));
        assertThat(result).isEmpty();
        RedisConnectionFactory failingFactory = new RecordingFactory(true) {
            @Override public RedisConnection getConnection() { throw new DataAccessResourceFailureException("redis"); }
        };
        assertThatThrownBy(() -> new RedisCocoLockManager(failingFactory, "prefix:")
                .tryLock("key", Duration.ZERO, Duration.ofSeconds(1))).isInstanceOf(CocoLockException.class);
    }

    @Test
    void validatesRedisLeasePrecision() {
        assertThatIllegalArgumentException().isThrownBy(() -> RedisCocoLockManager.redisLeaseMillis(Duration.ofNanos(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> RedisCocoLockManager.redisLeaseMillis(Duration.ZERO));
    }

    static class RecordingFactory implements RedisConnectionFactory {
        final boolean setResult;
        final AtomicInteger setCalls = new AtomicInteger();
        final AtomicInteger evalCalls = new AtomicInteger();
        final AtomicInteger connectionsClosed = new AtomicInteger();
        Expiration lastExpiration;
        SetOption lastOption;
        String lastKey;

        RecordingFactory(boolean setResult) { this.setResult = setResult; }

        @Override
        public RedisConnection getConnection() {
            return (RedisConnection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {RedisConnection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("stringCommands")) {
                            return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                                    new Class<?>[] {RedisStringCommands.class}, (p, stringMethod, stringArgs) -> {
                                        if (stringMethod.getName().equals("set")) {
                                            setCalls.incrementAndGet();
                                            lastKey = new String((byte[]) stringArgs[0], java.nio.charset.StandardCharsets.UTF_8);
                                            lastExpiration = (Expiration) stringArgs[2];
                                            lastOption = (SetOption) stringArgs[3];
                                            return setResult;
                                        }
                                        return defaultValue(stringMethod.getReturnType());
                                    });
                        }
                        if (method.getName().equals("scriptingCommands")) {
                            return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                                    new Class<?>[] {RedisScriptingCommands.class}, (p, scriptMethod, scriptArgs) -> {
                                        if (scriptMethod.getName().equals("eval")) {
                                            evalCalls.incrementAndGet();
                                            assertThat(scriptArgs[1]).isEqualTo(ReturnType.INTEGER);
                                            assertThat((byte[]) scriptArgs[0]).asString(java.nio.charset.StandardCharsets.UTF_8)
                                                    .contains("redis.call('get', KEYS[1]) == ARGV[1]");
                                            return 1L;
                                        }
                                        return defaultValue(scriptMethod.getReturnType());
                                    });
                        }
                        if (method.getName().equals("close")) { connectionsClosed.incrementAndGet(); return null; }
                        return defaultValue(method.getReturnType());
                    });
        }

        @Override
        public RedisSentinelConnection getSentinelConnection() {
            return null;
        }

        @Override
        public RedisClusterConnection getClusterConnection() {
            return null;
        }

        @Override
        public boolean getConvertPipelineAndTxResults() {
            return false;
        }

        @Override
        public org.springframework.dao.DataAccessException translateExceptionIfPossible(RuntimeException ex) {
            return null;
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) { return null; }
            if (type == boolean.class) { return false; }
            if (type == byte.class) { return (byte) 0; }
            if (type == short.class) { return (short) 0; }
            if (type == int.class) { return 0; }
            if (type == long.class) { return 0L; }
            if (type == float.class) { return 0F; }
            if (type == double.class) { return 0D; }
            if (type == char.class) { return '\0'; }
            return null;
        }
    }
}
