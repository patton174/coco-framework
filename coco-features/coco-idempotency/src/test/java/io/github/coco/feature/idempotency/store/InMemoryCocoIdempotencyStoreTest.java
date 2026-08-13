package io.github.coco.feature.idempotency.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class InMemoryCocoIdempotencyStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-08T08:00:00Z");

    @Test
    void acquireCompleteReplayMismatchAndFailUseDeterministicStates() {
        try (InMemoryCocoIdempotencyStore store = store(10)) {
            CocoIdempotencyRequest request = request("key-a", "payload-a");

            CocoIdempotencyAcquireResult acquired = store.acquire(request, NOW, NOW.plusSeconds(60));
            CocoIdempotencyLease lease = acquired.lease().orElseThrow();

            assertThat(acquired.status()).isEqualTo(CocoIdempotencyAcquireStatus.ACQUIRED);
            assertThat(store.acquire(request, NOW, NOW.plusSeconds(60)).status())
                    .isEqualTo(CocoIdempotencyAcquireStatus.IN_PROGRESS);
            assertThat(store.acquire(request("key-a", "payload-b"), NOW, NOW.plusSeconds(60)).status())
                    .isEqualTo(CocoIdempotencyAcquireStatus.PAYLOAD_MISMATCH);

            CocoIdempotencyStoredResponse response = new CocoIdempotencyStoredResponse(
                    201, Map.of("X-Result", List.of("created")), "done".getBytes(StandardCharsets.UTF_8));
            assertThat(store.complete(lease, response, NOW.plusSeconds(1))).isTrue();

            CocoIdempotencyAcquireResult replay = store.acquire(request, NOW.plusSeconds(2), NOW.plusSeconds(60));
            assertThat(replay.status()).isEqualTo(CocoIdempotencyAcquireStatus.REPLAY);
            assertThat(replay.response().orElseThrow().body()).isEqualTo("done".getBytes(StandardCharsets.UTF_8));
            assertThat(store.fail(lease, NOW.plusSeconds(2))).isFalse();
        }
    }

    @Test
    void staleOwnerCannotCompleteOrReleaseCurrentLease() {
        try (InMemoryCocoIdempotencyStore store = store(10)) {
            CocoIdempotencyRequest request = request("key-owner", "payload");
            CocoIdempotencyLease current = store.acquire(request, NOW, NOW.plusSeconds(60))
                    .lease().orElseThrow();
            CocoIdempotencyLease stale = new CocoIdempotencyLease(
                    current.request(), "stale-owner", current.expiresAt());
            CocoIdempotencyStoredResponse response = new CocoIdempotencyStoredResponse(200, Map.of(), new byte[0]);

            assertThat(store.complete(stale, response, NOW.plusSeconds(1))).isFalse();
            assertThat(store.fail(stale, NOW.plusSeconds(1))).isFalse();
            assertThat(store.acquire(request, NOW.plusSeconds(1), NOW.plusSeconds(60)).status())
                    .isEqualTo(CocoIdempotencyAcquireStatus.IN_PROGRESS);
        }
    }

    @Test
    void capacityIsBoundedAndExpiredEntriesAreReclaimedAtomically() {
        try (InMemoryCocoIdempotencyStore store = store(1)) {
            assertThat(store.acquire(request("key-a", "a"), NOW, NOW.plusSeconds(10)).status())
                    .isEqualTo(CocoIdempotencyAcquireStatus.ACQUIRED);
            assertThat(store.acquire(request("key-b", "b"), NOW, NOW.plusSeconds(10)).status())
                    .isEqualTo(CocoIdempotencyAcquireStatus.CAPACITY_EXCEEDED);

            assertThat(store.acquire(request("key-b", "b"), NOW.plusSeconds(11), NOW.plusSeconds(30)).status())
                    .isEqualTo(CocoIdempotencyAcquireStatus.ACQUIRED);
            assertThat(store.activeEntries()).isEqualTo(1);
        }
    }

    @Test
    void concurrentAcquireHasExactlyOneWinner() throws Exception {
        try (InMemoryCocoIdempotencyStore store = store(10)) {
            CocoIdempotencyRequest request = request("key-concurrent", "same-payload");
            CountDownLatch ready = new CountDownLatch(16);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(16);
            try {
                List<Future<CocoIdempotencyAcquireStatus>> futures = IntStream.range(0, 16)
                        .mapToObj(index -> executor.submit(() -> {
                            ready.countDown();
                            start.await();
                            return store.acquire(request, NOW, NOW.plusSeconds(60)).status();
                        }))
                        .toList();
                ready.await();
                start.countDown();
                List<CocoIdempotencyAcquireStatus> statuses = futures.stream().map(future -> {
                    try {
                        return future.get();
                    }
                    catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                }).toList();

                assertThat(statuses).filteredOn(CocoIdempotencyAcquireStatus.ACQUIRED::equals).hasSize(1);
                assertThat(statuses).filteredOn(CocoIdempotencyAcquireStatus.IN_PROGRESS::equals).hasSize(15);
            }
            finally {
                executor.shutdownNow();
            }
        }
    }

    private static InMemoryCocoIdempotencyStore store(int maxEntries) {
        return new InMemoryCocoIdempotencyStore(maxEntries, Duration.ofHours(1),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CocoIdempotencyRequest request(String key, String payload) {
        return new CocoIdempotencyRequest(sha256(key), sha256(payload));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
