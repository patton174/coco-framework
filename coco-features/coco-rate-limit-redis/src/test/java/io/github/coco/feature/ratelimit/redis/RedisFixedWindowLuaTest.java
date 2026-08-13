package io.github.coco.feature.ratelimit.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import io.github.coco.feature.ratelimit.CocoRateLimitDecision;
import io.github.coco.feature.ratelimit.CocoRateLimitKey;
import io.github.coco.feature.ratelimit.CocoRateLimitPermit;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.script.ScriptExecutor;
import org.springframework.data.redis.serializer.RedisSerializer;

class RedisFixedWindowLuaTest {

    @Test
    void executesActualLuaWithAbsoluteTtlAndLongRemaining() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-08T08:00:00Z"));
        LuaRedisScriptExecutor executor = new LuaRedisScriptExecutor(clock);
        RedisCocoRateLimitStore store = store(executor);
        Instant resetAt = clock.instant().plusSeconds(60);
        CocoRateLimitPermit permit = permit(Long.MAX_VALUE, resetAt);

        assertThat(store.acquire(permit).remaining()).isEqualTo(Long.MAX_VALUE - 1);
        clock.advanceSeconds(1);
        assertThat(store.acquire(permit).remaining()).isEqualTo(Long.MAX_VALUE - 2);
        assertThat(executor.ttlForLastKey()).isEqualTo(59_000);
        assertThat(executor.expiresAtForLastKey()).isEqualTo(resetAt.toEpochMilli());
    }

    @Test
    void allowsAtMostTheLimitUnderConcurrentLuaExecution() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-08T08:00:00Z"));
        LuaRedisScriptExecutor executor = new LuaRedisScriptExecutor(clock);
        RedisCocoRateLimitStore store = store(executor);
        Instant resetAt = clock.instant().plusSeconds(60);
        CocoRateLimitPermit permit = permit(20, resetAt);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Callable<CocoRateLimitDecision>> tasks = new ArrayList<>();
            for (int index = 0; index < 200; index++) {
                tasks.add(() -> store.acquire(permit));
            }

            List<CocoRateLimitDecision> decisions = pool.invokeAll(tasks).stream()
                    .map(RedisFixedWindowLuaTest::get)
                    .toList();
            List<Long> allowedRemaining = decisions.stream()
                    .filter(CocoRateLimitDecision::allowed)
                    .map(CocoRateLimitDecision::remaining)
                    .sorted()
                    .toList();

            assertThat(allowedRemaining).containsExactlyElementsOf(
                    java.util.stream.LongStream.range(0, 20).boxed().toList());
            assertThat(decisions).filteredOn(decision -> !decision.allowed()).hasSize(180)
                    .allMatch(decision -> decision.remaining() == 0 && !decision.capacityExhausted());
            assertThat(store.acquire(permit)).isEqualTo(new CocoRateLimitDecision(false, 20, 0,
                    resetAt, false));
        }
        finally {
            pool.shutdownNow();
        }
    }

    @Test
    void sharesTheSameKeyAcrossStoresAndIsolatesDifferentKeys() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-08T08:00:00Z"));
        LuaRedisScriptExecutor executor = new LuaRedisScriptExecutor(clock);
        RedisCocoRateLimitStore first = store(executor);
        RedisCocoRateLimitStore second = store(executor);
        Instant resetAt = clock.instant().plusSeconds(60);
        CocoRateLimitPermit shared = permit(1, resetAt);
        CocoRateLimitPermit isolated = new CocoRateLimitPermit(new CocoRateLimitKey("other-api", "203.0.113.10"),
                1, resetAt);

        assertThat(first.acquire(shared).allowed()).isTrue();
        assertThat(second.acquire(shared).allowed()).isFalse();
        assertThat(second.acquire(isolated).allowed()).isTrue();
    }

    @Test
    void startsASeparateCounterAtTheNextFixedWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-08T08:00:00Z"));
        LuaRedisScriptExecutor executor = new LuaRedisScriptExecutor(clock);
        RedisCocoRateLimitStore store = store(executor);
        CocoRateLimitPermit firstWindow = permit(1, clock.instant().plusSeconds(60));

        assertThat(store.acquire(firstWindow).allowed()).isTrue();
        assertThat(store.acquire(firstWindow).allowed()).isFalse();

        clock.advanceSeconds(60);
        CocoRateLimitPermit nextWindow = permit(1, clock.instant().plusSeconds(60));
        assertThat(store.acquire(nextWindow).allowed()).isTrue();
        assertThat(executor.entryCount()).isOne();
    }

    @Test
    void redisServerTimeRejectsAWindowThatExpiredDuringDispatch() {
        MutableClock applicationClock = new MutableClock(Instant.parse("2026-08-08T08:00:00Z"));
        MutableClock redisClock = new MutableClock(applicationClock.instant().plusSeconds(60));
        LuaRedisScriptExecutor executor = new LuaRedisScriptExecutor(redisClock);
        RedisCocoRateLimitStore store = store(executor);
        Instant resetAt = applicationClock.instant().plusSeconds(60);

        assertThat(store.acquire(permit(1, resetAt)))
                .isEqualTo(new CocoRateLimitDecision(false, 1, 0, resetAt, true));
        assertThat(executor.entryCount()).isZero();
    }

    private static RedisCocoRateLimitStore store(ScriptExecutor<String> executor) {
        return new RedisCocoRateLimitStore(executor, new CocoRateLimitRedisProperties());
    }

    private static CocoRateLimitPermit permit(long limit, Instant resetAt) {
        return new CocoRateLimitPermit(new CocoRateLimitKey("api", "203.0.113.10"), limit, resetAt);
    }

    private static CocoRateLimitDecision get(Future<CocoRateLimitDecision> future) {
        try {
            return future.get();
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class LuaRedisScriptExecutor implements ScriptExecutor<String> {

        private final Clock clock;

        private final Map<String, Entry> entries = new HashMap<>();

        private String lastKey;

        private LuaRedisScriptExecutor(Clock clock) {
            this.clock = clock;
        }

        @Override
        public synchronized <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            if (keys.size() != 1 || args.length != 2 || script.getResultType() != String.class) {
                throw new AssertionError("Unexpected Redis script invocation");
            }
            this.lastKey = keys.get(0);

            Globals globals = JsePlatform.standardGlobals();
            LuaTable redis = new LuaTable();
            redis.set("call", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs invocation) {
                    return redisCall(invocation);
                }
            });
            redis.set("error_reply", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue message) {
                    throw new LuaError(message.tojstring());
                }
            });
            redis.set("replicate_commands", new ZeroArgFunction() {
                @Override
                public LuaValue call() {
                    return LuaValue.TRUE;
                }
            });
            globals.set("redis", redis);
            globals.set("KEYS", table(keys.toArray()));
            globals.set("ARGV", table(args));

            LuaValue result = globals.load(script.getScriptAsString(), "fixed-window.lua").call();
            return script.getResultType().cast(result.tojstring());
        }

        @Override
        public <T> T execute(RedisScript<T> script, RedisSerializer<?> argsSerializer,
                RedisSerializer<T> resultSerializer, List<String> keys, Object... args) {
            return execute(script, keys, args);
        }

        private Varargs redisCall(Varargs invocation) {
            String command = invocation.arg(1).tojstring();
            return switch (command) {
                case "TIME" -> redisTime();
                case "GET" -> get(invocation.arg(2).tojstring());
                case "SET" -> set(invocation.arg(2).tojstring(), invocation.arg(3).tojstring());
                case "INCR" -> increment(invocation.arg(2).tojstring());
                case "PTTL" -> pttl(invocation.arg(2).tojstring());
                case "PEXPIREAT" -> expireAt(invocation.arg(2).tojstring(), invocation.arg(3).tolong());
                default -> throw new LuaError("Unsupported Redis command: " + command);
            };
        }

        private LuaValue redisTime() {
            Instant now = this.clock.instant();
            LuaTable result = new LuaTable();
            result.set(1, Long.toString(now.getEpochSecond()));
            result.set(2, String.format("%06d", now.getNano() / 1_000));
            return result;
        }

        private LuaValue get(String key) {
            Entry entry = liveEntry(key);
            return entry == null ? LuaValue.FALSE : LuaValue.valueOf(entry.value());
        }

        private LuaValue set(String key, String value) {
            this.entries.put(key, new Entry(value, null));
            return LuaValue.valueOf("OK");
        }

        private LuaValue increment(String key) {
            Entry entry = liveEntry(key);
            if (entry == null) {
                throw new LuaError("Missing counter");
            }
            long updated = Math.addExact(Long.parseLong(entry.value()), 1);
            this.entries.put(key, new Entry(Long.toString(updated), entry.expiresAtEpochMillis()));
            return LuaValue.valueOf(Long.toString(updated));
        }

        private LuaValue pttl(String key) {
            Entry entry = liveEntry(key);
            if (entry == null) {
                return LuaValue.valueOf(-2);
            }
            if (entry.expiresAtEpochMillis() == null) {
                return LuaValue.valueOf(-1);
            }
            return LuaValue.valueOf((double) (entry.expiresAtEpochMillis() - this.clock.instant().toEpochMilli()));
        }

        private LuaValue expireAt(String key, long resetAtEpochMillis) {
            Entry entry = liveEntry(key);
            if (entry == null) {
                return LuaValue.ZERO;
            }
            this.entries.put(key, new Entry(entry.value(), resetAtEpochMillis));
            return LuaValue.ONE;
        }

        private Entry liveEntry(String key) {
            Entry entry = this.entries.get(key);
            if (entry != null && entry.expiresAtEpochMillis() != null
                    && entry.expiresAtEpochMillis() <= this.clock.instant().toEpochMilli()) {
                this.entries.remove(key);
                return null;
            }
            return entry;
        }

        private long ttlForLastKey() {
            Entry entry = liveEntry(this.lastKey);
            return entry.expiresAtEpochMillis() - this.clock.instant().toEpochMilli();
        }

        private long expiresAtForLastKey() {
            return liveEntry(this.lastKey).expiresAtEpochMillis();
        }

        private int entryCount() {
            long now = this.clock.instant().toEpochMilli();
            this.entries.entrySet().removeIf(entry -> entry.getValue().expiresAtEpochMillis() != null
                    && entry.getValue().expiresAtEpochMillis() <= now);
            return this.entries.size();
        }

        private static LuaTable table(Object[] values) {
            LuaTable table = new LuaTable();
            for (int index = 0; index < values.length; index++) {
                table.set(index + 1, String.valueOf(values[index]));
            }
            return table;
        }

        private record Entry(String value, Long expiresAtEpochMillis) {
        }
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;

        private MutableClock(Instant instant) {
            this.instant = new AtomicReference<>(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return this.instant.get();
        }

        private void advanceSeconds(long seconds) {
            this.instant.updateAndGet(value -> value.plusSeconds(seconds));
        }
    }
}
