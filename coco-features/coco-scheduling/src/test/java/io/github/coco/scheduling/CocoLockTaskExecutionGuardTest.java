package io.github.coco.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.coco.feature.lock.CocoLockException;
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

    @Test
    void lostLeaseAfterNormalTaskReturnPublishesFailedInsteadOfSucceededAndCanClose() {
        assertLostLeaseAfterNormalTaskReturn(CocoLockStore.RenewResult.UNAVAILABLE);
        assertLostLeaseAfterNormalTaskReturn(CocoLockStore.RenewResult.NOT_OWNER);
    }

    @Test
    void blockedRenewalThatReturnsNotOwnerAfterSchedulerValidationFailsTheTask() throws Exception {
        assertBlockedRenewalAfterSchedulerValidationFails(CocoLockStore.RenewResult.NOT_OWNER);
    }

    @Test
    void blockedRenewalThatReturnsUnavailableAfterSchedulerValidationFailsTheTask() throws Exception {
        assertBlockedRenewalAfterSchedulerValidationFails(CocoLockStore.RenewResult.UNAVAILABLE);
    }

    @Test
    void rejectedStoreReleaseFailsTheTaskInsteadOfPublishingSuccess() {
        CocoLockProperties lockProperties = new CocoLockProperties();
        lockProperties.setWatchdogEnabled(false);
        ReleaseFalseStore store = new ReleaseFalseStore();
        CocoSchedulingProperties schedulingProperties = new CocoSchedulingProperties();
        ManualTaskScheduler springScheduler = new ManualTaskScheduler();
        List<CocoTaskExecutionEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicInteger executions = new AtomicInteger();
        CocoTaskDefinitionValidator validator = new CocoTaskDefinitionValidator(new CocoSchedulingMessageResolver(null));
        try (DefaultCocoLockManager manager = new DefaultCocoLockManager(store, lockProperties, Clock.systemUTC())) {
            CocoLockTaskExecutionGuard guard = new CocoLockTaskExecutionGuard(manager, schedulingProperties.getGuard());
            DefaultCocoTaskScheduler scheduler = new DefaultCocoTaskScheduler(springScheduler, guard, List.of(events::add),
                    validator, schedulingProperties.getShutdown());
            scheduler.register(CocoTaskDefinition.builder("release-false", executions::incrementAndGet)
                    .fixedRate(Duration.ofSeconds(1)).build());

            springScheduler.latest().run();
            scheduler.close();

            assertThat(executions).hasValue(1);
            assertThat(store.released).hasValue(1);
            assertThat(events).extracting(CocoTaskExecutionEvent::outcome)
                    .containsExactly(CocoTaskExecutionOutcome.STARTED, CocoTaskExecutionOutcome.FAILED);
            assertThat(events.get(1).failureType()).isEqualTo(CocoLockException.class.getName());
        }
    }

    private static void assertLostLeaseAfterNormalTaskReturn(CocoLockStore.RenewResult renewResult) {
        CocoLockProperties lockProperties = new CocoLockProperties();
        lockProperties.setWatchdogInterval(Duration.ofMillis(1));
        LostRenewStore store = new LostRenewStore(renewResult);
        CocoSchedulingProperties schedulingProperties = new CocoSchedulingProperties();
        ManualTaskScheduler springScheduler = new ManualTaskScheduler();
        AtomicInteger executions = new AtomicInteger();
        List<CocoTaskExecutionEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        CocoTaskDefinitionValidator validator = new CocoTaskDefinitionValidator(new CocoSchedulingMessageResolver(null));
        try (DefaultCocoLockManager manager = new DefaultCocoLockManager(store, lockProperties, Clock.systemUTC())) {
            CocoLockTaskExecutionGuard guard = new CocoLockTaskExecutionGuard(manager, schedulingProperties.getGuard());
            DefaultCocoTaskScheduler scheduler = new DefaultCocoTaskScheduler(springScheduler, guard, List.of(events::add),
                    validator, schedulingProperties.getShutdown());
            String taskName = "lease-lost-" + renewResult.name().toLowerCase();
            scheduler.register(CocoTaskDefinition.builder(taskName, () -> {
                assertThat(await(store.renewEntered)).isTrue();
                store.allowRenew.countDown();
                assertThat(awaitInvalid(guard, taskName)).isTrue();
                executions.incrementAndGet();
            }).fixedRate(Duration.ofSeconds(1)).build());

            springScheduler.latest().run();
            scheduler.close();

            assertThat(executions).hasValue(1);
            assertThat(events).extracting(CocoTaskExecutionEvent::outcome)
                    .containsExactly(CocoTaskExecutionOutcome.STARTED, CocoTaskExecutionOutcome.FAILED);
            assertThat(events).noneMatch(event -> event.outcome() == CocoTaskExecutionOutcome.SUCCEEDED);
            assertThat(events.get(1).failureType()).isEqualTo(CocoSchedulingException.class.getName());
            assertThat(springScheduler.latest().future().cancelled()).isTrue();
            assertThat(validator.error(CocoSchedulingMessage.GUARD_EXECUTION_INVALID, taskName).getCode())
                    .isEqualTo("coco.scheduling.guard-execution-invalid");
        }
    }

    private static void assertBlockedRenewalAfterSchedulerValidationFails(CocoLockStore.RenewResult renewResult)
            throws Exception {
        CocoLockProperties lockProperties = new CocoLockProperties();
        lockProperties.setWatchdogInterval(Duration.ofMillis(1));
        LostRenewStore store = new LostRenewStore(renewResult);
        CocoSchedulingProperties schedulingProperties = new CocoSchedulingProperties();
        ManualTaskScheduler springScheduler = new ManualTaskScheduler();
        List<CocoTaskExecutionEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        CocoTaskDefinitionValidator validator = new CocoTaskDefinitionValidator(new CocoSchedulingMessageResolver(null));
        CountDownLatch businessValidatedOwnership = new CountDownLatch(1);
        CountDownLatch schedulerValidatedOwnership = new CountDownLatch(1);
        CountDownLatch releaseStarted = new CountDownLatch(1);
        AtomicBoolean schedulerOwnershipValid = new AtomicBoolean();
        AtomicInteger executions = new AtomicInteger();
        try (DefaultCocoLockManager manager = new DefaultCocoLockManager(store, lockProperties, Clock.systemUTC())) {
            CocoLockTaskExecutionGuard lockGuard = new CocoLockTaskExecutionGuard(manager,
                    schedulingProperties.getGuard());
            CocoTaskExecutionGuard observingGuard = new CocoTaskExecutionGuard() {
                @Override public boolean tryAcquire(String taskName) { return lockGuard.tryAcquire(taskName); }
                @Override public void release(String taskName) {
                    releaseStarted.countDown();
                    lockGuard.release(taskName);
                }
                @Override public boolean isExecutionValid(String taskName) {
                    boolean valid = lockGuard.isExecutionValid(taskName);
                    schedulerOwnershipValid.set(valid);
                    schedulerValidatedOwnership.countDown();
                    return valid;
                }
            };
            DefaultCocoTaskScheduler scheduler = new DefaultCocoTaskScheduler(springScheduler, observingGuard,
                    List.of(events::add), validator, schedulingProperties.getShutdown());
            String taskName = "release-race-" + renewResult.name().toLowerCase();
            scheduler.register(CocoTaskDefinition.builder(taskName, () -> {
                assertThat(await(store.renewEntered)).isTrue();
                assertThat(lockGuard.isExecutionValid(taskName)).isTrue();
                executions.incrementAndGet();
                businessValidatedOwnership.countDown();
            }).fixedRate(Duration.ofSeconds(1)).build());

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<?> execution = executor.submit(springScheduler.latest()::run);
                assertThat(await(businessValidatedOwnership)).isTrue();
                assertThat(await(schedulerValidatedOwnership)).isTrue();
                assertThat(schedulerOwnershipValid.get()).isTrue();
                assertThat(await(releaseStarted)).isTrue();
                assertThat(execution.isDone()).isFalse();

                store.allowRenew.countDown();
                execution.get(2, TimeUnit.SECONDS);
            }
            finally {
                store.allowRenew.countDown();
                executor.shutdownNow();
            }

            scheduler.close();
            assertThat(events).extracting(CocoTaskExecutionEvent::outcome)
                    .containsExactly(CocoTaskExecutionOutcome.STARTED, CocoTaskExecutionOutcome.FAILED);
            assertThat(executions).hasValue(1);
            assertThat(events.get(1).failureType()).isEqualTo(CocoLockException.class.getName());
        }
    }

    private static boolean awaitInvalid(CocoLockTaskExecutionGuard guard, String taskName) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (!guard.isExecutionValid(taskName)) {
                return true;
            }
            Thread.onSpinWait();
        }
        return !guard.isExecutionValid(taskName);
    }

    private static boolean await(CountDownLatch latch) {
        try {
            return latch.await(2, TimeUnit.SECONDS);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static final class ThrowingHandle implements io.github.coco.feature.lock.CocoLockHandle {
        private final AtomicInteger releases;
        private ThrowingHandle(AtomicInteger releases) { this.releases = releases; }
        @Override public io.github.coco.feature.lock.CocoLockLease lease() { throw new UnsupportedOperationException(); }
        @Override public boolean reentrant() { return false; }
        @Override public boolean lost() { return false; }
        @Override public void close() { this.releases.incrementAndGet(); throw new IllegalStateException("release failed"); }
    }

    private static final class LostRenewStore implements CocoLockStore {
        private final CountDownLatch renewEntered = new CountDownLatch(1);
        private final CountDownLatch allowRenew = new CountDownLatch(1);
        private final RenewResult renewResult;

        private LostRenewStore(RenewResult renewResult) {
            this.renewResult = renewResult;
        }

        @Override
        public AcquireResult acquire(io.github.coco.feature.lock.CocoLockLease lease) {
            return AcquireResult.ACQUIRED;
        }

        @Override
        public RenewResult renew(io.github.coco.feature.lock.CocoLockLease lease) {
            this.renewEntered.countDown();
            try {
                if (!this.allowRenew.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("renewal was not released");
                }
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
            return this.renewResult;
        }

        @Override
        public boolean release(io.github.coco.feature.lock.CocoLockLease lease) {
            return true;
        }
    }

    private static final class ReleaseFalseStore implements CocoLockStore {
        private final AtomicInteger released = new AtomicInteger();

        @Override public AcquireResult acquire(io.github.coco.feature.lock.CocoLockLease lease) {
            return AcquireResult.ACQUIRED;
        }

        @Override public RenewResult renew(io.github.coco.feature.lock.CocoLockLease lease) {
            return RenewResult.RENEWED;
        }

        @Override public boolean release(io.github.coco.feature.lock.CocoLockLease lease) {
            this.released.incrementAndGet();
            return false;
        }
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
