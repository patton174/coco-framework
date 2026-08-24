package io.github.coco.scheduling;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

final class ManualTaskScheduler implements TaskScheduler {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
    private final List<Entry> entries = new ArrayList<>();

    @Override
    public Clock getClock() {
        return this.clock;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
        return add(task, "cron", trigger, null, null);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
        return add(task, "once", null, startTime, null);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
        return add(task, "fixed-rate", null, startTime, period);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
        return add(task, "fixed-rate", null, null, period);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
        return add(task, "fixed-delay", null, startTime, delay);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
        return add(task, "fixed-delay", null, null, delay);
    }

    List<Entry> entries() {
        return List.copyOf(this.entries);
    }

    Entry latest() {
        return this.entries.get(this.entries.size() - 1);
    }

    void advance(Duration duration) {
        this.clock.advance(duration);
    }

    private ScheduledFuture<?> add(Runnable task, String type, Trigger trigger, Instant startTime, Duration interval) {
        Entry entry = new Entry(task, type, trigger, startTime, interval);
        this.entries.add(entry);
        return entry.future;
    }

    record Entry(Runnable task, String type, Trigger trigger, Instant startTime, Duration interval, Future future) {

        Entry(Runnable task, String type, Trigger trigger, Instant startTime, Duration interval) {
            this(task, type, trigger, startTime, interval, new Future());
        }

        void run() {
            this.task.run();
        }
    }

    static final class Future implements ScheduledFuture<Object> {

        private final CountDownLatch cancelledLatch = new CountDownLatch(1);
        private volatile boolean cancelled;
        private volatile boolean interrupt;

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            this.cancelled = true;
            this.interrupt = mayInterruptIfRunning;
            this.cancelledLatch.countDown();
            return true;
        }

        boolean cancelled() {
            return this.cancelled;
        }

        boolean interrupt() {
            return this.interrupt;
        }

        boolean awaitCancellation(long timeout, TimeUnit unit) throws InterruptedException {
            return this.cancelledLatch.await(timeout, unit);
        }

        @Override
        public boolean isCancelled() {
            return this.cancelled;
        }

        @Override
        public boolean isDone() {
            return this.cancelled;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return this.instant;
        }

        private void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }
    }
}
