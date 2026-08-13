package io.github.coco.feature.concurrencylimit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCocoConcurrencyLimitStoreTest {

    private InMemoryCocoConcurrencyLimitStore store;

    @AfterEach
    void closeStore() {
        if (this.store != null) {
            this.store.close();
        }
    }

    @Test
    void acquiresAllDimensionsAtomicallyAndReleasesExactlyOnce() {
        this.store = store(10);
        CocoConcurrencyLimitRequest request = request(2, 1, 1, "client-a");

        CocoConcurrencyLimitAcquisition first = this.store.acquire(request);
        CocoConcurrencyLimitAcquisition rejected = this.store.acquire(request);

        assertThat(first.acquired()).isTrue();
        assertThat(rejected.acquired()).isFalse();
        assertThat(rejected.rejectedDimension()).isEqualTo(CocoConcurrencyLimitDimension.ROUTE);
        assertThat(rejected.rejectionReason()).isEqualTo(CocoConcurrencyLimitRejectionReason.LIMIT_REACHED);
        assertThat(this.store.currentCount(global(2))).isOne();
        assertThat(this.store.currentCount(route(1))).isOne();
        assertThat(this.store.currentCount(key(1, "client-a"))).isOne();

        this.store.release(first.permit());
        this.store.release(first.permit());

        assertThat(this.store.activeEntries()).isZero();
    }

    @Test
    void rejectsCapacityWithoutPartiallyCreatingAnyDimension() {
        this.store = store(2);

        CocoConcurrencyLimitAcquisition acquisition = this.store.acquire(request(3, 3, 3, "client-a"));

        assertThat(acquisition.acquired()).isFalse();
        assertThat(acquisition.rejectionReason())
                .isEqualTo(CocoConcurrencyLimitRejectionReason.CAPACITY_EXHAUSTED);
        assertThat(this.store.activeEntries()).isZero();
    }

    @Test
    void neverExceedsLimitUnderConcurrentAcquire() throws Exception {
        this.store = store(10);
        CocoConcurrencyLimitRequest request = new CocoConcurrencyLimitRequest(List.of(route(3)));
        int workers = 16;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch attempted = new CountDownLatch(workers);
        CountDownLatch release = new CountDownLatch(1);
        List<CocoConcurrencyLimitAcquisition> acquisitions = new CopyOnWriteArrayList<>();
        try {
            for (int index = 0; index < workers; index++) {
                executor.submit(() -> {
                    start.await();
                    CocoConcurrencyLimitAcquisition acquisition = this.store.acquire(request);
                    acquisitions.add(acquisition);
                    attempted.countDown();
                    release.await();
                    if (acquisition.acquired()) {
                        this.store.release(acquisition.permit());
                    }
                    return null;
                });
            }

            start.countDown();
            assertThat(attempted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(acquisitions).filteredOn(CocoConcurrencyLimitAcquisition::acquired).hasSize(3);
            assertThat(this.store.currentCount(route(3))).isEqualTo(3);
        }
        finally {
            release.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(this.store.activeEntries()).isZero();
    }

    @Test
    void rejectsAfterCloseWithoutCreatingEntries() {
        this.store = store(10);
        this.store.close();

        CocoConcurrencyLimitAcquisition acquisition = this.store.acquire(
                new CocoConcurrencyLimitRequest(List.of(route(1))));

        assertThat(acquisition.acquired()).isFalse();
        assertThat(acquisition.rejectionReason()).isEqualTo(CocoConcurrencyLimitRejectionReason.UNAVAILABLE);
        assertThat(this.store.activeEntries()).isZero();
    }

    private static InMemoryCocoConcurrencyLimitStore store(int maxEntries) {
        CocoConcurrencyLimitProperties properties = new CocoConcurrencyLimitProperties();
        properties.getInMemory().setMaxEntries(maxEntries);
        return new InMemoryCocoConcurrencyLimitStore(properties);
    }

    private static CocoConcurrencyLimitRequest request(int globalLimit, int routeLimit, int keyLimit, String key) {
        return new CocoConcurrencyLimitRequest(List.of(global(globalLimit), route(routeLimit), key(keyLimit, key)));
    }

    private static CocoConcurrencyLimitConstraint global(int limit) {
        return new CocoConcurrencyLimitConstraint(CocoConcurrencyLimitDimension.GLOBAL, "global", limit);
    }

    private static CocoConcurrencyLimitConstraint route(int limit) {
        return new CocoConcurrencyLimitConstraint(CocoConcurrencyLimitDimension.ROUTE, "orders", limit);
    }

    private static CocoConcurrencyLimitConstraint key(int limit, String key) {
        return new CocoConcurrencyLimitConstraint(CocoConcurrencyLimitDimension.KEY, "orders\0" + key, limit);
    }
}
