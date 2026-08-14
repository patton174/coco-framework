package io.github.coco.feature.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 进程内限流存储测试。
 */
class InMemoryCocoRateLimitStoreTest {

    @Test
    void acquiresAtMostTheConfiguredLimitUnderConcurrency() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T00:00:00Z"));
        CocoRateLimitProperties properties = properties(100, 60);
        CocoRateLimitPermit permit = permit("api", "203.0.113.10", 20, clock.instant().plusSeconds(60));
        try (InMemoryCocoRateLimitStore store = new InMemoryCocoRateLimitStore(properties, clock, false)) {
            ExecutorService executor = Executors.newFixedThreadPool(16);
            try {
                List<Callable<CocoRateLimitDecision>> tasks = new ArrayList<>();
                for (int index = 0; index < 200; index++) {
                    tasks.add(() -> store.acquire(permit));
                }
                List<Future<CocoRateLimitDecision>> results = executor.invokeAll(tasks);
                long allowed = results.stream().filter(result -> get(result).allowed()).count();

                assertThat(allowed).isEqualTo(20);
                assertThat(store.acquire(permit)).isEqualTo(new CocoRateLimitDecision(false, 20, 0,
                        clock.instant().plusSeconds(60), false));
            }
            finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void rejectsConcurrentNewKeysWhenTheActiveEntryCapacityIsFull() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T00:00:00Z"));
        try (InMemoryCocoRateLimitStore store = new InMemoryCocoRateLimitStore(properties(1, 60), clock, false)) {
            ExecutorService executor = Executors.newFixedThreadPool(16);
            CountDownLatch start = new CountDownLatch(1);
            try {
                List<Future<CocoRateLimitDecision>> results = new ArrayList<>();
                for (int index = 0; index < 200; index++) {
                    int key = index;
                    results.add(executor.submit(() -> {
                        start.await();
                        return store.acquire(permit("api", "203.0.113." + key, 1,
                                clock.instant().plusSeconds(60)));
                    }));
                }
                start.countDown();

                assertThat(results.stream().filter(result -> get(result).allowed()).count()).isOne();
                assertThat(results.stream().filter(result -> get(result).capacityExhausted()).count()).isEqualTo(199);
                assertThat(store.size()).isOne();
                assertThat(store.activeEntryCount()).isOne();
            }
            finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void expiresKeysAndReclaimsCapacity() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T00:00:00Z"));
        CocoRateLimitProperties properties = properties(1, 60);
        try (InMemoryCocoRateLimitStore store = new InMemoryCocoRateLimitStore(properties, clock, false)) {
            CocoRateLimitPermit first = permit("api", "203.0.113.10", 1, clock.instant().plusSeconds(5));
            CocoRateLimitPermit second = permit("api", "203.0.113.11", 1, clock.instant().plusSeconds(5));

            assertThat(store.acquire(first).allowed()).isTrue();
            assertThat(store.acquire(second)).isEqualTo(new CocoRateLimitDecision(false, 1, 0,
                    clock.instant().plusSeconds(5), true));

            clock.advanceSeconds(5);
            CocoRateLimitPermit nextWindow = permit("api", "203.0.113.11", 1, clock.instant().plusSeconds(5));
            assertThat(store.acquire(nextWindow).allowed()).isTrue();
            assertThat(store.size()).isEqualTo(1);
        }
    }

    @Test
    void capturesInMemorySettingsAtStoreConstruction() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T00:00:00Z"));
        CocoRateLimitProperties properties = properties(1, 60);
        try (InMemoryCocoRateLimitStore store = new InMemoryCocoRateLimitStore(properties, clock, false)) {
            properties.getInMemory().setMaxEntries(2);
            CocoRateLimitPermit first = permit("api", "203.0.113.10", 1, clock.instant().plusSeconds(60));
            CocoRateLimitPermit second = permit("api", "203.0.113.11", 1, clock.instant().plusSeconds(60));

            assertThat(store.acquire(first).allowed()).isTrue();
            assertThat(store.acquire(second).capacityExhausted()).isTrue();
        }
    }

    @Test
    void isolatesConcurrentCountsAcrossDifferentKeys() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T00:00:00Z"));
        try (InMemoryCocoRateLimitStore store = new InMemoryCocoRateLimitStore(properties(10, 60), clock, false)) {
            ExecutorService executor = Executors.newFixedThreadPool(12);
            try {
                List<Callable<CocoRateLimitDecision>> tasks = new ArrayList<>();
                for (int key = 0; key < 3; key++) {
                    CocoRateLimitPermit permit = permit("api", "203.0.113." + key, 10,
                            clock.instant().plusSeconds(60));
                    for (int request = 0; request < 40; request++) {
                        tasks.add(() -> store.acquire(permit));
                    }
                }
                long allowed = executor.invokeAll(tasks).stream().filter(result -> get(result).allowed()).count();
                assertThat(allowed).isEqualTo(30);
                assertThat(store.size()).isEqualTo(3);
            }
            finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void allowsIndependentKeysWhileAnotherKeyComputationIsInFlight() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T00:00:00Z"));
        BlockingComputeMap entries = new BlockingComputeMap();
        try (InMemoryCocoRateLimitStore store = new InMemoryCocoRateLimitStore(
                properties(2, 60), clock, false, entries)) {
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CocoRateLimitPermit blocked = permit("api", "203.0.113.10", 1,
                    clock.instant().plusSeconds(60));
            CocoRateLimitPermit independent = permit("api", "203.0.113.11", 1,
                    clock.instant().plusSeconds(60));
            entries.blockNext(blocked.key());
            try {
                Future<CocoRateLimitDecision> blockedResult = executor.submit(() -> store.acquire(blocked));
                assertThat(entries.awaitComputation()).isTrue();

                Future<CocoRateLimitDecision> independentResult = executor.submit(() -> store.acquire(independent));
                assertThat(independentResult.get(1, TimeUnit.SECONDS).allowed()).isTrue();

                entries.releaseComputation();
                assertThat(blockedResult.get(1, TimeUnit.SECONDS).allowed()).isTrue();
                assertThat(store.size()).isEqualTo(2);
                assertThat(store.activeEntryCount()).isEqualTo(2);
            }
            finally {
                entries.releaseComputation();
                executor.shutdownNow();
            }
        }
    }

    @Test
    void reusesTheSameKeyAfterItsWindowExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T00:00:00Z"));
        try (InMemoryCocoRateLimitStore store = new InMemoryCocoRateLimitStore(properties(2, 60), clock, false)) {
            CocoRateLimitPermit first = permit("api", "203.0.113.10", 1, clock.instant().plusSeconds(2));
            assertThat(store.acquire(first).allowed()).isTrue();
            assertThat(store.acquire(first).allowed()).isFalse();

            clock.advanceSeconds(2);
            CocoRateLimitPermit next = permit("api", "203.0.113.10", 1, clock.instant().plusSeconds(2));
            assertThat(store.acquire(next).allowed()).isTrue();
        }
    }

    @Test
    void rejectsAWindowThatHasAlreadyExpiredAtTheInjectedClock() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T00:00:00Z"));
        try (InMemoryCocoRateLimitStore store = new InMemoryCocoRateLimitStore(properties(2, 60), clock, false)) {
            CocoRateLimitDecision decision = store.acquire(
                    permit("api", "203.0.113.10", 2, clock.instant()));
            assertThat(decision.allowed()).isFalse();
            assertThat(decision.capacityExhausted()).isTrue();
            assertThat(store.size()).isZero();
        }
    }

    @Test
    void failsClosedAfterTheStoreIsClosed() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T00:00:00Z"));
        InMemoryCocoRateLimitStore store = new InMemoryCocoRateLimitStore(properties(2, 60), clock, false);
        store.close();

        CocoRateLimitDecision decision = store.acquire(
                permit("api", "203.0.113.10", 2, clock.instant().plusSeconds(60)));
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.capacityExhausted()).isTrue();
    }

    @Test
    void closeWaitsForAnAdmittedComputeBeforeClearingLifecycleState() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T00:00:00Z"));
        BlockingComputeMap entries = new BlockingComputeMap();
        InMemoryCocoRateLimitStore store = new InMemoryCocoRateLimitStore(
                properties(2, 60), clock, false, entries);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CocoRateLimitPermit permit = permit("api", "203.0.113.10", 1, clock.instant().plusSeconds(60));
        entries.blockNext(permit.key());
        try {
            Future<CocoRateLimitDecision> acquisition = executor.submit(() -> store.acquire(permit));
            assertThat(entries.awaitComputation()).isTrue();

            Future<?> close = executor.submit(store::close);
            assertThat(awaitTrue(store::isClosed)).isTrue();
            assertThat(close.isDone()).isFalse();

            entries.releaseComputation();
            assertThat(acquisition.get(1, TimeUnit.SECONDS).allowed()).isTrue();
            close.get(1, TimeUnit.SECONDS);

            assertThat(store.size()).isZero();
            assertThat(store.activeEntryCount()).isZero();
            assertThat(store.acquire(permit).allowed()).isFalse();
        }
        finally {
            entries.releaseComputation();
            executor.shutdownNow();
            store.close();
        }
    }

    @Test
    void repeatedCloseRacesCannotResurrectEntriesOrAllowNewRequests() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T00:00:00Z"));
        for (int attempt = 0; attempt < 20; attempt++) {
            InMemoryCocoRateLimitStore store = new InMemoryCocoRateLimitStore(
                    properties(10_000, 60), clock, false);
            ExecutorService executor = Executors.newFixedThreadPool(16);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch firstAllowed = new CountDownLatch(1);
            AtomicBoolean closeReturned = new AtomicBoolean();
            AtomicInteger sequence = new AtomicInteger();
            AtomicInteger postCloseAllowed = new AtomicInteger();
            try {
                List<Future<?>> workers = new ArrayList<>();
                for (int worker = 0; worker < 16; worker++) {
                    workers.add(executor.submit(() -> {
                        start.await();
                        for (int request = 0; request < 500; request++) {
                            boolean startedAfterClose = closeReturned.get();
                            int key = sequence.getAndIncrement();
                            CocoRateLimitDecision decision = store.acquire(permit("api", "stress-" + key, 1,
                                    clock.instant().plusSeconds(60)));
                            if (decision.allowed()) {
                                firstAllowed.countDown();
                                if (startedAfterClose) {
                                    postCloseAllowed.incrementAndGet();
                                }
                            }
                        }
                        return null;
                    }));
                }
                start.countDown();
                assertThat(firstAllowed.await(1, TimeUnit.SECONDS)).isTrue();

                store.close();
                closeReturned.set(true);
                for (Future<?> worker : workers) {
                    worker.get(5, TimeUnit.SECONDS);
                }

                assertThat(postCloseAllowed).hasValue(0);
                assertThat(store.size()).isZero();
                assertThat(store.activeEntryCount()).isZero();
                assertThat(store.acquire(permit("api", "after-close", 1,
                        clock.instant().plusSeconds(60))).allowed()).isFalse();
            }
            finally {
                store.close();
                executor.shutdownNow();
            }
        }
    }

    @Test
    void closeInterruptsRunningCleanupAndWaitsForTermination() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T00:00:00Z"));
        InMemoryCocoRateLimitStore store = new InMemoryCocoRateLimitStore(properties(2, 60), clock, true);
        ScheduledExecutorService cleanupExecutor = cleanupExecutor(store);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch mutated = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicInteger mutations = new AtomicInteger();
        cleanupExecutor.execute(() -> {
            started.countDown();
            try {
                while (true) {
                    TimeUnit.MILLISECONDS.sleep(10);
                    mutations.incrementAndGet();
                    mutated.countDown();
                }
            }
            catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        });
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(mutated.await(1, TimeUnit.SECONDS)).isTrue();

        store.close();

        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(cleanupExecutor.isShutdown()).isTrue();
        assertThat(cleanupExecutor.isTerminated()).isTrue();
        int mutationsAfterClose = mutations.get();
        TimeUnit.MILLISECONDS.sleep(100);
        assertThat(mutations).hasValue(mutationsAfterClose);
    }

    @Test
    void reportsRemainingQuotaForEachSuccessfulAcquire() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T00:00:00Z"));
        try (InMemoryCocoRateLimitStore store = new InMemoryCocoRateLimitStore(properties(2, 60), clock, false)) {
            CocoRateLimitPermit permit = permit("api", "203.0.113.10", 2, clock.instant().plusSeconds(60));
            assertThat(store.acquire(permit).remaining()).isEqualTo(1);
            assertThat(store.acquire(permit).remaining()).isZero();
            assertThat(store.acquire(permit).remaining()).isZero();
        }
    }

    @Test
    void logsTheProcessLocalStorageWarningOnlyOnce() {
        InMemoryCocoRateLimitStore.resetClusterWarningForTests();
        Logger logger = (Logger) LoggerFactory.getLogger(InMemoryCocoRateLimitStore.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            CocoRateLimitProperties properties = properties(2, 60);
            try (InMemoryCocoRateLimitStore first = new InMemoryCocoRateLimitStore(properties,
                    Clock.systemUTC(), false);
                    InMemoryCocoRateLimitStore second = new InMemoryCocoRateLimitStore(properties,
                            Clock.systemUTC(), false)) {
                assertThat(first.size()).isZero();
                assertThat(second.size()).isZero();
            }
            long warnings = appender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .filter(event -> event.getFormattedMessage().contains("process-local storage"))
                    .count();
            assertThat(warnings).isOne();
        }
        finally {
            logger.detachAppender(appender);
            appender.stop();
            InMemoryCocoRateLimitStore.resetClusterWarningForTests();
        }
    }

    private static CocoRateLimitProperties properties(int maxEntries, int cleanupIntervalSeconds) {
        CocoRateLimitProperties properties = new CocoRateLimitProperties();
        CocoRateLimitProperties.InMemory inMemory = new CocoRateLimitProperties.InMemory();
        inMemory.setMaxEntries(maxEntries);
        inMemory.setCleanupIntervalSeconds(cleanupIntervalSeconds);
        properties.setInMemory(inMemory);
        return properties;
    }

    private static CocoRateLimitPermit permit(String route, String subject, long limit, Instant resetAt) {
        return new CocoRateLimitPermit(new CocoRateLimitKey(route, subject), limit, resetAt);
    }

    private static CocoRateLimitDecision get(Future<CocoRateLimitDecision> result) {
        try {
            return result.get();
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static ScheduledExecutorService cleanupExecutor(InMemoryCocoRateLimitStore store)
            throws ReflectiveOperationException {
        Field cleanupExecutor = InMemoryCocoRateLimitStore.class.getDeclaredField("cleanupExecutor");
        cleanupExecutor.setAccessible(true);
        return (ScheduledExecutorService) cleanupExecutor.get(store);
    }

    private static boolean awaitTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(1);
        }
        return condition.getAsBoolean();
    }

    private static final class BlockingComputeMap
            extends ConcurrentHashMap<CocoRateLimitKey, InMemoryCocoRateLimitStore.Bucket> {

        private final AtomicReference<CocoRateLimitKey> blockedKey = new AtomicReference<>();

        private final CountDownLatch computationReady = new CountDownLatch(1);

        private final CountDownLatch releaseComputation = new CountDownLatch(1);

        private void blockNext(CocoRateLimitKey key) {
            this.blockedKey.set(key);
        }

        private boolean awaitComputation() throws InterruptedException {
            return this.computationReady.await(1, TimeUnit.SECONDS);
        }

        private void releaseComputation() {
            this.releaseComputation.countDown();
        }

        @Override
        public InMemoryCocoRateLimitStore.Bucket compute(CocoRateLimitKey key,
                BiFunction<? super CocoRateLimitKey, ? super InMemoryCocoRateLimitStore.Bucket,
                        ? extends InMemoryCocoRateLimitStore.Bucket> remappingFunction) {
            if (!key.equals(this.blockedKey.getAndSet(null))) {
                return super.compute(key, remappingFunction);
            }
            return super.compute(key, (computedKey, bucket) -> {
                InMemoryCocoRateLimitStore.Bucket computed = remappingFunction.apply(computedKey, bucket);
                this.computationReady.countDown();
                try {
                    this.releaseComputation.await();
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while blocking rate-limit computation", exception);
                }
                return computed;
            });
        }
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;

        private MutableClock(Instant instant) {
            this.instant = new AtomicReference<>(instant);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
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
