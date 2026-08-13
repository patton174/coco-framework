package io.github.coco.feature.ratelimit.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.coco.feature.ratelimit.CocoRateLimitDecision;
import io.github.coco.feature.ratelimit.CocoRateLimitKey;
import io.github.coco.feature.ratelimit.CocoRateLimitPermit;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisSentinelConnection;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.script.ScriptExecutor;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.dao.DataAccessException;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

class RedisCocoRateLimitStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-08T08:00:00Z");

    @Test
    void passesClusterKeyAndExactLuaArguments() {
        RecordingScriptExecutor executor = new RecordingScriptExecutor("1:4");
        RedisCocoRateLimitStore store = store(executor, "tenant:rate:");
        Instant resetAt = NOW.plusSeconds(60);

        CocoRateLimitDecision decision = store.acquire(permit("orders", "customer-42", 5, resetAt));

        assertThat(decision).isEqualTo(new CocoRateLimitDecision(true, 5, 4, resetAt, false));
        assertThat(executor.invocations).hasSize(1);
        Invocation invocation = executor.invocations.get(0);
        assertThat(invocation.script()).contains("redis.replicate_commands()", "redis.call('TIME')",
                "redis.call('INCR', KEYS[1])",
                "redis.call('PEXPIREAT', KEYS[1], reset_at)");
        assertThat(invocation.script()).doesNotContain("PTTL");
        assertThat(invocation.keys()).singleElement()
                .asString()
                .matches("tenant:rate:\\{[0-9a-f]{64}}:" + resetAt.toEpochMilli());
        assertThat(invocation.keys().get(0)).doesNotContain("orders", "customer-42");
        assertThat(invocation.arguments()).containsExactly("5", Long.toString(resetAt.toEpochMilli()));
    }

    @Test
    void keepsOneClusterHashTagAcrossWindowsAndSeparatesSubjects() {
        RecordingScriptExecutor executor = new RecordingScriptExecutor("1:0", "1:0", "1:0");
        RedisCocoRateLimitStore store = store(executor, "coco:rate-limit");

        store.acquire(permit("api", "subject-a", 1, NOW.plusSeconds(60)));
        store.acquire(permit("api", "subject-a", 1, NOW.plusSeconds(120)));
        store.acquire(permit("api", "subject-b", 1, NOW.plusSeconds(60)));

        String firstTag = hashTag(executor.invocations.get(0).keys().get(0));
        String nextWindowTag = hashTag(executor.invocations.get(1).keys().get(0));
        String otherSubjectTag = hashTag(executor.invocations.get(2).keys().get(0));
        assertThat(nextWindowTag).isEqualTo(firstTag);
        assertThat(otherSubjectTag).isNotEqualTo(firstTag);
    }

    @Test
    void mapsLuaReturnProtocolWithoutLosingLongPrecision() {
        RecordingScriptExecutor executor = new RecordingScriptExecutor(
                "1:9223372036854775806", "0:0", "E:0");
        RedisCocoRateLimitStore store = store(executor, "coco:rate-limit");
        CocoRateLimitPermit permit = permit("api", "subject", Long.MAX_VALUE, NOW.plusSeconds(60));

        assertThat(store.acquire(permit)).isEqualTo(new CocoRateLimitDecision(true, Long.MAX_VALUE,
                Long.MAX_VALUE - 1, permit.resetAt(), false));
        assertThat(store.acquire(permit)).isEqualTo(new CocoRateLimitDecision(false, Long.MAX_VALUE,
                0, permit.resetAt(), false));
        assertThat(store.acquire(permit)).isEqualTo(new CocoRateLimitDecision(false, Long.MAX_VALUE,
                0, permit.resetAt(), true));
    }

    @Test
    void rejectsMalformedLuaResults() {
        List<String> invalidResults = Arrays.asList(null, "", "1", "2:0", "1:-1", "1:01", "1:5:0",
                "0:1", "E:1", "1:5");
        CocoRateLimitPermit permit = permit("api", "subject", 5, NOW.plusSeconds(60));

        for (String invalidResult : invalidResults) {
            RedisCocoRateLimitStore store = store(new RecordingScriptExecutor(invalidResult), "coco:rate-limit");
            assertThatThrownBy(() -> store.acquire(permit))
                    .as("invalid result %s", invalidResult)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("invalid result");
        }
    }

    @Test
    void delegatesPermitExpiryToRedisServerTime() {
        RecordingScriptExecutor executor = new RecordingScriptExecutor("1:0");
        RedisCocoRateLimitStore store = store(executor, "coco:rate-limit");
        CocoRateLimitPermit permit = permit("api", "subject", 1, NOW);

        assertThat(store.acquire(permit)).isEqualTo(new CocoRateLimitDecision(true, 1, 0, NOW, false));
        assertThat(executor.invocations).hasSize(1);
    }

    @Test
    void propagatesConnectionFailuresForCallerFailClosedHandling() {
        RedisConnectionFailureException failure = new RedisConnectionFailureException("offline");
        RedisCocoRateLimitStore store = store(new ThrowingScriptExecutor(failure), "coco:rate-limit");

        assertThatThrownBy(() -> store.acquire(permit("api", "subject", 1, NOW.plusSeconds(60))))
                .isSameAs(failure);
    }

    @Test
    void rejectsPrefixThatCanOverrideClusterHashTag() {
        CocoRateLimitRedisProperties properties = properties("tenant:{forced}");

        assertThatThrownBy(() -> new RedisCocoRateLimitStore(new RecordingScriptExecutor("1:0"), properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key-prefix");
    }

    @Test
    void rejectsDeadlineOutsideTheRedisEpochMillisecondRangeBeforeCallingRedis() {
        RecordingScriptExecutor executor = new RecordingScriptExecutor("1:0");
        RedisCocoRateLimitStore store = store(executor, "coco:rate-limit");

        assertThatThrownBy(() -> store.acquire(permit("api", "subject", 1, Instant.MIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("epoch milliseconds");
        assertThatThrownBy(() -> store.acquire(permit("api", "subject", 1, Instant.EPOCH)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after the Unix epoch");
        assertThatThrownBy(() -> store.acquire(permit("api", "subject", 1, Instant.MAX)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("epoch milliseconds");
        assertThat(executor.invocations).isEmpty();
    }

    @Test
    void closesConnectionAfterEachLuaInvocationAndRejectsAfterClose() {
        AtomicBoolean connectionClosed = new AtomicBoolean();
        RedisConnection connection = (RedisConnection) Proxy.newProxyInstance(RedisConnection.class.getClassLoader(),
                new Class<?>[] { RedisConnection.class }, (proxy, method, arguments) -> switch (method.getName()) {
                    case "scriptingCommands" -> Proxy.newProxyInstance(method.getReturnType().getClassLoader(),
                            new Class<?>[] { method.getReturnType() }, (scriptingProxy, scriptingMethod,
                                    scriptingArguments) -> {
                                        if ("eval".equals(scriptingMethod.getName())) {
                                            return "1:0".getBytes(StandardCharsets.UTF_8);
                                        }
                                        return null;
                                    });
                    case "close" -> {
                        connectionClosed.set(true);
                        yield null;
                    }
                    case "isClosed" -> connectionClosed.get();
                    case "getNativeConnection" -> null;
                    default -> null;
                });
        RedisCocoRateLimitStore store = new RedisCocoRateLimitStore(new SingleConnectionFactory(connection),
                new CocoRateLimitRedisProperties());

        assertThat(store.acquire(permit("api", "subject", 1, NOW.plusSeconds(60))).allowed()).isTrue();
        assertThat(connectionClosed).isTrue();

        store.close();
        assertThatThrownBy(() -> store.acquire(permit("api", "subject", 1, NOW.plusSeconds(60))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    private static RedisCocoRateLimitStore store(ScriptExecutor<String> executor, String prefix) {
        return new RedisCocoRateLimitStore(executor, properties(prefix));
    }

    private static CocoRateLimitRedisProperties properties(String prefix) {
        CocoRateLimitRedisProperties properties = new CocoRateLimitRedisProperties();
        properties.setKeyPrefix(prefix);
        return properties;
    }

    private static CocoRateLimitPermit permit(String route, String subject, long limit, Instant resetAt) {
        return new CocoRateLimitPermit(new CocoRateLimitKey(route, subject), limit, resetAt);
    }

    private static String hashTag(String key) {
        return key.substring(key.indexOf('{') + 1, key.indexOf('}'));
    }

    private record Invocation(String script, List<String> keys, List<Object> arguments) {
    }

    private static final class RecordingScriptExecutor implements ScriptExecutor<String> {

        private final List<String> results;

        private final List<Invocation> invocations = new ArrayList<>();

        private int resultIndex;

        private RecordingScriptExecutor(String... results) {
            this.results = Arrays.asList(results);
        }

        @Override
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            this.invocations.add(new Invocation(script.getScriptAsString(), List.copyOf(keys),
                    List.copyOf(Arrays.asList(args.clone()))));
            String result = this.results.get(Math.min(this.resultIndex, this.results.size() - 1));
            this.resultIndex++;
            return result == null ? null : script.getResultType().cast(result);
        }

        @Override
        public <T> T execute(RedisScript<T> script, RedisSerializer<?> argsSerializer,
                RedisSerializer<T> resultSerializer, List<String> keys, Object... args) {
            return execute(script, keys, args);
        }
    }

    private static final class ThrowingScriptExecutor implements ScriptExecutor<String> {

        private final RuntimeException failure;

        private ThrowingScriptExecutor(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            throw this.failure;
        }

        @Override
        public <T> T execute(RedisScript<T> script, RedisSerializer<?> argsSerializer,
                RedisSerializer<T> resultSerializer, List<String> keys, Object... args) {
            throw this.failure;
        }
    }

    private record SingleConnectionFactory(RedisConnection connection) implements RedisConnectionFactory {

        @Override public boolean getConvertPipelineAndTxResults() { return false; }
        @Override public RedisConnection getConnection() { return this.connection; }
        @Override public RedisClusterConnection getClusterConnection() { return null; }
        @Override public RedisSentinelConnection getSentinelConnection() { return null; }
        @Override public DataAccessException translateExceptionIfPossible(RuntimeException exception) { return null; }
    }
}
