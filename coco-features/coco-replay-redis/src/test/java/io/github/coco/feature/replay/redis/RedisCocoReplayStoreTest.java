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
import org.springframework.dao.QueryTimeoutException;
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
                .doesNotContain(key.appId(), key.keyId(), key.timestamp(), key.nonce(), key.method(), key.path());
        assertThat(factory.events()).containsExactly(
                ProtocolEvent.TIME_MILLISECONDS, ProtocolEvent.SET_NX_PX, ProtocolEvent.CLOSE,
                ProtocolEvent.TIME_MILLISECONDS, ProtocolEvent.SET_NX_PX, ProtocolEvent.CLOSE);
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
        assertThat(factory.events()).containsExactly(ProtocolEvent.TIME_MILLISECONDS, ProtocolEvent.CLOSE);
        assertThat(factory.connectionCloseCount()).isEqualTo(1);
    }

    @Test
    void preservesOneMillisecondAndLargestRepresentableTtl() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        RedisCocoReplayStore store = new RedisCocoReplayStore(factory);

        assertThat(store.reserve(key("one-millisecond"), Instant.ofEpochMilli(BASE_TIME_MILLIS + 1))).isTrue();
        assertThat(store.reserve(key("maximum"), Instant.ofEpochMilli(Long.MAX_VALUE))).isTrue();

        assertThat(factory.commands()).extracting(SetCommand::ttlMillis)
                .containsExactly(1L, Long.MAX_VALUE - BASE_TIME_MILLIS);
    }

    @Test
    void ttlUnderflowFailsClosedWithoutSendingSet() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        RedisCocoReplayStore store = new RedisCocoReplayStore(factory);

        assertThatThrownBy(() -> store.reserve(key("underflow"), Instant.ofEpochMilli(Long.MIN_VALUE)))
                .isInstanceOf(ArithmeticException.class);

        assertThat(factory.commands()).isEmpty();
        assertThat(factory.events()).containsExactly(ProtocolEvent.TIME_MILLISECONDS, ProtocolEvent.CLOSE);
    }

    @Test
    void usesOneNamespacedHashedKeyForRedisClusterRouting() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        RedisCocoReplayStore store = new RedisCocoReplayStore(factory);
        CocoReplayKey key = key("cluster-sensitive");

        assertThat(store.reserve(key, Instant.ofEpochMilli(BASE_TIME_MILLIS + 60_000))).isTrue();

        byte[] redisKey = factory.commands().get(0).key();
        assertThat(new String(redisKey, StandardCharsets.UTF_8)).matches("coco:replay:[0-9a-f]{64}");
        assertThat(redisClusterSlot(redisKey)).isBetween(0, 16_383);
        assertThat(factory.events()).containsExactly(
                ProtocolEvent.TIME_MILLISECONDS, ProtocolEvent.SET_NX_PX, ProtocolEvent.CLOSE);
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
        assertThat(factory.events()).isEmpty();
        assertThat(factory.connectionCloseCount()).isZero();
    }

    @Test
    void serverTimeFailureClosesConnectionAndFailsClosed() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        factory.failServerTime(new QueryTimeoutException("Redis TIME timed out"));
        RedisCocoReplayStore store = new RedisCocoReplayStore(factory);

        assertThatThrownBy(() -> store.reserve(key("time-timeout"), Instant.ofEpochMilli(BASE_TIME_MILLIS + 60_000)))
                .isInstanceOf(QueryTimeoutException.class);

        assertThat(factory.commands()).isEmpty();
        assertThat(factory.events()).containsExactly(ProtocolEvent.TIME_MILLISECONDS, ProtocolEvent.CLOSE);
    }

    @Test
    void setTimeoutClosesConnectionAndFailsClosed() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        factory.failSet(new QueryTimeoutException("Redis SET timed out"));
        RedisCocoReplayStore store = new RedisCocoReplayStore(factory);

        assertThatThrownBy(() -> store.reserve(key("set-timeout"), Instant.ofEpochMilli(BASE_TIME_MILLIS + 60_000)))
                .isInstanceOf(QueryTimeoutException.class);

        assertThat(factory.commands()).hasSize(1);
        assertThat(factory.events()).containsExactly(
                ProtocolEvent.TIME_MILLISECONDS, ProtocolEvent.SET_NX_PX, ProtocolEvent.CLOSE);
    }

    @Test
    void setConnectionInterruptionClosesConnectionAndFailsClosed() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        factory.failSet(new DataAccessResourceFailureException("Redis connection interrupted"));
        RedisCocoReplayStore store = new RedisCocoReplayStore(factory);

        assertThatThrownBy(() -> store.reserve(key("set-interrupted"),
                Instant.ofEpochMilli(BASE_TIME_MILLIS + 60_000)))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(factory.events()).containsExactly(
                ProtocolEvent.TIME_MILLISECONDS, ProtocolEvent.SET_NX_PX, ProtocolEvent.CLOSE);
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

    private static int redisClusterSlot(byte[] key) {
        int crc = 0;
        for (byte keyByte : key) {
            crc ^= (keyByte & 0xff) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) == 0 ? crc << 1 : (crc << 1) ^ 0x1021;
                crc &= 0xffff;
            }
        }
        return crc & 0x3fff;
    }

    private enum ProtocolEvent {

        TIME_MILLISECONDS,

        SET_NX_PX,

        CLOSE
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

        private final List<ProtocolEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();

        private final AtomicInteger connectionCount = new AtomicInteger();

        private final AtomicInteger connectionCloseCount = new AtomicInteger();

        private volatile boolean connectionAcquisitionFails;

        private volatile boolean connectionCloseFails;

        private volatile RuntimeException serverTimeFailure;

        private volatile RuntimeException setFailure;

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
                            this.events.add(ProtocolEvent.TIME_MILLISECONDS);
                            if (this.serverTimeFailure != null) {
                                throw this.serverTimeFailure;
                            }
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
                        this.events.add(ProtocolEvent.SET_NX_PX);
                        if (this.setFailure != null) {
                            throw this.setFailure;
                        }
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
            this.events.add(ProtocolEvent.CLOSE);
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

        private void failServerTime(RuntimeException failure) {
            this.serverTimeFailure = failure;
        }

        private void failSet(RuntimeException failure) {
            this.setFailure = failure;
        }

        private List<SetCommand> commands() {
            return this.commands;
        }

        private List<ProtocolEvent> events() {
            return this.events;
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
