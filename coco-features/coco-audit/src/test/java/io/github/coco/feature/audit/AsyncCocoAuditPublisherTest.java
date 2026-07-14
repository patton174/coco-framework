package io.github.coco.feature.audit;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.github.coco.feature.audit.core.CocoAuditEvent;
import io.github.coco.feature.audit.core.CocoAuditFailurePolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsyncCocoAuditPublisherTest {

    @Test
    void ignoresShutdownDrainTimeoutWhenConfiguredFailOpen() throws Exception {
        BlockingPublisher delegate = new BlockingPublisher();
        AsyncCocoAuditPublisher publisher = publisher(delegate, CocoAuditFailurePolicy.IGNORE);

        try {
            fillWorkerAndQueue(publisher, delegate);

            assertThatCode(publisher::close).doesNotThrowAnyException();
        }
        finally {
            delegate.releaseAndAwaitCompletion();
            publisher.close();
        }

        assertThat(delegate.recordedCount()).isEqualTo(1);
    }

    @Test
    void throwsShutdownDrainTimeoutWhenConfiguredFailClosed() throws Exception {
        BlockingPublisher delegate = new BlockingPublisher();
        AsyncCocoAuditPublisher publisher = publisher(delegate, CocoAuditFailurePolicy.THROW);

        try {
            fillWorkerAndQueue(publisher, delegate);

            assertThatThrownBy(publisher::close)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Timed out while draining Coco audit queue");
        }
        finally {
            delegate.releaseAndAwaitCompletion();
            publisher.close();
        }

        assertThat(delegate.recordedCount()).isEqualTo(1);
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

    private static final class BlockingPublisher {

        private final CountDownLatch started = new CountDownLatch(1);

        private final CountDownLatch release = new CountDownLatch(1);

        private final CountDownLatch completed = new CountDownLatch(1);

        private int recordedCount;

        void publish(CocoAuditEvent event) {
            this.started.countDown();
            awaitUninterruptibly(this.release);
            this.recordedCount++;
            this.completed.countDown();
        }

        boolean awaitStarted() throws InterruptedException {
            return this.started.await(5, TimeUnit.SECONDS);
        }

        void releaseAndAwaitCompletion() throws InterruptedException {
            this.release.countDown();
            assertThat(this.completed.await(5, TimeUnit.SECONDS)).isTrue();
        }

        int recordedCount() {
            return this.recordedCount;
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
