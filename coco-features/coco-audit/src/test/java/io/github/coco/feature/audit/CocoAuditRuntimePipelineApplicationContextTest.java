package io.github.coco.feature.audit;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.feature.audit.accesslog.CocoAccessLogAuditRecorder;
import io.github.coco.feature.audit.core.CocoAuditErrorHandler;
import io.github.coco.feature.audit.core.CocoAuditEvent;
import io.github.coco.feature.audit.core.CocoAuditPublisher;
import io.github.coco.feature.audit.core.CocoAuditRecorder;
import io.github.coco.logging.access.CocoAccessLog;
import io.github.coco.logging.access.CocoAccessLogRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CocoAuditRuntimePipelineApplicationContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoCommonAutoConfiguration.class,
                    CocoAuditAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages",
                    "coco.audit.logging.enabled=false");

    @Test
    void recordsSynchronouslyByDefault() {
        AtomicReference<Thread> recordingThread = new AtomicReference<>();
        Thread publishingThread = Thread.currentThread();

        this.contextRunner
                .withBean(CocoAuditRecorder.class, () -> event -> recordingThread.set(Thread.currentThread()))
                .run(context -> context.getBean(CocoAuditPublisher.class)
                        .publish(CocoAuditEvent.builder("synchronous").build()));

        assertThat(recordingThread).hasValue(publishingThread);
    }

    @Test
    void recordsAsynchronouslyAndDrainsQueuedEventsOnShutdown() {
        int eventCount = 200;
        List<String> recordedIds = new CopyOnWriteArrayList<>();
        Set<String> recordingThreads = java.util.concurrent.ConcurrentHashMap.newKeySet();
        String publishingThread = Thread.currentThread().getName();
        CountDownLatch firstRecordStarted = new CountDownLatch(1);
        CountDownLatch releaseRecorder = new CountDownLatch(1);
        ScheduledExecutorService releaser = Executors.newSingleThreadScheduledExecutor();

        try {
            this.contextRunner
                    .withBean(CocoAuditRecorder.class, () -> event -> {
                        recordingThreads.add(Thread.currentThread().getName());
                        if (firstRecordStarted.getCount() > 0) {
                            firstRecordStarted.countDown();
                            await(releaseRecorder);
                        }
                        recordedIds.add(event.resourceId().orElseThrow());
                    })
                    .withPropertyValues("coco.audit.async.enabled=true",
                            "coco.audit.async.queue-capacity=256",
                            "coco.audit.async.shutdown-timeout=5s")
                    .run(context -> {
                        CocoAuditPublisher publisher = context.getBean(CocoAuditPublisher.class);
                        IntStream.range(0, eventCount).forEach(index -> publisher.publish(CocoAuditEvent
                                .builder("asynchronous")
                                .resourceId(Integer.toString(index))
                                .build()));
                        assertThat(firstRecordStarted.await(5, TimeUnit.SECONDS)).isTrue();
                        releaser.schedule(releaseRecorder::countDown, 100, TimeUnit.MILLISECONDS);
                    });
        }
        finally {
            releaseRecorder.countDown();
            releaser.shutdownNow();
        }

        assertThat(recordedIds).hasSize(eventCount);
        assertThat(recordingThreads).isNotEmpty().doesNotContain(publishingThread);
    }

    @Test
    void recordsConcurrentPublishersWithoutLoss() throws Exception {
        int eventCount = 800;
        List<String> recordedIds = new CopyOnWriteArrayList<>();
        ExecutorService publishers = Executors.newFixedThreadPool(8);
        try {
            this.contextRunner
                    .withBean(CocoAuditRecorder.class,
                            () -> event -> recordedIds.add(event.resourceId().orElseThrow()))
                    .withPropertyValues("coco.audit.async.enabled=true",
                            "coco.audit.async.queue-capacity=1024",
                            "coco.audit.async.shutdown-timeout=5s")
                    .run(context -> {
                        CocoAuditPublisher publisher = context.getBean(CocoAuditPublisher.class);
                        List<? extends Future<?>> submissions = IntStream.range(0, eventCount)
                                .mapToObj(index -> publishers.submit(() -> publisher.publish(CocoAuditEvent
                                        .builder("concurrent")
                                        .resourceId(Integer.toString(index))
                                        .build())))
                                .toList();
                        for (Future<?> submission : submissions) {
                            submission.get(5, TimeUnit.SECONDS);
                        }
                    });
        }
        finally {
            publishers.shutdownNow();
        }

        assertThat(recordedIds).hasSize(eventCount).doesNotHaveDuplicates();
    }

    @Test
    void dropsQueueOverflowWhenConfiguredFailOpen() {
        List<String> recordedIds = new CopyOnWriteArrayList<>();
        CountDownLatch firstRecordStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstRecord = new CountDownLatch(1);

        this.contextRunner
                .withBean(CocoAuditRecorder.class, () -> event -> {
                    if ("first".equals(event.resourceId().orElse(null))) {
                        firstRecordStarted.countDown();
                        await(releaseFirstRecord);
                    }
                    recordedIds.add(event.resourceId().orElseThrow());
                })
                .withPropertyValues("coco.audit.async.enabled=true",
                        "coco.audit.async.queue-capacity=1",
                        "coco.audit.failure-policy=ignore")
                .run(context -> {
                    CocoAuditPublisher publisher = context.getBean(CocoAuditPublisher.class);
                    try {
                        publisher.publish(event("first"));
                        assertThat(firstRecordStarted.await(5, TimeUnit.SECONDS)).isTrue();
                        publisher.publish(event("second"));
                        publisher.publish(event("dropped"));
                    }
                    finally {
                        releaseFirstRecord.countDown();
                    }
                });

        assertThat(recordedIds).containsExactly("first", "second");
    }

    @Test
    void throwsQueueOverflowWhenConfiguredFailClosed() {
        List<String> recordedIds = new CopyOnWriteArrayList<>();
        CountDownLatch firstRecordStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstRecord = new CountDownLatch(1);

        this.contextRunner
                .withBean(CocoAuditRecorder.class, () -> event -> {
                    if ("first".equals(event.resourceId().orElse(null))) {
                        firstRecordStarted.countDown();
                        await(releaseFirstRecord);
                    }
                    recordedIds.add(event.resourceId().orElseThrow());
                })
                .withPropertyValues("coco.audit.async.enabled=true",
                        "coco.audit.async.queue-capacity=1",
                        "coco.audit.failure-policy=throw")
                .run(context -> {
                    CocoAuditPublisher publisher = context.getBean(CocoAuditPublisher.class);
                    try {
                        publisher.publish(event("first"));
                        assertThat(firstRecordStarted.await(5, TimeUnit.SECONDS)).isTrue();
                        publisher.publish(event("second"));
                        assertThatThrownBy(() -> publisher.publish(event("rejected")))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessage("Coco audit queue is full");
                    }
                    finally {
                        releaseFirstRecord.countDown();
                    }
                });

        assertThat(recordedIds).containsExactly("first", "second");
    }

    @Test
    void continuesAfterAsyncRecorderFailureWhenConfiguredFailOpen() {
        AtomicInteger successfulRecords = new AtomicInteger();
        int eventCount = 100;

        this.contextRunner
                .withBean("failingAuditRecorder", CocoAuditRecorder.class,
                        () -> event -> {
                            throw new IllegalStateException("recorder failed");
                        })
                .withBean("successfulAuditRecorder", CocoAuditRecorder.class,
                        () -> event -> successfulRecords.incrementAndGet())
                .withPropertyValues("coco.audit.async.enabled=true",
                        "coco.audit.async.queue-capacity=128",
                        "coco.audit.failure-policy=ignore")
                .run(context -> {
                    CocoAuditPublisher publisher = context.getBean(CocoAuditPublisher.class);
                    IntStream.range(0, eventCount)
                            .forEach(index -> publisher.publish(event(Integer.toString(index))));
                });

        assertThat(successfulRecords).hasValue(eventCount);
    }

    @Test
    void rejectsNewEventsAfterAsyncRecorderFailureWhenConfiguredFailClosed() throws Exception {
        IllegalStateException recorderFailure = new IllegalStateException("recorder failed");
        CountDownLatch recorderInvoked = new CountDownLatch(1);
        AtomicReference<RuntimeException> observedFailure = new AtomicReference<>();

        this.contextRunner
                .withBean(CocoAuditRecorder.class, () -> event -> {
                    recorderInvoked.countDown();
                    throw recorderFailure;
                })
                .withPropertyValues("coco.audit.async.enabled=true",
                        "coco.audit.async.queue-capacity=8",
                        "coco.audit.failure-policy=throw")
                .run(context -> {
                    CocoAuditPublisher publisher = context.getBean(CocoAuditPublisher.class);
                    publisher.publish(event("first"));
                    assertThat(recorderInvoked.await(5, TimeUnit.SECONDS)).isTrue();
                    observedFailure.set(awaitRecorderFailure(publisher, recorderFailure));
                });

        assertThat(observedFailure).hasValue(recorderFailure);
    }

    @Test
    void preservesCustomErrorHandlerFailureInAsyncMode() throws Exception {
        IllegalStateException handlerFailure = new IllegalStateException("custom handler failed");
        CountDownLatch handlerInvoked = new CountDownLatch(1);
        AtomicReference<RuntimeException> observedFailure = new AtomicReference<>();

        this.contextRunner
                .withBean(CocoAuditRecorder.class, () -> event -> {
                    throw new IllegalStateException("recorder failed");
                })
                .withBean(CocoAuditErrorHandler.class, () -> (event, recorder, failure) -> {
                    handlerInvoked.countDown();
                    throw handlerFailure;
                })
                .withPropertyValues("coco.audit.async.enabled=true",
                        "coco.audit.async.queue-capacity=8",
                        "coco.audit.failure-policy=ignore")
                .run(context -> {
                    CocoAuditPublisher publisher = context.getBean(CocoAuditPublisher.class);
                    publisher.publish(event("first"));
                    assertThat(handlerInvoked.await(5, TimeUnit.SECONDS)).isTrue();
                    observedFailure.set(awaitRecorderFailure(publisher, handlerFailure));
                });

        assertThat(observedFailure).hasValue(handlerFailure);
    }

    @Test
    void appliesConfiguredFailOpenPolicyToRecorderFailures() {
        AtomicInteger successfulRecords = new AtomicInteger();

        this.contextRunner
                .withBean("failingAuditRecorder", CocoAuditRecorder.class,
                        () -> event -> {
                            throw new IllegalStateException("recorder failed");
                        })
                .withBean("successfulAuditRecorder", CocoAuditRecorder.class,
                        () -> event -> successfulRecords.incrementAndGet())
                .withPropertyValues("coco.audit.failure-policy=ignore")
                .run(context -> context.getBean(CocoAuditPublisher.class)
                        .publish(CocoAuditEvent.builder("fail-open").build()));

        assertThat(successfulRecords).hasValue(1);
    }

    @Test
    void appliesConfiguredFailClosedPolicyToRecorderFailures() {
        AtomicInteger subsequentRecords = new AtomicInteger();

        this.contextRunner
                .withBean("failingAuditRecorder", CocoAuditRecorder.class,
                        () -> event -> {
                            throw new IllegalStateException("recorder failed");
                        })
                .withBean("subsequentAuditRecorder", CocoAuditRecorder.class,
                        () -> event -> subsequentRecords.incrementAndGet())
                .withPropertyValues("coco.audit.failure-policy=throw")
                .run(context -> assertThatThrownBy(() -> context.getBean(CocoAuditPublisher.class)
                        .publish(CocoAuditEvent.builder("fail-closed").build()))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("recorder failed"));

        assertThat(subsequentRecords).hasValue(0);
    }

    @Test
    void customPublisherOverridesDefaultAndReceivesAccessLogEvents() {
        AtomicReference<CocoAuditEvent> latest = new AtomicReference<>();
        CocoAuditPublisher customPublisher = latest::set;

        this.contextRunner
                .withBean("customAuditPublisher", CocoAuditPublisher.class, () -> customPublisher)
                .withPropertyValues("coco.audit.async.enabled=true")
                .run(context -> {
                    assertThat(context.getBeansOfType(CocoAuditPublisher.class))
                            .containsOnlyKeys("customAuditPublisher");
                    CocoAccessLogRecorder accessLogRecorder = context.getBean("cocoAccessLogAuditRecorder",
                            CocoAccessLogRecorder.class);
                    accessLogRecorder.record(CocoAccessLog.of("trace-custom", "get", "/custom", 200, 4L,
                            true, null, "127.0.0.1", "JUnit", null, null));
                });

        assertThat(latest.get()).isNotNull();
        assertThat(latest.get().type()).isEqualTo(CocoAccessLogAuditRecorder.EVENT_TYPE);
        assertThat(latest.get().traceId()).contains("trace-custom");
    }

    @Test
    void convertsAccessLogAndDrainsItThroughAsyncRecorder() {
        List<CocoAuditEvent> events = new CopyOnWriteArrayList<>();

        this.contextRunner
                .withBean(CocoAuditRecorder.class, () -> events::add)
                .withPropertyValues("coco.audit.async.enabled=true",
                        "coco.audit.async.queue-capacity=8")
                .run(context -> context.getBean("cocoAccessLogAuditRecorder", CocoAccessLogRecorder.class)
                        .record(CocoAccessLog.of("trace-async-access", "post", "/orders", 202, 8L,
                                true, null, "127.0.0.1", "JUnit", null, null)));

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(CocoAccessLogAuditRecorder.EVENT_TYPE);
            assertThat(event.traceId()).contains("trace-async-access");
            assertThat(event.resourceId()).contains("/orders");
            assertThat(event.attributes()).containsEntry("status", 202);
        });
    }

    @Test
    void doesNotRecursivelyHandleRecorderFailures() {
        AtomicReference<CocoAuditPublisher> publisherReference = new AtomicReference<>();
        AtomicInteger handlerInvocations = new AtomicInteger();
        AtomicInteger successfulRecords = new AtomicInteger();
        CountDownLatch nestedPublishCompleted = new CountDownLatch(1);
        CocoAuditErrorHandler republishingHandler = (event, recorder, failure) -> {
            if (handlerInvocations.incrementAndGet() == 1) {
                publisherReference.get().publish(CocoAuditEvent.builder("failure-diagnostic").build());
                nestedPublishCompleted.countDown();
            }
        };

        this.contextRunner
                .withBean("failingAuditRecorder", CocoAuditRecorder.class,
                        () -> event -> {
                            throw new IllegalStateException("recorder failed");
                        })
                .withBean("successfulAuditRecorder", CocoAuditRecorder.class,
                        () -> event -> successfulRecords.incrementAndGet())
                .withBean(CocoAuditErrorHandler.class, () -> republishingHandler)
                .run(context -> {
                    CocoAuditPublisher publisher = context.getBean(CocoAuditPublisher.class);
                    publisherReference.set(publisher);
                    publisher.publish(CocoAuditEvent.builder("business-event").build());
                });

        assertThat(handlerInvocations).hasValue(1);
        assertThat(successfulRecords).hasValue(2);
        assertThat(nestedPublishCompleted.getCount()).isZero();
    }

    @Test
    void doesNotRecursivelyHandleRecorderFailuresInAsyncMode() {
        AtomicReference<CocoAuditPublisher> publisherReference = new AtomicReference<>();
        AtomicInteger handlerInvocations = new AtomicInteger();
        AtomicInteger successfulRecords = new AtomicInteger();
        CountDownLatch nestedPublishCompleted = new CountDownLatch(1);
        CocoAuditErrorHandler republishingHandler = (event, recorder, failure) -> {
            if (handlerInvocations.incrementAndGet() == 1) {
                publisherReference.get().publish(CocoAuditEvent.builder("failure-diagnostic").build());
                nestedPublishCompleted.countDown();
            }
        };

        this.contextRunner
                .withBean("failingAuditRecorder", CocoAuditRecorder.class,
                        () -> event -> {
                            throw new IllegalStateException("recorder failed");
                        })
                .withBean("successfulAuditRecorder", CocoAuditRecorder.class,
                        () -> event -> successfulRecords.incrementAndGet())
                .withBean(CocoAuditErrorHandler.class, () -> republishingHandler)
                .withPropertyValues("coco.audit.async.enabled=true",
                        "coco.audit.async.queue-capacity=8")
                .run(context -> {
                    CocoAuditPublisher publisher = context.getBean(CocoAuditPublisher.class);
                    publisherReference.set(publisher);
                    publisher.publish(CocoAuditEvent.builder("business-event").build());
                    assertThat(nestedPublishCompleted.await(5, TimeUnit.SECONDS)).isTrue();
                });

        assertThat(handlerInvocations).hasValue(1);
        assertThat(successfulRecords).hasValue(2);
    }

    private static CocoAuditEvent event(String resourceId) {
        return CocoAuditEvent.builder("runtime-contract").resourceId(resourceId).build();
    }

    private static RuntimeException awaitRecorderFailure(CocoAuditPublisher publisher,
            RuntimeException expectedFailure) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            try {
                publisher.publish(event("probe"));
            }
            catch (RuntimeException ex) {
                if (ex == expectedFailure) {
                    return ex;
                }
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("async recorder failure was not exposed to subsequent publishers");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for audit test fixture");
            }
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for audit test fixture", ex);
        }
    }

}
