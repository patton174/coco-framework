package io.github.coco.feature.web.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;

class InMemoryCocoReplayStoreTest {

    private static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void reserveDoesNotRunExpiredKeyCleanupOnWritePath() {
        MutableClock clock = new MutableClock(BASE_TIME);
        InMemoryCocoReplayStore store = newStore(clock);
        CocoReplayKey expiredKey = key("expired");
        CocoReplayKey activeKey = key("active");

        assertTrue(store.reserve(expiredKey, BASE_TIME.plusSeconds(1)));
        clock.set(BASE_TIME.plusSeconds(2));
        assertTrue(store.reserve(activeKey, BASE_TIME.plusSeconds(60)));

        assertEquals(2, store.reservedKeyCount());
        assertEquals(1, store.cleanupExpiredKeys());
        assertEquals(1, store.reservedKeyCount());
    }

    @Test
    void expiredSameKeyCanBeReservedAgainWithoutCleanup() {
        MutableClock clock = new MutableClock(BASE_TIME);
        InMemoryCocoReplayStore store = newStore(clock);
        CocoReplayKey replayKey = key("same");

        assertTrue(store.reserve(replayKey, BASE_TIME.plusSeconds(1)));
        clock.set(BASE_TIME.plusSeconds(2));

        assertTrue(store.reserve(replayKey, BASE_TIME.plusSeconds(60)));
        assertEquals(1, store.reservedKeyCount());
    }

    @Test
    void activeSameKeyCannotBeReservedTwice() {
        MutableClock clock = new MutableClock(BASE_TIME);
        InMemoryCocoReplayStore store = newStore(clock);
        CocoReplayKey replayKey = key("same");

        assertTrue(store.reserve(replayKey, BASE_TIME.plusSeconds(60)));

        assertFalse(store.reserve(replayKey, BASE_TIME.plusSeconds(120)));
        assertEquals(1, store.reservedKeyCount());
    }

    @Test
    void rejectsNewKeysAtGlobalCapacityWithDistinctFailure() {
        MutableClock clock = new MutableClock(BASE_TIME);
        InMemoryCocoReplayStore store = newStore(clock, 2, 2);

        assertTrue(store.reserve(key("app-1", "nonce-1"), BASE_TIME.plusSeconds(60)));
        assertTrue(store.reserve(key("app-2", "nonce-2"), BASE_TIME.plusSeconds(60)));

        CocoReplayCapacityExceededException exception = assertThrows(CocoReplayCapacityExceededException.class,
                () -> store.reserve(key("app-3", "nonce-3"), BASE_TIME.plusSeconds(60)));
        assertEquals(CocoReplayCapacityExceededException.Scope.GLOBAL, exception.scope());
        assertEquals(2, exception.capacity());
        assertEquals("coco.web.replay.capacity-exhausted", exception.messageCode());
        assertEquals(2, store.reservedKeyCount());
    }

    @Test
    void isolatesCapacityByAppIdWithoutBlockingOtherApps() {
        MutableClock clock = new MutableClock(BASE_TIME);
        InMemoryCocoReplayStore store = newStore(clock, 3, 1);

        assertTrue(store.reserve(key("app-1", "nonce-1"), BASE_TIME.plusSeconds(60)));

        CocoReplayCapacityExceededException exception = assertThrows(CocoReplayCapacityExceededException.class,
                () -> store.reserve(key("app-1", "nonce-2"), BASE_TIME.plusSeconds(60)));
        assertEquals(CocoReplayCapacityExceededException.Scope.APP_ID, exception.scope());
        assertEquals(1, exception.capacity());
        assertEquals(1, store.reservedKeyCountForAppId("app-1"));
        assertTrue(store.reserve(key("app-2", "nonce-3"), BASE_TIME.plusSeconds(60)));
        assertEquals(2, store.reservedKeyCount());
    }

    @Test
    void reclaimsExpiredKeysBeforeReportingCapacityExhaustion() {
        MutableClock clock = new MutableClock(BASE_TIME);
        InMemoryCocoReplayStore store = newStore(clock, 1, 1);
        assertTrue(store.reserve(key("app-1", "expired"), BASE_TIME.plusSeconds(1)));
        clock.set(BASE_TIME.plusSeconds(2));

        assertTrue(store.reserve(key("app-2", "active"), BASE_TIME.plusSeconds(60)));

        assertEquals(1, store.reservedKeyCount());
        assertEquals(0, store.reservedKeyCountForAppId("app-1"));
        assertEquals(1, store.reservedKeyCountForAppId("app-2"));
    }

    @Test
    void enforcesGlobalCapacityUnderConcurrentReservations() throws Exception {
        MutableClock clock = new MutableClock(BASE_TIME);
        InMemoryCocoReplayStore store = newStore(clock, 16, 16);

        int reserved = reserveConcurrently(store, 64,
                index -> key("app-" + index, "nonce-" + index),
                CocoReplayCapacityExceededException.Scope.GLOBAL);

        assertEquals(16, reserved);
        assertEquals(16, store.reservedKeyCount());
    }

    @Test
    void enforcesPerAppCapacityUnderConcurrentReservations() throws Exception {
        MutableClock clock = new MutableClock(BASE_TIME);
        InMemoryCocoReplayStore store = newStore(clock, 64, 8);

        int reserved = reserveConcurrently(store, 64,
                index -> key("app-1", "nonce-" + index),
                CocoReplayCapacityExceededException.Scope.APP_ID);

        assertEquals(8, reserved);
        assertEquals(8, store.reservedKeyCount());
        assertEquals(8, store.reservedKeyCountForAppId("app-1"));
    }

    @Test
    void reservesSameKeyExactlyOnceUnderConcurrency() throws Exception {
        MutableClock clock = new MutableClock(BASE_TIME);
        InMemoryCocoReplayStore store = newStore(clock, 1, 1);

        int reserved = reserveConcurrently(store, 32, index -> key("same"), null);

        assertEquals(1, reserved);
        assertEquals(1, store.reservedKeyCount());
    }

    @Test
    void cleanupAndExpiredSameKeyReservationRemainAtomic() throws Exception {
        MutableClock clock = new MutableClock(BASE_TIME);
        InMemoryCocoReplayStore store = newStore(clock, 1, 1);
        CocoReplayKey replayKey = key("same");
        assertTrue(store.reserve(replayKey, BASE_TIME.plusSeconds(1)));
        clock.set(BASE_TIME.plusSeconds(2));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> cleanup = executor.submit(() -> {
                start.await();
                return store.cleanupExpiredKeys();
            });
            Future<Boolean> reservation = executor.submit(() -> {
                start.await();
                return store.reserve(replayKey, BASE_TIME.plusSeconds(60));
            });
            start.countDown();

            assertTrue(reservation.get(10, TimeUnit.SECONDS));
            assertTrue(cleanup.get(10, TimeUnit.SECONDS) <= 1);
            assertEquals(1, store.reservedKeyCount());
            assertEquals(1, store.reservedKeyCountForAppId("app-1"));
        }
        finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static InMemoryCocoReplayStore newStore(Clock clock) {
        return newStore(clock, 100, 100);
    }

    private static InMemoryCocoReplayStore newStore(Clock clock, int maxEntries, int maxEntriesPerAppId) {
        CocoReplayProperties properties = new CocoReplayProperties();
        properties.setCleanupIntervalSeconds(1);
        properties.getInMemory().setMaxEntries(maxEntries);
        properties.getInMemory().setMaxEntriesPerAppId(maxEntriesPerAppId);
        return new InMemoryCocoReplayStore(properties, clock, false);
    }

    private static CocoReplayKey key(String nonce) {
        return key("app-1", nonce);
    }

    private static CocoReplayKey key(String appId, String nonce) {
        return new CocoReplayKey(appId, "key-1", BASE_TIME.toString(), nonce, "POST", "/api/orders");
    }

    private static int reserveConcurrently(InMemoryCocoReplayStore store, int attempts,
            IntFunction<CocoReplayKey> keyFactory, CocoReplayCapacityExceededException.Scope expectedScope)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(attempts, 16));
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>(attempts);
        try {
            for (int index = 0; index < attempts; index++) {
                CocoReplayKey replayKey = keyFactory.apply(index);
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        return store.reserve(replayKey, BASE_TIME.plusSeconds(60));
                    }
                    catch (CocoReplayCapacityExceededException exception) {
                        if (expectedScope == null) {
                            throw exception;
                        }
                        assertEquals(expectedScope, exception.scope());
                        return false;
                    }
                }));
            }
            start.countDown();
            int reserved = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(10, TimeUnit.SECONDS)) {
                    reserved++;
                }
            }
            return reserved;
        }
        finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return this.instant;
        }
    }
}
