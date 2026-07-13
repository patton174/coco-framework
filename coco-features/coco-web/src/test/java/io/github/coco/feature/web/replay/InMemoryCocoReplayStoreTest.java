package io.github.coco.feature.web.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

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

    private static InMemoryCocoReplayStore newStore(Clock clock) {
        CocoReplayProperties properties = new CocoReplayProperties();
        properties.setCleanupIntervalSeconds(1);
        return new InMemoryCocoReplayStore(properties, clock, false);
    }

    private static CocoReplayKey key(String nonce) {
        return new CocoReplayKey("app-1", "key-1", BASE_TIME.toString(), nonce, "POST", "/api/orders");
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
