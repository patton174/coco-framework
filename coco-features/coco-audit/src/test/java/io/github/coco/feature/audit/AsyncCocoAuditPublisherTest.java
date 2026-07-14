package io.github.coco.feature.audit;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github.coco.feature.audit.core.CocoAuditEvent;
import io.github.coco.feature.audit.core.CocoAuditFailurePolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsyncCocoAuditPublisherTest {

    @Test
    void exposesShutdownDrainTimeoutWhenConfiguredFailOpen() throws Exception {
        BlockingPublisher delegate = new BlockingPublisher();
        AsyncCocoAuditPublisher publisher = publisher(delegate, CocoAuditFailurePolicy.IGNORE);
        IllegalStateException shutdownFailure;

        try {
            fillWorkerAndQueue(publisher, delegate);

            shutdownFailure = captureShutdownFailure(publisher,
                    "Timed out while draining Coco audit queue; 2 accepted event(s) remain undrained");
        }
        finally {
            delegate.releaseAndAwaitCompletion();
        }

        assertThatThrownBy(publisher::close).isSameAs(shutdownFailure);
    }

    @Test
    void throwsShutdownDrainTimeoutWhenConfiguredFailClosed() throws Exception {
        BlockingPublisher delegate = new BlockingPublisher();
        AsyncCocoAuditPublisher publisher = publisher(delegate, CocoAuditFailurePolicy.THROW);
        IllegalStateException shutdownFailure;

        try {
            fillWorkerAndQueue(publisher, delegate);

            shutdownFailure = captureShutdownFailure(publisher,
                    "Timed out while draining Coco audit queue; 2 accepted event(s) remain undrained");
        }
        finally {
            delegate.releaseAndAwaitCompletion();
        }

        assertThatThrownBy(publisher::close).isSameAs(shutdownFailure);
    }

    @Test
    void exposesInterruptedShutdownAndPreservesTheInterruptStatus() throws Exception {
        BlockingPublisher delegate = new BlockingPublisher();
        AsyncCocoAuditPublisher publisher = new AsyncCocoAuditPublisher(delegate::publish, 1,
                Duration.ofSeconds(5), CocoAuditFailurePolicy.IGNORE);
        AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        AtomicBoolean closeThreadInterrupted = new AtomicBoolean();

        fillWorkerAndQueue(publisher, delegate);
        Thread closer = new Thread(() -> {
            try {
                publisher.close();
            }
            catch (Throwable ex) {
                closeFailure.set(ex);
                closeThreadInterrupted.set(Thread.currentThread().isInterrupted());
            }
        }, "coco-audit-interrupted-close-test");
        closer.start();
        awaitWaiting(closer);
        closer.interrupt();
        closer.join(TimeUnit.SECONDS.toMillis(5));

        try {
            assertThat(closer.isAlive()).isFalse();
            assertThat(closeFailure.get())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Interrupted while draining Coco audit queue; 2 accepted event(s) remain undrained")
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(closeThreadInterrupted).isTrue();
        }
        finally {
            delegate.releaseAndAwaitCompletion();
        }

        assertThatThrownBy(publisher::close).isSameAs(closeFailure.get());
    }

    @Test
    void drainsAcceptedEventsWithoutInterruptingTheRecorder() throws Exception {
        InterruptibleBlockingPublisher delegate = new InterruptibleBlockingPublisher();
        AsyncCocoAuditPublisher publisher = new AsyncCocoAuditPublisher(delegate::publish, 1,
                Duration.ofSeconds(5), CocoAuditFailurePolicy.THROW);

        publisher.publish(event("recording"));
        assertThat(delegate.awaitStarted()).isTrue();
        publisher.publish(event("queued"));

        Thread closer = new Thread(publisher::close, "coco-audit-close-test");
        closer.start();
        awaitWaiting(closer);
        delegate.release();
        closer.join(TimeUnit.SECONDS.toMillis(5));

        assertThat(closer.isAlive()).isFalse();
        assertThat(delegate.wasInterrupted()).isFalse();
        assertThat(delegate.recordedCount()).isEqualTo(2);
    }

    private static AsyncCocoAuditPublisher publisher(BlockingPublisher delegate,
            CocoAuditFailurePolicy failurePolicy) {
        return new AsyncCocoAuditPublisher(delegate::publish, 1, Duration.ofMillis(20), failurePolicy);
    }

    private static void fillWorkerAndQueue(AsyncCocoAuditPublisher publisher, BlockingPublisher delegate)
            throws InterruptedException {
        publisher.publish(event("recording"));
        assertThat(delegate.awaitStarted()).isTrue();
        publisher.publish(event("queued"));
    }

    private static CocoAuditEvent event(String resourceId) {
        return CocoAuditEvent.builder("shutdown-contract").resourceId(resourceId).build();
    }

    private static IllegalStateException captureShutdownFailure(AsyncCocoAuditPublisher publisher,
            String expectedMessage) {
        try {
            publisher.close();
            throw new AssertionError("expected shutdown failure");
        }
        catch (IllegalStateException ex) {
            assertThat(ex).hasMessage(expectedMessage);
            return ex;
        }
    }

    private static void awaitWaiting(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("thread did not start waiting for shutdown drain");
    }

    private static final class BlockingPublisher {

        private final CountDownLatch started = new CountDownLatch(1);

        private final CountDownLatch release = new CountDownLatch(1);

        private final CountDownLatch completed = new CountDownLatch(1);

        void publish(CocoAuditEvent event) {
            this.started.countDown();
            awaitUninterruptibly(this.release);
            this.completed.countDown();
        }

        boolean awaitStarted() throws InterruptedException {
            return this.started.await(5, TimeUnit.SECONDS);
        }

        void releaseAndAwaitCompletion() throws InterruptedException {
            this.release.countDown();
            assertThat(this.completed.await(5, TimeUnit.SECONDS)).isTrue();
        }

        private static void awaitUninterruptibly(CountDownLatch latch) {
            boolean interrupted = false;
            while (true) {
                try {
                    latch.await();
                    break;
                }
                catch (InterruptedException ex) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class InterruptibleBlockingPublisher {

        private final CountDownLatch started = new CountDownLatch(1);

        private final CountDownLatch release = new CountDownLatch(1);

        private volatile boolean interrupted;

        private int recordedCount;

        void publish(CocoAuditEvent event) {
            this.started.countDown();
            try {
                this.release.await();
            }
            catch (InterruptedException ex) {
                this.interrupted = true;
                Thread.currentThread().interrupt();
                return;
            }
            this.recordedCount++;
        }

        boolean awaitStarted() throws InterruptedException {
            return this.started.await(5, TimeUnit.SECONDS);
        }

        void release() {
            this.release.countDown();
        }

        boolean wasInterrupted() {
            return this.interrupted;
        }

        int recordedCount() {
            return this.recordedCount;
        }
    }

}
