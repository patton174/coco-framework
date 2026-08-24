package io.github.coco.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.coco.feature.lock.CocoLockManager;
import io.github.coco.feature.lock.CocoLockProperties;
import io.github.coco.feature.lock.CocoLockRequest;
import io.github.coco.feature.lock.CocoLockResult;
import io.github.coco.feature.lock.CocoLockStore;
import io.github.coco.feature.lock.DefaultCocoLockManager;
import io.github.coco.feature.lock.RedisCocoLockStore;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

class CocoLockTaskExecutionGuardTest {

    @Test
    void twoSchedulerInstancesShareRedisLockAndReleaseOnlyTheirOwnHandle() {
        MapBackedRedisTemplate redis = new MapBackedRedisTemplate();
        CocoLockProperties lockProperties = new CocoLockProperties();
        lockProperties.setWatchdogEnabled(false);
        CocoSchedulingProperties schedulingProperties = new CocoSchedulingProperties();
        schedulingProperties.getGuard().setLease(Duration.ofSeconds(10));
        try (DefaultCocoLockManager firstManager = new DefaultCocoLockManager(
                new RedisCocoLockStore(redis, "coco:lock:", Clock.systemUTC()), lockProperties, Clock.systemUTC());
                DefaultCocoLockManager secondManager = new DefaultCocoLockManager(
                        new RedisCocoLockStore(redis, "coco:lock:", Clock.systemUTC()), lockProperties, Clock.systemUTC())) {
            CocoLockTaskExecutionGuard first = new CocoLockTaskExecutionGuard(firstManager, schedulingProperties.getGuard());
            CocoLockTaskExecutionGuard second = new CocoLockTaskExecutionGuard(secondManager, schedulingProperties.getGuard());

            assertThat(first.tryAcquire("inventory#refresh")).isTrue();
            assertThat(second.tryAcquire("inventory#refresh")).isFalse();
            first.release("inventory#refresh");
            assertThat(second.tryAcquire("inventory#refresh")).isTrue();
            second.release("inventory#refresh");
        }
    }

    @Test
    void unavailableLockFailsTheTriggerWithoutRunningTheTask() {
        CocoLockManager unavailable = request -> new CocoLockResult(CocoLockStore.AcquireResult.UNAVAILABLE, null);
        CocoLockTaskExecutionGuard guard = new CocoLockTaskExecutionGuard(unavailable,
                new CocoSchedulingProperties().getGuard());
        AtomicInteger executions = new AtomicInteger();
        ManualTaskScheduler springScheduler = new ManualTaskScheduler();
        CocoTaskDefinitionValidator validator = new CocoTaskDefinitionValidator(new CocoSchedulingMessageResolver(null));
        DefaultCocoTaskScheduler scheduler = new DefaultCocoTaskScheduler(springScheduler, guard, List.of(), validator,
                new CocoSchedulingProperties().getShutdown());

        scheduler.register(CocoTaskDefinition.builder("redis-failure", executions::incrementAndGet)
                .fixedRate(Duration.ofSeconds(1)).build());
        springScheduler.latest().run();

        assertThat(executions).hasValue(0);
        assertThat(scheduler.status("redis-failure")).hasValueSatisfying(status ->
                assertThat(status.outcome()).isEqualTo(CocoTaskExecutionOutcome.FAILED));
    }

    @Test
    void releaseRemovesThreadLocalHandleBeforeCloseFailure() {
        AtomicInteger releases = new AtomicInteger();
        CocoLockManager manager = request -> new CocoLockResult(CocoLockStore.AcquireResult.ACQUIRED,
                new ThrowingHandle(releases));
        CocoLockTaskExecutionGuard guard = new CocoLockTaskExecutionGuard(manager, new CocoSchedulingProperties().getGuard());

        assertThat(guard.tryAcquire("cleanup")).isTrue();
        assertThatThrownBy(() -> guard.release("cleanup")).isInstanceOf(IllegalStateException.class);
        guard.release("cleanup");
        assertThat(releases).hasValue(1);
    }

    private static final class ThrowingHandle implements io.github.coco.feature.lock.CocoLockHandle {
        private final AtomicInteger releases;
        private ThrowingHandle(AtomicInteger releases) { this.releases = releases; }
        @Override public io.github.coco.feature.lock.CocoLockLease lease() { throw new UnsupportedOperationException(); }
        @Override public boolean reentrant() { return false; }
        @Override public boolean lost() { return false; }
        @Override public void close() { this.releases.incrementAndGet(); throw new IllegalStateException("release failed"); }
    }

    private static final class MapBackedRedisTemplate extends StringRedisTemplate {
        private final Map<String, String> values = new ConcurrentHashMap<>();

        private MapBackedRedisTemplate() {
            super(new LettuceConnectionFactory());
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... arguments) {
            String key = keys.get(0);
            String owner = arguments[0].toString();
            String source = script.getScriptAsString();
            Long result;
            if (source.contains("'SET'")) {
                result = this.values.putIfAbsent(key, owner) == null ? 1L : 0L;
            }
            else if (source.contains("'PEXPIRE'")) {
                result = owner.equals(this.values.get(key)) ? 1L : 0L;
            }
            else {
                result = this.values.remove(key, owner) ? 1L : 0L;
            }
            return (T) result;
        }
    }
}
