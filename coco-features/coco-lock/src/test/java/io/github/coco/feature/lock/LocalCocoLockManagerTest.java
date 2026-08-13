package io.github.coco.feature.lock;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalCocoLockManagerTest {

    @Test
    void serializesSameKeyAndTimesOutWaitingCaller() throws Exception {
        LocalCocoLockManager manager = new LocalCocoLockManager();
        try (CocoLock ignored = manager.tryLock("same", Duration.ZERO, Duration.ofSeconds(1)).orElseThrow()) {
            AtomicReference<Optional<CocoLock>> result = new AtomicReference<>();
            Thread contender = new Thread(() -> result.set(manager.tryLock("same", Duration.ofMillis(80),
                    Duration.ofSeconds(1))));
            contender.start();
            contender.join();
            assertThat(result.get()).isEmpty();
        }
        assertThat(manager.stateCount()).isZero();
    }

    @Test
    void permitsDifferentKeysInParallel() throws Exception {
        LocalCocoLockManager manager = new LocalCocoLockManager();
        CountDownLatch acquired = new CountDownLatch(1);
        try (CocoLock ignored = manager.tryLock("one", Duration.ZERO, Duration.ofSeconds(1)).orElseThrow()) {
            Thread otherKey = new Thread(() -> {
                try (CocoLock second = manager.tryLock("two", Duration.ZERO, Duration.ofSeconds(1)).orElseThrow()) {
                    acquired.countDown();
                }
            });
            otherKey.start();
            assertThat(acquired.await(1, TimeUnit.SECONDS)).isTrue();
            otherKey.join();
        }
    }

    @Test
    void rejectsReentryAndCrossThreadReleaseWithoutReleasingOwnerLock() throws Exception {
        LocalCocoLockManager manager = new LocalCocoLockManager();
        CocoLock lock = manager.tryLock("key", Duration.ZERO, Duration.ofSeconds(1)).orElseThrow();
        assertThatThrownBy(() -> manager.tryLock("key", Duration.ZERO, Duration.ofSeconds(1)))
                .isInstanceOf(CocoLockException.class);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread foreign = new Thread(() -> {
            try {
                lock.close();
            }
            catch (Throwable ex) {
                failure.set(ex);
            }
        });
        foreign.start();
        foreign.join();
        assertThat(failure.get()).isInstanceOf(IllegalStateException.class);
        AtomicReference<Optional<CocoLock>> contenderResult = new AtomicReference<>();
        Thread contender = new Thread(() -> contenderResult.set(manager.tryLock("key", Duration.ZERO,
                Duration.ofSeconds(1))));
        contender.start();
        contender.join();
        assertThat(contenderResult.get()).isEmpty();
        lock.close();
        lock.close();
        try (CocoLock reacquired = manager.tryLock("key", Duration.ZERO, Duration.ofSeconds(1)).orElseThrow()) {
            assertThat(reacquired.key()).isEqualTo("key");
        }
    }

    @Test
    void managerCloseRejectsNewAcquisitionButDoesNotReleaseHeldLock() {
        LocalCocoLockManager manager = new LocalCocoLockManager();
        CocoLock lock = manager.tryLock("key", Duration.ZERO, Duration.ofSeconds(1)).orElseThrow();
        manager.close();
        manager.close();
        assertThatIllegalStateException().isThrownBy(() -> manager.tryLock("other", Duration.ZERO, Duration.ofSeconds(1)));
        lock.close();
        assertThat(manager.stateCount()).isZero();
    }

    @Test
    void reclaimsIdleKeyStateAfterSuccessfulRelease() {
        LocalCocoLockManager manager = new LocalCocoLockManager();
        CocoLock lock = manager.tryLock("reclaimed", Duration.ZERO, Duration.ofSeconds(1)).orElseThrow();
        assertThat(manager.stateCount()).isOne();

        lock.close();

        assertThat(manager.stateCount()).isZero();
        try (CocoLock reacquired = manager.tryLock("reclaimed", Duration.ZERO, Duration.ofSeconds(1)).orElseThrow()) {
            assertThat(manager.stateCount()).isOne();
        }
        assertThat(manager.stateCount()).isZero();
    }
}
