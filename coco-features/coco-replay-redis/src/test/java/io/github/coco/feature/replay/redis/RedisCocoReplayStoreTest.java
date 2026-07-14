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
import org.springframework.data.redis.connection.RedisScriptingCommands;
import org.springframework.data.redis.connection.ReturnType;

class RedisCocoReplayStoreTest {

    private static final long BASE_TIME_MILLIS = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();

    @Test
    void reservesOnceUsingSingleKeyAtomicScript() throws Exception {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        RedisCocoReplayStore store = new RedisCocoReplayStore(factory);
        CocoReplayKey key = key("sensitive-nonce");

        assertThat(store.reserve(key, Instant.ofEpochMilli(BASE_TIME_MILLIS + 60_000))).isTrue();
        assertThat(store.reserve(key, Instant.ofEpochMilli(BASE_TIME_MILLIS + 120_000))).isFalse();

        List<EvalCommand> commands = factory.commands();
        assertThat(commands).hasSize(2);
        EvalCommand firstCommand = commands.get(0);
        assertThat(firstCommand.key()).isEqualTo(expectedRedisKey(key));
        assertThat(firstCommand.keyCount()).isOne();
        assertThat(firstCommand.returnType()).isEqualTo(ReturnType.INTEGER);
        assertThat(firstCommand.deadline()).isEqualTo(Long.toString(BASE_TIME_MILLIS + 60_000)
                .getBytes(StandardCharsets.UTF_8));
        assertThat(firstCommand.value()).containsExactly((byte) 1);
        assertThat(firstCommand.ttlMillis()).isEqualTo(60_000);
        assertThat(new String(firstCommand.key(), StandardCharsets.UTF_8))
                .doesNotContain(key.appId(), key.keyId(), key.timestamp(), key.nonce(), key.method(), key.path());
        assertThat(factory.events()).containsExactly(ProtocolEvent.EVAL_ONE_KEY, ProtocolEvent.CLOSE,
                ProtocolEvent.EVAL_ONE_KEY, ProtocolEvent.CLOSE);
        assertThat(factory.connectionCloseCount()).isEqualTo(2);
    }

    @Test
    void scriptContainsTimeAndSetNxPxWithoutDynamicKeyMaterial() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        CocoReplayKey key = key("no-script-leak");

        assertThat(new RedisCocoReplayStore(factory).reserve(key,
                Instant.ofEpochMilli(BASE_TIME_MILLIS + 60_000))).isTrue();

        String script = new String(factory.commands().get(0).script(), StandardCharsets.UTF_8);
        assertThat(script).contains("redis.replicate_commands()", "redis.call('TIME')", "KEYS[1]", "ARGV[1]",
                "ARGV[2]", "'NX'", "'PX'")
                .doesNotContain(key.appId(), key.keyId(), key.timestamp(), key.nonce(), key.method(), key.path());
    }

    @Test
    void allowsReservationAfterKeyNodeExpiration() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        RedisCocoReplayStore store = new RedisCocoReplayStore(factory);
        CocoReplayKey key = key("expired");

        assertThat(store.reserve(key, Instant.ofEpochMilli(BASE_TIME_MILLIS + 1_000))).isTrue();
        factory.setServerTimeMillis(BASE_TIME_MILLIS + 1_000);

        assertThat(store.reserve(key, Instant.ofEpochMilli(BASE_TIME_MILLIS + 61_000))).isTrue();
    }

    @Test
    void rejectsAlreadyExpiredDeadlineWithinAtomicScript() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        RedisCocoReplayStore store = new RedisCocoReplayStore(factory);

        assertThat(store.reserve(key("past"), Instant.ofEpochMilli(BASE_TIME_MILLIS))).isFalse();

        assertThat(factory.commands()).hasSize(1);
        assertThat(factory.events()).containsExactly(ProtocolEvent.EVAL_ONE_KEY, ProtocolEvent.CLOSE);
    }

    @Test
    void rejectsNegativeDeadlineWithinAtomicScript() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);

        assertThat(new RedisCocoReplayStore(factory).reserve(key("negative"), Instant.ofEpochMilli(Long.MIN_VALUE)))
                .isFalse();
        assertThat(factory.commands()).hasSize(1);
    }

    @Test
    void preservesOneMillisecondAndLargestRepresentableTtl() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        RedisCocoReplayStore store = new RedisCocoReplayStore(factory);

        assertThat(store.reserve(key("one-millisecond"), Instant.ofEpochMilli(BASE_TIME_MILLIS + 1))).isTrue();
        assertThat(store.reserve(key("maximum"), Instant.ofEpochMilli(Long.MAX_VALUE))).isTrue();

        assertThat(factory.commands()).extracting(EvalCommand::ttlMillis)
                .containsExactly(1L, Long.MAX_VALUE - BASE_TIME_MILLIS);
    }

    @Test
    void usesExactlyOneNamespacedHashedKeyForRedisClusterRouting() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        RedisCocoReplayStore store = new RedisCocoReplayStore(factory);
        CocoReplayKey key = key("cluster-sensitive");

        assertThat(store.reserve(key, Instant.ofEpochMilli(BASE_TIME_MILLIS + 60_000))).isTrue();

        byte[] redisKey = factory.commands().get(0).key();
        assertThat(new String(redisKey, StandardCharsets.UTF_8)).matches("coco:replay:[0-9a-f]{64}");
        assertThat(redisClusterSlot(redisKey)).isBetween(0, 16_383);
        assertThat(factory.clusterConnectionRequested()).isFalse();
    }

    @Test
    void computesTtlFromTheOwningClusterNodeClock() throws Exception {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        CocoReplayKey key = key("node-clock");
        int slot = redisClusterSlot(expectedRedisKey(key));
        factory.setServerTimeMillisForSlot(slot, BASE_TIME_MILLIS + 59_000);

        assertThat(new RedisCocoReplayStore(factory).reserve(key,
                Instant.ofEpochMilli(BASE_TIME_MILLIS + 60_000))).isTrue();

        assertThat(factory.commands().get(0).nodeTimeMillis()).isEqualTo(BASE_TIME_MILLIS + 59_000);
        assertThat(factory.commands().get(0).ttlMillis()).isEqualTo(1_000);
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

        assertThatThrownBy(() -> new RedisCocoReplayStore(factory).reserve(key("connection-failure"),
                Instant.ofEpochMilli(BASE_TIME_MILLIS + 60_000)))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(factory.commands()).isEmpty();
        assertThat(factory.events()).isEmpty();
    }

    @Test
    void scriptTimeoutClosesConnectionAndFailsClosed() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        factory.failEval(new QueryTimeoutException("Redis script timed out"));

        assertThatThrownBy(() -> new RedisCocoReplayStore(factory).reserve(key("script-timeout"),
                Instant.ofEpochMilli(BASE_TIME_MILLIS + 60_000)))
                .isInstanceOf(QueryTimeoutException.class);
        assertThat(factory.events()).containsExactly(ProtocolEvent.EVAL_ONE_KEY, ProtocolEvent.CLOSE);
    }

    @Test
    void scriptConnectionInterruptionClosesConnectionAndFailsClosed() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        factory.failEval(new DataAccessResourceFailureException("Redis connection interrupted"));

        assertThatThrownBy(() -> new RedisCocoReplayStore(factory).reserve(key("script-interrupted"),
                Instant.ofEpochMilli(BASE_TIME_MILLIS + 60_000)))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(factory.events()).containsExactly(ProtocolEvent.EVAL_ONE_KEY, ProtocolEvent.CLOSE);
    }

    @Test
    void unexpectedScriptResultFailsClosed() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        factory.setEvalResult(2L);

        assertThatThrownBy(() -> new RedisCocoReplayStore(factory).reserve(key("unexpected-result"),
                Instant.ofEpochMilli(BASE_TIME_MILLIS + 60_000)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no result");
    }

    @Test
    void nullScriptResultFailsClosed() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        factory.setEvalResult(null);

        assertThatThrownBy(() -> new RedisCocoReplayStore(factory).reserve(key("null-result"),
                Instant.ofEpochMilli(BASE_TIME_MILLIS + 60_000)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no result");
    }

    @Test
    void closeFailurePropagatesAfterReservationAttempt() {
        StrictRedisConnectionFactory factory = new StrictRedisConnectionFactory(BASE_TIME_MILLIS);
        factory.failConnectionClose();

        assertThatThrownBy(() -> new RedisCocoReplayStore(factory).reserve(key("close-failure"),
                Instant.ofEpochMilli(BASE_TIME_MILLIS + 60_000)))
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

        EVAL_ONE_KEY,

        CLOSE
    }

    private record EvalCommand(byte[] script, ReturnType returnType, int keyCount, byte[] key, byte[] deadline,
            byte[] value, long ttlMillis, long nodeTimeMillis) {

        private EvalCommand {
            script = Arrays.copyOf(script, script.length);
            key = Arrays.copyOf(key, key.length);
            deadline = Arrays.copyOf(deadline, deadline.length);
            value = Arrays.copyOf(value, value.length);
        }
    }

    private static final class StrictRedisConnectionFactory implements RedisConnectionFactory {

        private final AtomicLong defaultServerTimeMillis;

        private final ConcurrentHashMap<Integer, Long> serverTimesBySlot = new ConcurrentHashMap<>();

        private final ConcurrentHashMap<ByteArrayKey, Long> reservations = new ConcurrentHashMap<>();

        private final List<EvalCommand> commands = new java.util.concurrent.CopyOnWriteArrayList<>();

        private final List<ProtocolEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();

        private final AtomicInteger connectionCount = new AtomicInteger();

        private final AtomicInteger connectionCloseCount = new AtomicInteger();

        private volatile boolean connectionAcquisitionFails;

        private volatile boolean connectionCloseFails;

        private volatile RuntimeException evalFailure;

        private volatile Long evalResult;

        private volatile boolean evalResultConfigured;

        private volatile boolean clusterConnectionRequested;

        private StrictRedisConnectionFactory(long serverTimeMillis) {
            this.defaultServerTimeMillis = new AtomicLong(serverTimeMillis);
        }

        @Override
        public RedisConnection getConnection() {
            if (this.connectionAcquisitionFails) {
                throw new DataAccessResourceFailureException("Redis is unavailable");
            }
            this.connectionCount.incrementAndGet();
            return (RedisConnection) Proxy.newProxyInstance(RedisConnection.class.getClassLoader(),
                    new Class<?>[] { RedisConnection.class }, (proxy, method, arguments) -> switch (method.getName()) {
                        case "scriptingCommands" -> scriptingCommands();
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
            this.clusterConnectionRequested = true;
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

        private RedisScriptingCommands scriptingCommands() {
            return (RedisScriptingCommands) Proxy.newProxyInstance(RedisScriptingCommands.class.getClassLoader(),
                    new Class<?>[] { RedisScriptingCommands.class }, (proxy, method, arguments) -> {
                        if (!method.getName().equals("eval") || arguments == null || arguments.length != 4) {
                            throw new AssertionError("Unexpected Redis scripting operation: " + method);
                        }
                        byte[] script = (byte[]) arguments[0];
                        ReturnType returnType = (ReturnType) arguments[1];
                        int keyCount = (int) arguments[2];
                        byte[][] keysAndArgs = (byte[][]) arguments[3];
                        return eval(script, returnType, keyCount, keysAndArgs);
                    });
        }

        private Long eval(byte[] script, ReturnType returnType, int keyCount, byte[][] keysAndArgs) {
            if (returnType != ReturnType.INTEGER || keyCount != 1 || keysAndArgs.length != 3) {
                throw new AssertionError("Redis replay must use one-key EVAL with integer result");
            }
            validateScript(script);
            byte[] key = keysAndArgs[0];
            byte[] deadline = keysAndArgs[1];
            byte[] value = keysAndArgs[2];
            if (!Arrays.equals(value, new byte[] { 1 })) {
                throw new AssertionError("Redis replay must use the fixed binary reservation value");
            }
            long nodeTimeMillis = serverTimeMillis(key);
            long ttlMillis = ttlMillis(deadline, nodeTimeMillis);
            this.commands.add(new EvalCommand(script, returnType, keyCount, key, deadline, value, ttlMillis,
                    nodeTimeMillis));
            this.events.add(ProtocolEvent.EVAL_ONE_KEY);
            if (this.evalFailure != null) {
                throw this.evalFailure;
            }
            if (this.evalResultConfigured) {
                return this.evalResult;
            }
            return ttlMillis <= 0 ? 0L : reserve(key, ttlMillis, nodeTimeMillis) ? 1L : 0L;
        }

        private static void validateScript(byte[] script) {
            String source = new String(script, StandardCharsets.UTF_8);
            if (!source.contains("redis.replicate_commands()") || !source.contains("redis.call('TIME')")
                    || !source.contains("KEYS[1]")
                    || !source.contains("ARGV[1]") || !source.contains("ARGV[2]")
                    || !source.contains("'NX'") || !source.contains("'PX'")) {
                throw new AssertionError("Redis replay must use the fixed atomic TIME plus SET NX PX script");
            }
        }

        private static long ttlMillis(byte[] deadline, long nodeTimeMillis) {
            long deadlineMillis = Long.parseLong(new String(deadline, StandardCharsets.UTF_8));
            if (deadlineMillis <= nodeTimeMillis) {
                return 0;
            }
            return Math.subtractExact(deadlineMillis, nodeTimeMillis);
        }

        private boolean reserve(byte[] key, long ttlMillis, long nodeTimeMillis) {
            ByteArrayKey keyCopy = new ByteArrayKey(key);
            long expiresAtMillis = Math.addExact(nodeTimeMillis, ttlMillis);
            synchronized (this.reservations) {
                Long currentExpiresAtMillis = this.reservations.get(keyCopy);
                if (currentExpiresAtMillis != null && currentExpiresAtMillis > nodeTimeMillis) {
                    return false;
                }
                this.reservations.put(keyCopy, expiresAtMillis);
                return true;
            }
        }

        private long serverTimeMillis(byte[] key) {
            return this.serverTimesBySlot.getOrDefault(redisClusterSlot(key), this.defaultServerTimeMillis.get());
        }

        private void closeConnection() {
            this.events.add(ProtocolEvent.CLOSE);
            this.connectionCloseCount.incrementAndGet();
            if (this.connectionCloseFails) {
                throw new DataAccessResourceFailureException("Redis close failed");
            }
        }

        private void setServerTimeMillis(long serverTimeMillis) {
            this.defaultServerTimeMillis.set(serverTimeMillis);
        }

        private void setServerTimeMillisForSlot(int slot, long serverTimeMillis) {
            this.serverTimesBySlot.put(slot, serverTimeMillis);
        }

        private void failConnectionAcquisition() {
            this.connectionAcquisitionFails = true;
        }

        private void failConnectionClose() {
            this.connectionCloseFails = true;
        }

        private void failEval(RuntimeException failure) {
            this.evalFailure = failure;
        }

        private void setEvalResult(Long result) {
            this.evalResult = result;
            this.evalResultConfigured = true;
        }

        private List<EvalCommand> commands() {
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

        private boolean clusterConnectionRequested() {
            return this.clusterConnectionRequested;
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
