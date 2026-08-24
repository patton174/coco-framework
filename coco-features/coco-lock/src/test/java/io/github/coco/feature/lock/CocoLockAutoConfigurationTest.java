package io.github.coco.feature.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class CocoLockAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, CocoLockAutoConfiguration.class))
            .withUserConfiguration(LockServices.class);

    @Test
    void disabledStateRegistersNothing() {
        this.contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(CocoLockProperties.class);
            assertThat(context).doesNotHaveBean(CocoLockStore.class);
            assertThat(context).doesNotHaveBean(CocoLockManager.class);
            assertThat(context).doesNotHaveBean(CocoLockAspect.class);
        });
    }

    @Test
    void applicationStoreBeanWinsAndSpelAndMethodOverrideResolveExpectedKeys() {
        this.contextRunner.withPropertyValues("coco.lock.enabled=true")
                .withUserConfiguration(CustomStoreConfiguration.class).run(context -> {
                    assertThat(context).getBean(CocoLockStore.class).isInstanceOf(RecordingStore.class);
                    LockService service = context.getBean(LockService.class);
                    service.fromSpel("order-7");
                    service.classLevel();
                    service.methodOverride();
                    RecordingStore store = context.getBean(RecordingStore.class);
                    assertThat(store.seenKeys).containsExactly("order-7", "class-key", "method-key");
                });
    }

    @Test
    void annotationsMutuallyExcludeSameKeyButAllowDifferentKeysAndReleaseAfterFailure() throws Exception {
        this.contextRunner.withPropertyValues("coco.lock.enabled=true", "coco.lock.wait=100ms", "coco.lock.poll-interval=5ms")
                .run(context -> {
                    LockService service = context.getBean(LockService.class);
                    ExecutorService executor = Executors.newFixedThreadPool(2);
                    try {
                        CountDownLatch entered = new CountDownLatch(1);
                        CountDownLatch release = new CountDownLatch(1);
                        Future<String> first = executor.submit(() -> service.blocking("same", entered, release));
                        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                        Future<?> second = executor.submit(() -> service.blocking("same", new CountDownLatch(1), new CountDownLatch(1)));
                        assertThatThrownBy(() -> second.get(2, TimeUnit.SECONDS)).hasCauseInstanceOf(RuntimeException.class);
                        release.countDown();
                        assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("same");

                        CountDownLatch parallel = new CountDownLatch(2);
                        CountDownLatch finish = new CountDownLatch(1);
                        Future<String> left = executor.submit(() -> service.blocking("left", parallel, finish));
                        Future<String> right = executor.submit(() -> service.blocking("right", parallel, finish));
                        assertThat(parallel.await(2, TimeUnit.SECONDS)).isTrue();
                        finish.countDown();
                        assertThat(left.get(2, TimeUnit.SECONDS)).isEqualTo("left");
                        assertThat(right.get(2, TimeUnit.SECONDS)).isEqualTo("right");

                        assertThatThrownBy(service::fails).isInstanceOf(IllegalStateException.class);
                        assertThat(service.afterFailure()).isEqualTo("released");
                    }
                    finally { executor.shutdownNow(); }
                });
    }

    @Test
    void invalidSpelAndAsyncReturnAreRejectedBeforeBusinessCodeRuns() {
        this.contextRunner.withPropertyValues("coco.lock.enabled=true").run(context -> {
            LockService service = context.getBean(LockService.class);
            LockService.ASYNC_BODY_RAN.set(false);
            assertThatThrownBy(service::invalidSpel).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(service::asynchronous).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(service::reactive).isInstanceOf(RuntimeException.class);
            assertThat(LockService.ASYNC_BODY_RAN.get()).isFalse();
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class LockServices {
        @Bean LockService lockService() { return new LockService(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomStoreConfiguration {
        @Bean RecordingStore recordingStore() { return new RecordingStore(); }
    }

    @CocoLock(key = "class-key")
    static class LockService {
        private static final AtomicBoolean ASYNC_BODY_RAN = new AtomicBoolean();
        @CocoLock(key = "#p0")
        String fromSpel(String key) { return key; }
        String classLevel() { return "class"; }
        @CocoLock(key = "method-key")
        String methodOverride() { return "method"; }
        @CocoLock(key = "#p0")
        String blocking(String key, CountDownLatch entered, CountDownLatch release) throws InterruptedException {
            entered.countDown();
            release.await(2, TimeUnit.SECONDS);
            return key;
        }
        @CocoLock(key = "failure")
        String fails() { throw new IllegalStateException("business failure"); }
        @CocoLock(key = "failure")
        String afterFailure() { return "released"; }
        @CocoLock(key = "#missing")
        String invalidSpel() { return "not-reached"; }
        @CocoLock(key = "async")
        CompletableFuture<String> asynchronous() {
            ASYNC_BODY_RAN.set(true);
            return CompletableFuture.completedFuture("not-reached");
        }
        @CocoLock(key = "reactive")
        Flow.Publisher<String> reactive() {
            ASYNC_BODY_RAN.set(true);
            return subscriber -> { };
        }
    }

    static final class RecordingStore implements CocoLockStore {
        private final Map<String, CocoLockLease> entries = new ConcurrentHashMap<>();
        private final java.util.List<String> seenKeys = new java.util.concurrent.CopyOnWriteArrayList<>();
        @Override public AcquireResult acquire(CocoLockLease lease) {
            if (this.entries.putIfAbsent(lease.key(), lease) != null) { return AcquireResult.CONTENDED; }
            this.seenKeys.add(lease.key());
            return AcquireResult.ACQUIRED;
        }
        @Override public RenewResult renew(CocoLockLease lease) {
            return this.entries.replace(lease.key(), this.entries.get(lease.key()), lease)
                    ? RenewResult.RENEWED : RenewResult.NOT_OWNER;
        }
        @Override public boolean release(CocoLockLease lease) {
            return this.entries.remove(lease.key(), lease);
        }
    }
}
