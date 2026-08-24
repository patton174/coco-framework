package io.github.coco.messaging.internal;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.messaging.CocoMessageAsyncShutdownPolicy;
import io.github.coco.messaging.CocoMessageDeliveryMode;
import io.github.coco.messaging.CocoMessageEnvelope;
import io.github.coco.messaging.CocoMessageFailurePolicy;
import io.github.coco.messaging.CocoMessageHandler;
import io.github.coco.messaging.CocoMessageNoSubscriberPolicy;
import io.github.coco.messaging.CocoMessageSubscription;
import io.github.coco.messaging.CocoMessageTransport;
import io.github.coco.messaging.CocoMessagingException;
import io.github.coco.messaging.CocoMessagingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认进程内 Coco 消息传输。
 * <p>
 * 每次投递都在 CopyOnWrite 快照上执行，因此订阅的并发增删不会导致当前消息漏投或重复投递。
 * </p>
 */
public final class LocalCocoMessageTransport implements CocoMessageTransport {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalCocoMessageTransport.class);

    private final ConcurrentMap<String, CopyOnWriteArrayList<CocoMessageHandler>> handlers = new ConcurrentHashMap<>();

    private final CocoMessageFailurePolicy failurePolicy;

    private final CocoMessageNoSubscriberPolicy noSubscriberPolicy;

    private final ThreadPoolExecutor executor;

    private final Duration shutdownAwait;

    private final CocoMessageAsyncShutdownPolicy shutdownPolicy;

    private final AtomicBoolean accepting = new AtomicBoolean(true);

    /**
     * 创建本地传输。
     * @param properties 消息配置
     */
    public LocalCocoMessageTransport(CocoMessagingProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        this.failurePolicy = properties.getFailurePolicy();
        this.noSubscriberPolicy = properties.getNoSubscriberPolicy();
        CocoMessagingProperties.AsyncProperties async = properties.getAsync();
        this.shutdownAwait = requirePositive(async.getShutdownAwait());
        this.shutdownPolicy = async.getShutdownPolicy();
        this.executor = properties.getDeliveryMode() == CocoMessageDeliveryMode.ASYNC ? createExecutor(async) : null;
    }

    @Override
    public void publish(CocoMessageEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        if (!this.accepting.get()) {
            throw new CocoMessagingException("coco.messaging.error.transport-closed");
        }
        List<CocoMessageHandler> subscribers = List.copyOf(this.handlers.getOrDefault(envelope.topic(),
                new CopyOnWriteArrayList<>()));
        if (subscribers.isEmpty()) {
            if (this.noSubscriberPolicy == CocoMessageNoSubscriberPolicy.FAIL) {
                throw new CocoMessagingException("coco.messaging.error.no-subscriber", envelope.topic());
            }
            return;
        }
        Runnable delivery = () -> deliver(envelope, subscribers);
        if (this.executor == null) {
            delivery.run();
            return;
        }
        try {
            this.executor.execute(delivery);
        }
        catch (RejectedExecutionException exception) {
            throw new CocoMessagingException("coco.messaging.error.async-rejected", exception, envelope.topic());
        }
    }

    @Override
    public CocoMessageSubscription subscribe(String topic, CocoMessageHandler handler) {
        String checkedTopic = CocoMessageEnvelope.create(topic, null).topic();
        CocoMessageHandler checkedHandler = Objects.requireNonNull(handler, "handler must not be null");
        if (!this.accepting.get()) {
            throw new CocoMessagingException("coco.messaging.error.transport-closed");
        }
        CopyOnWriteArrayList<CocoMessageHandler> topicHandlers = this.handlers.computeIfAbsent(checkedTopic,
                ignored -> new CopyOnWriteArrayList<>());
        topicHandlers.add(checkedHandler);
        AtomicBoolean subscribed = new AtomicBoolean(true);
        return () -> {
            if (subscribed.compareAndSet(true, false)) {
                topicHandlers.remove(checkedHandler);
                this.handlers.remove(checkedTopic, topicHandlers);
            }
        };
    }

    @Override
    public void close() {
        if (!this.accepting.compareAndSet(true, false) || this.executor == null) {
            return;
        }
        if (this.shutdownPolicy == CocoMessageAsyncShutdownPolicy.CANCEL) {
            this.executor.shutdownNow();
            return;
        }
        this.executor.shutdown();
        try {
            if (!this.executor.awaitTermination(this.shutdownAwait.toMillis(), TimeUnit.MILLISECONDS)) {
                this.executor.shutdownNow();
            }
        }
        catch (InterruptedException exception) {
            this.executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void deliver(CocoMessageEnvelope envelope, List<CocoMessageHandler> subscribers) {
        Optional<String> previousTraceId = CocoTraceContext.currentTraceId();
        restoreTraceId(envelope.traceId());
        try {
            for (CocoMessageHandler handler : subscribers) {
                try {
                    handler.handle(envelope);
                }
                catch (RuntimeException exception) {
                    if (this.failurePolicy == CocoMessageFailurePolicy.FAIL_FAST) {
                        throw exception;
                    }
                    LOGGER.warn("Coco message handler failed: topic={}, messageId={}, handler={}", envelope.topic(),
                            envelope.messageId(), handler.getClass().getName(), exception);
                }
            }
        }
        finally {
            restoreTraceId(previousTraceId.orElse(null));
        }
    }

    private static ThreadPoolExecutor createExecutor(CocoMessagingProperties.AsyncProperties properties) {
        if (properties.getQueueCapacity() <= 0) {
            throw CocoMessagingException.invalidArgument("coco.messaging.error.async-capacity-invalid");
        }
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.getQueueCapacity()), new CocoMessagingThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
        executor.prestartAllCoreThreads();
        return executor;
    }

    private static Duration requirePositive(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw CocoMessagingException.invalidArgument("coco.messaging.error.shutdown-await-invalid");
        }
        return duration;
    }

    private static void restoreTraceId(String traceId) {
        if (traceId == null) {
            CocoTraceContext.clear();
        }
        else {
            CocoTraceContext.setTraceId(traceId);
        }
    }

    private static final class CocoMessagingThreadFactory implements ThreadFactory {

        private final AtomicLong sequence = new AtomicLong();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "coco-messaging-" + this.sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
