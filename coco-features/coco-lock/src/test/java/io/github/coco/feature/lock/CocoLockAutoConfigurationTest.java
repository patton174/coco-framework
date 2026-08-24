package io.github.coco.feature.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class CocoLockAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, CocoLockRedisAutoConfiguration.class,
                    CocoLockAutoConfiguration.class))
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
    void redisStoreIsOptInAndUsesTheConfiguredStoreProperties() {
        this.contextRunner.withPropertyValues("coco.lock.enabled=true", "coco.lock.store-type=redis",
                        "coco.lock.redis.key-prefix=private:lock:")
                .withBean(StringRedisTemplate.class, () -> new StringRedisTemplate(new LettuceConnectionFactory()))
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisCocoLockStore.class);
                    CocoLockProperties properties = context.getBean(CocoLockProperties.class);
                    assertThat(properties.getStoreType()).isEqualTo(CocoLockStoreType.REDIS);
                    assertThat(properties.getRedis().getKeyPrefix()).isEqualTo("private:lock:");
                });
    }

    @Test
    void redisStoreBacksOffForApplicationStoreAndStrictlyResolvesTemplates() {
        this.contextRunner.withPropertyValues("coco.lock.enabled=true", "coco.lock.store-type=redis")
                .withUserConfiguration(CustomStoreConfiguration.class).run(context ->
                        assertThat(context).getBean(CocoLockStore.class).isInstanceOf(RecordingStore.class));
        this.contextRunner.withPropertyValues("coco.lock.enabled=true", "coco.lock.store-type=redis",
                        "coco.lock.redis.template-bean-name=missingTemplate")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining(
                        "coco.lock.redis.template-bean-name"));
        this.contextRunner.withPropertyValues("coco.lock.enabled=true", "coco.lock.store-type=redis")
                .run(context -> assertThat(context.getStartupFailure()).hasStackTraceContaining(
                        "coco.lock.redis.template-bean-name"));
        this.contextRunner.withPropertyValues("coco.lock.enabled=true", "coco.lock.store-type=redis",
                        "coco.lock.redis.template-bean-name=notATemplate")
                .withUserConfiguration(WrongTypeRedisTemplate.class).run(context ->
                        assertThat(context.getStartupFailure()).hasStackTraceContaining(
                                "coco.lock.redis.template-bean-name"));
        this.contextRunner.withPropertyValues("coco.lock.enabled=true", "coco.lock.store-type=redis")
                .withUserConfiguration(NonPrimaryRedisTemplates.class).run(context ->
                        assertThat(context.getStartupFailure()).hasStackTraceContaining(
                                "coco.lock.redis.template-bean-name"));
        this.contextRunner.withPropertyValues("coco.lock.enabled=true", "coco.lock.store-type=redis")
                .withUserConfiguration(PrimaryRedisTemplates.class).run(context ->
                        assertThat(context).hasSingleBean(RedisCocoLockStore.class));
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

    @Test
    void jdkProxyResolvesInterfaceMethodsTypesBridgesAndMethodPriority() {
        this.contextRunner.withPropertyValues("coco.lock.enabled=true", "spring.aop.proxy-target-class=false")
                .withUserConfiguration(CustomStoreConfiguration.class, JdkProxyServices.class).run(context -> {
                    context.getBean(InterfaceMethodLock.class).run();
                    context.getBean(InterfaceTypeLock.class).run();
                    context.getBean(GenericLock.class).convert("value");
                    context.getBean(MethodPriorityLock.class).run();
                    RecordingStore store = context.getBean(RecordingStore.class);
                    assertThat(store.seenKeys).containsExactly("interface-method", "interface-type", "bridge-method",
                            "implementation-method");
                });
    }

    @Test
    void jdkProxyCannotBypassAsyncRejectionFromInterfaceMethod() {
        this.contextRunner.withPropertyValues("coco.lock.enabled=true", "spring.aop.proxy-target-class=false")
                .withUserConfiguration(JdkProxyServices.class).run(context -> {
                    InterfaceAsyncLock.BODY_RAN.set(false);
                    assertThatThrownBy(() -> context.getBean(InterfaceAsyncLock.class).run()).isInstanceOf(RuntimeException.class);
                    assertThat(InterfaceAsyncLock.BODY_RAN.get()).isFalse();
                });
    }

    @Test
    void renewFailureBeforeNormalBusinessReturnFailsClosedWithoutSleep() throws Exception {
        this.contextRunner.withPropertyValues("coco.lock.enabled=true", "coco.lock.lease=100ms",
                        "coco.lock.watchdog-interval=1ms")
                .withUserConfiguration(RenewFailureStoreConfiguration.class, LostReturnServiceConfiguration.class)
                .run(context -> {
                    RenewFailureStore store = context.getBean(RenewFailureStore.class);
                    LostReturnService service = context.getBean(LostReturnService.class);
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    try {
                        CountDownLatch entered = new CountDownLatch(1);
                        CountDownLatch allowBusinessReturn = new CountDownLatch(1);
                        Future<String> result = executor.submit(() -> service.returnNormallyAfterLoss(entered, allowBusinessReturn));
                        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                        assertThat(store.renewEntered.await(2, TimeUnit.SECONDS)).isTrue();
                        store.allowRenew.countDown();
                        allowBusinessReturn.countDown();
                        assertThatThrownBy(() -> result.get(2, TimeUnit.SECONDS)).hasCauseInstanceOf(RuntimeException.class);
                    }
                    finally { executor.shutdownNow(); }
                });
    }

    @Test
    void businessFailureIsPreservedWhenReleaseAlsoFails() {
        this.contextRunner.withPropertyValues("coco.lock.enabled=true")
                .withUserConfiguration(ThrowingReleaseStoreConfiguration.class).run(context -> {
                    assertThatThrownBy(() -> context.getBean(LockService.class).fails())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("business failure")
                            .satisfies(exception -> assertThat(exception.getSuppressed())
                                    .anySatisfy(suppressed -> assertThat(suppressed)
                                            .isInstanceOf(CocoLockException.class)
                                            .hasCauseInstanceOf(IllegalStateException.class)
                                            .hasRootCauseMessage("release failure")));
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

    @Configuration(proxyBeanMethods = false)
    static class JdkProxyServices {
        @Bean InterfaceMethodLock interfaceMethodLock() { return new InterfaceMethodLockImpl(); }
        @Bean InterfaceTypeLock interfaceTypeLock() { return new InterfaceTypeLockImpl(); }
        @Bean GenericLock<String> genericLock() { return new GenericLockImpl(); }
        @Bean MethodPriorityLock methodPriorityLock() { return new MethodPriorityLockImpl(); }
        @Bean InterfaceAsyncLock interfaceAsyncLock() { return new InterfaceAsyncLockImpl(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class RenewFailureStoreConfiguration {
        @Bean RenewFailureStore renewFailureStore() { return new RenewFailureStore(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class ThrowingReleaseStoreConfiguration {
        @Bean CocoLockStore throwingReleaseStore() { return new ThrowingReleaseStore(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class PrimaryRedisTemplates {
        @Bean @Primary StringRedisTemplate primaryTemplate() { return new StringRedisTemplate(new LettuceConnectionFactory()); }
        @Bean StringRedisTemplate secondaryTemplate() { return new StringRedisTemplate(new LettuceConnectionFactory()); }
    }

    @Configuration(proxyBeanMethods = false)
    static class NonPrimaryRedisTemplates {
        @Bean StringRedisTemplate firstTemplate() { return new StringRedisTemplate(new LettuceConnectionFactory()); }
        @Bean StringRedisTemplate secondTemplate() { return new StringRedisTemplate(new LettuceConnectionFactory()); }
    }

    @Configuration(proxyBeanMethods = false)
    static class WrongTypeRedisTemplate {
        @Bean String notATemplate() { return "not a redis template"; }
    }

    @Configuration(proxyBeanMethods = false)
    static class LostReturnServiceConfiguration {
        @Bean LostReturnService lostReturnService(CocoLockManager manager) { return new LostReturnService(manager); }
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

    static final class RenewFailureStore implements CocoLockStore {
        private final CountDownLatch renewEntered = new CountDownLatch(1);
        private final CountDownLatch allowRenew = new CountDownLatch(1);
        @Override public AcquireResult acquire(CocoLockLease lease) { return AcquireResult.ACQUIRED; }
        @Override public RenewResult renew(CocoLockLease lease) {
            this.renewEntered.countDown();
            try {
                if (!this.allowRenew.await(2, TimeUnit.SECONDS)) { throw new AssertionError("renew was not released"); }
            }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
            return RenewResult.NOT_OWNER;
        }
        @Override public boolean release(CocoLockLease lease) { return true; }
    }

    static final class ThrowingReleaseStore implements CocoLockStore {
        @Override public AcquireResult acquire(CocoLockLease lease) { return AcquireResult.ACQUIRED; }
        @Override public RenewResult renew(CocoLockLease lease) { return RenewResult.RENEWED; }
        @Override public boolean release(CocoLockLease lease) { throw new IllegalStateException("release failure"); }
    }

    static class LostReturnService {
        private final CocoLockManager manager;
        LostReturnService(CocoLockManager manager) { this.manager = manager; }
        @CocoLock(key = "lost-return")
        String returnNormallyAfterLoss(CountDownLatch entered, CountDownLatch allowBusinessReturn) throws InterruptedException {
            entered.countDown();
            allowBusinessReturn.await(2, TimeUnit.SECONDS);
            CocoLockRequest request = new CocoLockRequest("lost-return", java.time.Duration.ofMillis(100),
                    java.time.Duration.ZERO, java.time.Duration.ofMillis(1));
            while (true) {
                CocoLockResult nested = this.manager.tryAcquire(request);
                if (!nested.acquired()) { return "business value"; }
                nested.handle().close();
                Thread.onSpinWait();
            }
        }
    }

    interface InterfaceMethodLock {
        @CocoLock(key = "interface-method")
        String run();
    }

    static final class InterfaceMethodLockImpl implements InterfaceMethodLock {
        @Override public String run() { return "method"; }
    }

    @CocoLock(key = "interface-type")
    interface InterfaceTypeLock {
        String run();
    }

    static final class InterfaceTypeLockImpl implements InterfaceTypeLock {
        @Override public String run() { return "type"; }
    }

    interface GenericLock<T> {
        T convert(T value);
    }

    static final class GenericLockImpl implements GenericLock<String> {
        @Override @CocoLock(key = "bridge-method") public String convert(String value) { return value; }
    }

    @CocoLock(key = "interface-priority")
    interface MethodPriorityLock {
        String run();
    }

    static final class MethodPriorityLockImpl implements MethodPriorityLock {
        @Override @CocoLock(key = "implementation-method") public String run() { return "method"; }
    }

    interface InterfaceAsyncLock {
        @CocoLock(key = "interface-async")
        CompletionStage<String> run();
        AtomicBoolean BODY_RAN = new AtomicBoolean();
    }

    static final class InterfaceAsyncLockImpl implements InterfaceAsyncLock {
        @Override public CompletableFuture<String> run() {
            BODY_RAN.set(true);
            return CompletableFuture.completedFuture("not-reached");
        }
    }
}
