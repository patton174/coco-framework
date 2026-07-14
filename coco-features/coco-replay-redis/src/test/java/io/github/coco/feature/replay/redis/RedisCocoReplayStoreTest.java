package io.github.coco.feature.replay.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.github.coco.feature.web.replay.CocoReplayKey;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.SetCondition;
import org.springframework.data.redis.core.types.Expiration;

class RedisCocoReplayStoreTest {

    private static final long BASE_TIME_MILLIS = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();

    @Test
    void reservesOnceUsingServerTimeAndAtomicSetNxWithTtl() throws Exception {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        RedisCocoReplayStore store = new RedisCocoReplayStore(factory);
        CocoReplayKey key = key("sensitive-nonce");

        assertThat(store.reserve(key, Instant.ofEpochMilli(BASE_TIME_MILLIS + 60_000))).isTrue();
        assertThat(store.reserve(key, Instant.ofEpochMilli(BASE_TIME_MILLIS + 120_000))).isFalse();

        List<SetCommand> commands = factory.commands();
        assertThat(commands).hasSize(2);
        SetCommand firstCommand = commands.get(0);
        assertThat(firstCommand.key()).isEqualTo(expectedRedisKey(key));
        assertThat(firstCommand.value()).containsExactly((byte) 1);
        assertThat(firstCommand.ttlMillis()).isEqualTo(60_000);
        assertThat(firstCommand.condition().getKeyCondition())
                .isEqualTo(SetCondition.ifAbsent().getKeyCondition());
        assertThat(new String(firstCommand.key(), StandardCharsets.UTF_8))
                .doesNotContain(key.appId(), key.keyId(), key.nonce(), key.path());
        assertThat(factory.connectionCloseCount()).isEqualTo(2);
    }

    @Test
    void allowsReservationAfterServerSideExpiration() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        RedisCocoReplayStore store = new RedisCocoReplayStore(factory);
        CocoReplayKey key = key("expired");

        assertThat(store.reserve(key, Instant.ofEpochMilli(BASE_TIME_MILLIS + 1_000))).isTrue();
        factory.setServerTimeMillis(BASE_TIME_MILLIS + 1_000);

        assertThat(store.reserve(key, Instant.ofEpochMilli(BASE_TIME_MILLIS + 61_000))).isTrue();
    }

    @Test
    void rejectsAlreadyExpiredDeadlineWithoutIssuingSet() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        RedisCocoReplayStore store = new RedisCocoReplayStore(factory);

        assertThat(store.reserve(key("past"), Instant.ofEpochMilli(BASE_TIME_MILLIS))).isFalse();

        assertThat(factory.commands()).isEmpty();
        assertThat(factory.connectionCloseCount()).isEqualTo(1);
    }

    @Test
    void concurrentReservationsHaveOneWinner() throws Exception {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        RedisCocoReplayStore store = new RedisCocoReplayStore(factory);
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch ready = new CountDownLatch(12);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = java.util.stream.IntStream.range(0, 12)
                    .mapToObj(ignored -> executor.submit(() -> {
                        ready.countDown();
                        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                        return store.reserve(key("concurrent"), Instant.ofEpochMilli(BASE_TIME_MILLIS + 60_000));
                    }))
                    .toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            long winners = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(10, TimeUnit.SECONDS)) {
                    winners++;
                }
            }
            assertThat(winners).isEqualTo(1);
        }
        finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void connectionFailuresPropagateAndNeverBecomeDuplicateResults() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        factory.failConnectionAcquisition();
        RedisCocoReplayStore store = new RedisCocoReplayStore(factory);

        assertThatThrownBy(() -> store.reserve(key("connection-failure"),
                Instant.ofEpochMilli(BASE_TIME_MILLIS + 60_000)))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(factory.commands()).isEmpty();
    }

    @Test
    void closeFailurePropagatesAfterReservationAttempt() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        factory.failConnectionClose();
        RedisCocoReplayStore store = new RedisCocoReplayStore(factory);

        assertThatThrownBy(() -> store.reserve(key("close-failure"), Instant.ofEpochMilli(BASE_TIME_MILLIS + 60_000)))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(factory.commands()).hasSize(1);
    }

    @Test
    void closedStoreFailsClosedWithoutOpeningConnection() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        RedisCocoReplayStore store = new RedisCocoReplayStore(factory);
        store.close();

        assertThatThrownBy(() -> store.reserve(key("closed"), Instant.ofEpochMilli(BASE_TIME_MILLIS + 60_000)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
        assertThat(factory.connectionCount()).isZero();
    }

    private static CocoReplayKey key(String nonce) {
        return new CocoReplayKey("app-sensitive", "key-sensitive", "2026-01-01T00:00:00Z", nonce,
                "POST", "/payments/42");
    }

    private static byte[] expectedRedisKey(CocoReplayKey key) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.value().getBytes(StandardCharsets.UTF_8));
        return ("coco:replay:" + HexFormat.of().formatHex(digest)).getBytes(StandardCharsets.UTF_8);
    }

    private record SetCommand(byte[] key, byte[] value, long ttlMillis, SetCondition condition) {

        private SetCommand {
            key = Arrays.copyOf(key, key.length);
            value = Arrays.copyOf(value, value.length);
        }
    }

    private static final class StrictRedisConnectionFactory implements RedisConnectionFactory {

        private final AtomicLong serverTimeMillis;

        private final ConcurrentHashMap<ByteArrayKey, Long> reservations = new ConcurrentHashMap<>();

        private final List<SetCommand> commands = new java.util.concurrent.CopyOnWriteArrayList<>();

        private final AtomicInteger connectionCount = new AtomicInteger();

        private final AtomicInteger connectionCloseCount = new AtomicInteger();

        private volatile boolean connectionAcquisitionFails;

        private volatile boolean connectionCloseFails;

        private StrictRedisConnectionFactory(long serverTimeMillis) {
            this.serverTimeMillis = new AtomicLong(serverTimeMillis);
        }

        @Override
        public RedisConnection getConnection() {
            if (this.connectionAcquisitionFails) {
                throw new DataAccessResourceFailureException("Redis is unavailable");
            }
            this.connectionCount.incrementAndGet();
            return (RedisConnection) Proxy.newProxyInstance(RedisConnection.class.getClassLoader(),
                    new Class<?>[] { RedisConnection.class }, (proxy, method, arguments) -> switch (method.getName()) {
                        case "serverCommands" -> serverCommands();
                        case "stringCommands" -> stringCommands();
                        case "close" -> {
                            closeConnection();
                            yield null;
                        }
                        case "isClosed" -> false;
                        case "getNativeConnection" -> this;
                        default -> throw new AssertionError("Unexpected Redis connection operation: " + method);
                    });
        }

        @Override
        public boolean getConvertPipelineAndTxResults() {
            return false;
        }

        @Override
        public org.springframework.data.redis.connection.RedisClusterConnection getClusterConnection() {
            throw new AssertionError("Cluster multi-key connection must not be requested");
        }

        @Override
        public org.springframework.data.redis.connection.RedisSentinelConnection getSentinelConnection() {
            throw new AssertionError("Sentinel connection must not be requested");
        }

        @Override
        public org.springframework.dao.DataAccessException translateExceptionIfPossible(RuntimeException exception) {
            return null;
        }

        private RedisServerCommands serverCommands() {
            return (RedisServerCommands) Proxy.newProxyInstance(RedisServerCommands.class.getClassLoader(),
                    new Class<?>[] { RedisServerCommands.class }, (proxy, method, arguments) -> {
                        if (method.getName().equals("time") && arguments != null && arguments.length == 1
                                && arguments[0] == TimeUnit.MILLISECONDS) {
                            return this.serverTimeMillis.get();
                        }
                        throw new AssertionError("Unexpected Redis server operation: " + method);
                    });
        }

        private RedisStringCommands stringCommands() {
            return (RedisStringCommands) Proxy.newProxyInstance(RedisStringCommands.class.getClassLoader(),
                    new Class<?>[] { RedisStringCommands.class }, (proxy, method, arguments) -> {
                        if (!method.getName().equals("set") || arguments == null || arguments.length != 4) {
                            throw new AssertionError("Unexpected Redis string operation: " + method);
                        }
                        byte[] key = (byte[]) arguments[0];
                        byte[] value = (byte[]) arguments[1];
                        SetCondition condition = (SetCondition) arguments[2];
                        Expiration expiration = (Expiration) arguments[3];
                        if (condition.getKeyCondition() != SetCondition.ifAbsent().getKeyCondition()) {
                            throw new AssertionError("Redis reservation must use SET NX");
                        }
                        long ttlMillis = expiration.getExpirationTimeInMilliseconds();
                        if (ttlMillis <= 0) {
                            throw new AssertionError("Redis reservation must use a positive TTL");
                        }
                        this.commands.add(new SetCommand(key, value, ttlMillis, condition));
                        return reserve(key, ttlMillis);
                    });
        }

        private boolean reserve(byte[] key, long ttlMillis) {
            ByteArrayKey keyCopy = new ByteArrayKey(key);
            long expiresAtMillis = Math.addExact(this.serverTimeMillis.get(), ttlMillis);
            synchronized (this.reservations) {
                Long currentExpiresAtMillis = this.reservations.get(keyCopy);
                if (currentExpiresAtMillis != null && currentExpiresAtMillis > this.serverTimeMillis.get()) {
                    return false;
                }
                this.reservations.put(keyCopy, expiresAtMillis);
                return true;
            }
        }

        private void closeConnection() {
            this.connectionCloseCount.incrementAndGet();
            if (this.connectionCloseFails) {
                throw new DataAccessResourceFailureException("Redis close failed");
            }
        }

        private void setServerTimeMillis(long serverTimeMillis) {
            this.serverTimeMillis.set(serverTimeMillis);
        }

        private void failConnectionAcquisition() {
            this.connectionAcquisitionFails = true;
        }

        private void failConnectionClose() {
            this.connectionCloseFails = true;
        }

        private List<SetCommand> commands() {
            return this.commands;
        }

        private int connectionCount() {
            return this.connectionCount.get();
        }

        private int connectionCloseCount() {
            return this.connectionCloseCount.get();
        }
    }

    private record ByteArrayKey(byte[] value) {

        private ByteArrayKey {
            value = Arrays.copyOf(value, value.length);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ByteArrayKey that && Arrays.equals(this.value, that.value);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(this.value);
        }
    }
}
