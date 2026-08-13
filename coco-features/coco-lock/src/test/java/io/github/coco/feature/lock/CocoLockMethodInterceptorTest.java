package io.github.coco.feature.lock;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CocoLockMethodInterceptorTest {

    @Test
    void resolvesSpelKeyAndReleasesAfterBusinessException() throws Throwable {
        RecordingManager manager = new RecordingManager(true);
        CocoLockMethodInterceptor interceptor = new CocoLockMethodInterceptor(manager, properties());
        Method method = Service.class.getMethod("fail", String.class);
        assertThatThrownBy(() -> invoke(interceptor, method, new Service(), new Object[] {"42"},
                () -> { throw new IllegalArgumentException("business"); }))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("business");
        assertThat(manager.key).isEqualTo("order:42");
        assertThat(manager.closed.get()).isTrue();
    }

    @Test
    void failsWhenLockIsNotAcquiredAndRejectsAsyncReturn() throws Throwable {
        Method method = Service.class.getMethod("fail", String.class);
        CocoLockMethodInterceptor unavailable = new CocoLockMethodInterceptor(new RecordingManager(false), properties());
        assertThatThrownBy(() -> invoke(unavailable, method, new Service(), new Object[] {"42"}, () -> "ok"))
                .isInstanceOf(CocoLockAcquisitionException.class);
        Method async = Service.class.getMethod("async", String.class);
        assertThatThrownBy(() -> invoke(new CocoLockMethodInterceptor(new RecordingManager(true), properties()), async,
                new Service(), new Object[] {"42"}, () -> CompletableFuture.completedFuture("ok")))
                .isInstanceOf(CocoLockException.class).hasMessageContaining("asynchronous");
    }

    private static Object invoke(CocoLockMethodInterceptor interceptor, Method method, Object target, Object[] arguments,
            Proceeding proceeding) throws Throwable {
        return interceptor.invoke(new MethodInvocation() {
            @Override public Method getMethod() { return method; }
            @Override public Object[] getArguments() { return arguments; }
            @Override public Object proceed() throws Throwable { return proceeding.proceed(); }
            @Override public Object getThis() { return target; }
            @Override public java.lang.reflect.AccessibleObject getStaticPart() { return method; }
        });
    }

    private static CocoLockProperties properties() {
        CocoLockProperties properties = new CocoLockProperties();
        properties.setDefaultWait(Duration.ZERO);
        properties.setDefaultLease(Duration.ofSeconds(1));
        return properties;
    }

    interface Proceeding { Object proceed() throws Throwable; }

    static final class Service {
        @CocoLocked("order:#p0")
        public String fail(String id) { return id; }

        @CocoLocked("order:#p0")
        public CompletableFuture<String> async(String id) { return CompletableFuture.completedFuture(id); }
    }

    static final class RecordingManager implements CocoLockManager {
        private final boolean acquire;
        private String key;
        private final AtomicBoolean closed = new AtomicBoolean();

        RecordingManager(boolean acquire) { this.acquire = acquire; }

        @Override public Optional<CocoLock> tryLock(String key, Duration waitTime, Duration leaseTime) {
            this.key = key;
            if (!this.acquire) { return Optional.empty(); }
            return Optional.of(new CocoLock() {
                @Override public String key() { return key; }
                @Override public java.time.Instant acquiredAt() { return java.time.Instant.EPOCH; }
                @Override public java.time.Instant expiresAt() { return java.time.Instant.EPOCH; }
                @Override public void close() { closed.set(true); }
            });
        }

        @Override public void close() { }
    }
}
